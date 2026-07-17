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
 *
 * <p>The inventory height grows with the currencies shown on the current page:
 * five currencies fill a single row, ten span two rows, and so on, with any
 * leftover slots (and the navigation row, when paging is needed) padded with a
 * tooltip-less black glass pane.
 */
public final class BalanceGui {

    /** Columns in one inventory row, fixed by the Bukkit chest layout. */
    private static final int COLUMNS = 9;

    /** Maximum rows a Bukkit chest inventory can display. */
    private static final int MAX_ROWS = 6;

    /**
     * Number of currency slots per page. Five rows of currencies, leaving the
     * sixth row for navigation when more than one page is required.
     */
    public static final int PAGE_SIZE = (MAX_ROWS - 1) * COLUMNS;

    private BalanceGui() {
    }

    /**
     * Opens one page of the balance GUI, hopping onto the thread that owns the
     * player before touching the inventory API (main thread on Spigot/Paper,
     * region thread on Folia). Safe to call from asynchronous callbacks.
     *
     * <p>The inventory is sized to the currencies on this page: one row per nine
     * currencies, plus a navigation row when the GUI spans several pages.
     *
     * @param player  the player viewing the GUI
     * @param entries every currency row, across all pages
     * @param page    the zero-based page to show, clamped into range
     */
    public static void open(Player player, List<CurrencyBalanceEntry> entries, int page) {
        int pageCount = pageCount(entries.size());
        int clamped = Math.min(Math.max(page, 0), pageCount - 1);
        boolean paginated = pageCount > 1;

        Schedulers.runForEntity(player, () -> {
            int start = clamped * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, entries.size());
            int shown = end - start;

            int contentRows = Math.max(1, (shown + COLUMNS - 1) / COLUMNS);
            int rows = contentRows + (paginated ? 1 : 0);
            int size = rows * COLUMNS;
            int contentSlots = contentRows * COLUMNS;

            int previousSlot = paginated ? contentSlots : -1;
            int nextSlot = paginated ? contentSlots + COLUMNS - 1 : -1;

            BalanceGuiHolder holder = new BalanceGuiHolder(entries, clamped, previousSlot, nextSlot);
            Inventory gui = Bukkit.createInventory(holder, size,
                    "Balance (" + (clamped + 1) + "/" + pageCount + ")");
            holder.setInventory(gui);

            for (int i = start; i < end; i++) {
                gui.setItem(i - start, currencyItem(entries.get(i)));
            }

            ItemStack filler = fillerItem();
            for (int slot = shown; slot < size; slot++) {
                gui.setItem(slot, filler);
            }
            if (paginated && clamped > 0) {
                gui.setItem(previousSlot, namedItem(new ItemStack(Material.ARROW),
                        ChatColor.YELLOW + "Previous Page", List.of()));
            }
            if (paginated && clamped < pageCount - 1) {
                gui.setItem(nextSlot, namedItem(new ItemStack(Material.ARROW),
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
     * Builds the black glass pane used to pad empty slots. The tooltip is
     * hidden so the pane shows no hover text at all.
     */
    private static ItemStack fillerItem() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            filler.setItemMeta(meta);
        }
        return filler;
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
