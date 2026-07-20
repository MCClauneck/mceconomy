package io.github.mcclauneck.mceconomy.bukkit.event;

import io.github.mcclauneck.mceconomy.api.models.LogScope;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * A cancellable Bukkit custom event fired <strong>before</strong> a balance change is
 * applied to an account's wallet.
 * <p>
 * This is the veto point for economy operations: a listener may call
 * {@link #setCancelled(boolean) setCancelled(true)} to block the change entirely.
 * A typical use is refusing to <em>give money to a player</em> — cancel the event
 * when {@link #getScope()} is a credit such as {@link LogScope#RECEIVE}.
 * <p>
 * The {@link #getAmount() amount} is always the non-negative magnitude of the change;
 * the {@link #getScope() scope} tells you whether it is a credit or a debit. The
 * account is identified by an account type plus a UUID — for MCIdentity-bound money
 * this is the identity UUID (see {@code AccountTypes.IDENTITY}), never the base player
 * UUID.
 * <p>
 * The event marks itself asynchronous whenever it is fired off the primary thread, so
 * keep handlers thread-safe and hop back onto the appropriate scheduler before touching
 * thread-confined Bukkit API.
 */
public class EconomyTransactionEvent extends Event implements Cancellable {

    /**
     * The handlers for this event.
     */
    private static final HandlerList HANDLERS = new HandlerList();

    private final String accountType;
    private final UUID accountUuid;
    private final String currencySystemCode;
    private final long amount;
    private final LogScope scope;
    private boolean cancelled;

    /**
     * Constructs the transaction event.
     * @param accountType The type of account (e.g. {@code "identity"}).
     * @param accountUuid The UUID of the account (the identity UUID for identity money).
     * @param currencySystemCode The system code of the currency being changed.
     * @param amount The non-negative magnitude of the change.
     * @param scope The scope of the change (e.g. {@link LogScope#RECEIVE} for a credit).
     */
    public EconomyTransactionEvent(String accountType, UUID accountUuid, String currencySystemCode, long amount, LogScope scope) {
        super(!Bukkit.isPrimaryThread());
        this.accountType = accountType;
        this.accountUuid = accountUuid;
        this.currencySystemCode = currencySystemCode;
        this.amount = amount;
        this.scope = scope;
    }

    /**
     * Gets the account type.
     * @return The account type string.
     */
    public String getAccountType() {
        return accountType;
    }

    /**
     * Gets the account UUID.
     * @return The UUID of the account.
     */
    public UUID getAccountUuid() {
        return accountUuid;
    }

    /**
     * Gets the currency system code.
     * @return The currency system code string.
     */
    public String getCurrencySystemCode() {
        return currencySystemCode;
    }

    /**
     * Gets the non-negative magnitude of the balance change.
     * @return The amount.
     */
    public long getAmount() {
        return amount;
    }

    /**
     * Gets the scope of the change, which distinguishes a credit from a debit.
     * @return The transaction scope.
     */
    public LogScope getScope() {
        return scope;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Gets the handler list for this event.
     * @return The handler list.
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
