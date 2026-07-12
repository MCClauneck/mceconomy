package io.github.mcclauneck.bukkit.gui;

import io.github.mcclauneck.bukkit.scheduler.Schedulers;
import io.github.mcclauneck.bukkit.util.SkullTextures;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The paged {@code /balance} GUI: one custom head per currency showing the
 * viewing identity's balance, with previous/next navigation when the
 * currencies do not fit a single page.
 */
public final class BalanceGui {

    /** Number of currency slots per page; the last row is reserved for navigation. */
    public static final int PAGE_SIZE = 45;

    /** Slot of the previous-page button inside the navigation row. */
    public static final int PREVIOUS_SLOT = 45;

    /** Slot of the next-page button inside the navigation row. */
    public static final int NEXT_SLOT = 53;

    private BalanceGui() {
    }

    /**
     * Opens one page of the balance GUI, hopping onto the thread that owns the
     * player before touching the inventory API (main thread on Spigot/Paper,
     * region thread on Folia). Safe to call from asynchronous callbacks.
     *
     * @param player  the player viewing the GUI
     * @param entries every currency row, across all pages
     * @param page    the zero-based page to show, clamped into range
     */
    public static void open(Player player, List<CurrencyBalanceEntry> entries, int page) {
        int pageCount = pageCount(entries.size());
        int clamped = Math.min(Math.max(page, 0), pageCount - 1);

        Schedulers.runForEntity(player, () -> {
            BalanceGuiHolder holder = new BalanceGuiHolder(entries, clamped);
            Inventory gui = Bukkit.createInventory(holder, 54,
                    "Balance (" + (clamped + 1) + "/" + pageCount + ")");
            holder.setInventory(gui);

            int start = clamped * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, entries.size());
            for (int i = start; i < end; i++) {
                gui.setItem(i - start, currencyItem(entries.get(i)));
            }

            ItemStack filler = namedItem(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
            for (int slot = PAGE_SIZE; slot < 54; slot++) {
                gui.setItem(slot, filler);
            }
            if (clamped > 0) {
                gui.setItem(PREVIOUS_SLOT, namedItem(new ItemStack(Material.ARROW),
                        ChatColor.YELLOW + "Previous Page", List.of()));
            }
            if (clamped < pageCount - 1) {
                gui.setItem(NEXT_SLOT, namedItem(new ItemStack(Material.ARROW),
                        ChatColor.YELLOW + "Next Page", List.of()));
            }

            player.openInventory(gui);
        });
    }

    /**
     * Computes how many pages are needed for the given number of currencies
     * (always at least one, so an empty economy still opens an empty GUI).
     *
     * @param entryCount the total number of currency rows
     * @return the number of pages
     */
    public static int pageCount(int entryCount) {
        return Math.max(1, (entryCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /**
     * Builds the head item for one currency row.
     */
    private static ItemStack currencyItem(CurrencyBalanceEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Balance: " + ChatColor.WHITE + String.format("%,d", entry.balance()));
        lore.add(entry.transferable()
                ? ChatColor.DARK_GRAY + "Transferable"
                : ChatColor.DARK_GRAY + "Not transferable");
        return namedItem(SkullTextures.createHead(entry.texture()),
                ChatColor.GOLD + entry.displayName(), lore);
    }

    /**
     * Applies a display name and lore to an item.
     */
    private static ItemStack namedItem(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
