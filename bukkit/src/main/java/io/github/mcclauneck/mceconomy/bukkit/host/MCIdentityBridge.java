package io.github.mcclauneck.mceconomy.bukkit.host;

import io.github.mcclauneck.mceconomy.api.host.IdentitySource;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds the pure {@link IdentitySource} contract to the live MCIdentity plugin.
 *
 * <p>Everything is resolved reflectively through MCIdentity's own classloader.
 * Bukkit's {@code depend} declaration guarantees it loads first and makes its
 * classes reachable, and resolving at runtime keeps this plugin — and the
 * libraries it publishes — from having to be compiled against every MCIdentity
 * release. No {@code io.github.mcoriax} artifact appears in this build.</p>
 *
 * <p>Two things are read: which identity a player has equipped, and the equip
 * event itself, subscribed through {@link #registerEquipListener} because the
 * event class cannot be named in an {@code @EventHandler} signature. Nothing
 * touches MCIdentity's database, config, or item formats.</p>
 *
 * <p>Every failure path returns empty, which callers treat as "no active
 * identity" — a broken integration refuses to move money rather than crediting
 * the wrong wallet.</p>
 */
public final class MCIdentityBridge implements IdentitySource {

    /**
     * The host plugin name, as declared in its {@code plugin.yml}.
     */
    public static final String PLUGIN_NAME = "MCIdentity";

    /**
     * The host class holding the provider singleton.
     */
    private static final String PROVIDER_CLASS = "io.github.mcoriax.mcidentity.common.MCIdentityProvider";

    /**
     * The host event fired when a player equips an identity.
     */
    private static final String EQUIP_EVENT_CLASS = "io.github.mcoriax.mcidentity.bukkit.event.MCIdentityEquipEvent";

    private final Logger logger;

    /**
     * The loaded MCIdentity plugin, kept for its classloader.
     */
    private final ClassLoader hostLoader;

    /**
     * The {@code MCIdentityProvider.instance} singleton field.
     */
    private final Field instanceField;

    /**
     * The static {@code hasDatabase()} readiness probe.
     */
    private final Method hasDatabase;

    /**
     * The instance method resolving a player's equipped identity.
     */
    private final Method getActiveIdentity;

    /**
     * Whether a lookup failure has already been logged, so a persistent problem
     * does not flood the console on every interaction.
     */
    private volatile boolean lookupFailureLogged;

    private MCIdentityBridge(Logger logger, ClassLoader hostLoader, Field instanceField,
                             Method hasDatabase, Method getActiveIdentity) {
        this.logger = logger;
        this.hostLoader = hostLoader;
        this.instanceField = instanceField;
        this.hasDatabase = hasDatabase;
        this.getActiveIdentity = getActiveIdentity;
    }

    /**
     * Resolves the binding against the loaded MCIdentity plugin.
     *
     * <p>Called once at enable time; the resolved handles are cached for the
     * lifetime of the plugin.</p>
     *
     * @param plugin This plugin, used for its logger.
     * @return An Optional containing the bound source, or empty when MCIdentity
     *         is absent, disabled, or does not expose the expected provider.
     */
    public static Optional<MCIdentityBridge> resolve(Plugin plugin) {
        Logger logger = plugin.getLogger();

        Plugin host = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (host == null || !host.isEnabled()) {
            logger.severe(PLUGIN_NAME + " is not enabled; identity wallets cannot be resolved.");
            return Optional.empty();
        }

        try {
            ClassLoader loader = host.getClass().getClassLoader();
            Class<?> providerClass = Class.forName(PROVIDER_CLASS, false, loader);

            Field instanceField = providerClass.getField("instance");
            Method hasDatabase = providerClass.getMethod("hasDatabase");
            Method getActiveIdentity = providerClass.getMethod("getActiveIdentity", UUID.class);

            return Optional.of(new MCIdentityBridge(logger, loader, instanceField, hasDatabase, getActiveIdentity));
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.severe("Could not bind to MCIdentity (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). The installed MCIdentity version may be incompatible.");
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return Boolean.TRUE.equals(hasDatabase.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            logLookupFailureOnce(e);
            return false;
        }
    }

    @Override
    public CompletableFuture<Optional<UUID>> activeIdentity(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            Object provider = instanceField.get(null);
            if (provider == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Object result = getActiveIdentity.invoke(provider, playerUuid);
            if (!(result instanceof CompletableFuture<?> future)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            return future
                    .thenApply(MCIdentityBridge::asIdentityUuid)
                    .exceptionally(ex -> {
                        logLookupFailureOnce(ex);
                        return Optional.empty();
                    });
        } catch (ReflectiveOperationException | RuntimeException e) {
            logLookupFailureOnce(e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * Subscribes to MCIdentity's equip event.
     *
     * <p>The event class is not on this plugin's compile classpath, so it cannot
     * appear in an {@code @EventHandler} method signature. Bukkit's lower-level
     * {@code registerEvent} takes the event type as a {@code Class} instead,
     * which is exactly what reflection can supply.</p>
     *
     * @param plugin   This plugin, registered as the owner of the handler.
     * @param onEquip  Called with the equipped identity UUID, on the event thread.
     * @return {@code true} when the subscription was installed.
     */
    public boolean registerEquipListener(Plugin plugin, Consumer<UUID> onEquip) {
        try {
            Class<? extends Event> eventClass = Class.forName(EQUIP_EVENT_CLASS, false, hostLoader)
                    .asSubclass(Event.class);
            Method getIdentityUuid = eventClass.getMethod("getIdentityUuid");

            // An empty marker listener: all the behaviour lives in the executor.
            Listener listener = new Listener() {
            };

            Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR,
                    (ignored, event) -> {
                        if (!eventClass.isInstance(event)) {
                            return;
                        }
                        try {
                            if (getIdentityUuid.invoke(event) instanceof UUID identityUuid) {
                                onEquip.accept(identityUuid);
                            }
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            logLookupFailureOnce(e);
                        }
                    }, plugin, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.severe("Could not subscribe to the MCIdentity equip event ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). Identity accounts will not be created automatically.");
            return false;
        }
    }

    /**
     * Narrows the host's {@code Optional<UUID>} result without trusting its shape.
     *
     * @param result The value the host future completed with.
     * @return The identity UUID, or empty when the host returned anything else.
     */
    private static Optional<UUID> asIdentityUuid(Object result) {
        if (result instanceof Optional<?> optional && optional.orElse(null) instanceof UUID identityUuid) {
            return Optional.of(identityUuid);
        }
        return Optional.empty();
    }

    /**
     * Logs the first lookup failure at warning level and stays quiet afterwards.
     *
     * @param error The failure to report.
     */
    private void logLookupFailureOnce(Throwable error) {
        if (lookupFailureLogged) {
            return;
        }
        lookupFailureLogged = true;
        logger.log(Level.WARNING, "Failed to read the active identity from MCIdentity. "
                + "Identity wallets stay unavailable until this is resolved. "
                + "This message is logged once.", error);
    }
}
