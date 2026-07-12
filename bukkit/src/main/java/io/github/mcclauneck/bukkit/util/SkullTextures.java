package io.github.mcclauneck.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds custom player heads from base64 texture values using the
 * platform-agnostic {@code org.bukkit.profile} API (available in spigot-api).
 * <p>
 * Textures are supplied as the base64-encoded profile-property value that wraps
 * a JSON document of the form {@code {"textures":{"SKIN":{"url":"..."}}}}; the
 * skin URL is extracted and applied through the profile's textures.
 * </p>
 */
public final class SkullTextures {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private SkullTextures() {
    }

    /**
     * Creates a player head with the given base64 texture. When the texture is
     * missing or unparsable the head is returned untextured.
     *
     * @param base64Texture the base64 profile-property texture value, or {@code null}
     * @return a {@link Material#PLAYER_HEAD} item
     */
    public static ItemStack createHead(String base64Texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            apply(meta, base64Texture);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Applies a base64-encoded skin texture to the given skull meta. Does
     * nothing when the texture is missing or unparsable.
     *
     * @param meta          the skull meta to modify
     * @param base64Texture the base64 profile-property texture value
     */
    public static void apply(SkullMeta meta, String base64Texture) {
        URL skinUrl = toSkinUrl(base64Texture);
        if (skinUrl == null) {
            return;
        }
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        profile.getTextures().setSkin(skinUrl);
        meta.setOwnerProfile(profile);
    }

    /**
     * Decodes a base64 profile-property texture value into its skin URL.
     *
     * @param base64Texture the base64 texture value
     * @return the skin URL, or {@code null} if it could not be parsed
     */
    private static URL toSkinUrl(String base64Texture) {
        if (base64Texture == null || base64Texture.isBlank()) {
            return null;
        }
        try {
            String json = new String(Base64.getDecoder().decode(base64Texture), StandardCharsets.UTF_8);
            Matcher matcher = URL_PATTERN.matcher(json);
            if (!matcher.find()) {
                return null;
            }
            return URI.create(matcher.group(1)).toURL();
        } catch (Exception e) {
            return null;
        }
    }
}
