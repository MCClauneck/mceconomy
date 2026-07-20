package io.github.mcclauneck.mceconomy.bukkit.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * {@link InventoryHolder} marking an inventory as a balance GUI page and
 * carrying the state the click listener needs to paginate. The navigation slots
 * are stored per page because the inventory height (and therefore the row that
 * holds the arrows) depends on how many currencies the page shows.
 */
public final class BalanceGuiHolder implements InventoryHolder {

    private final List<CurrencyBalanceEntry> entries;
    private final int page;
    private final int previousSlot;
    private final int nextSlot;
    private Inventory inventory;

    /**
     * @param entries      every currency row shown by the GUI, across all pages
     * @param page         the zero-based page this inventory displays
     * @param previousSlot the slot of the previous-page arrow, or {@code -1} when the GUI is a single page
     * @param nextSlot     the slot of the next-page arrow, or {@code -1} when the GUI is a single page
     */
    public BalanceGuiHolder(List<CurrencyBalanceEntry> entries, int page, int previousSlot, int nextSlot) {
        this.entries = entries;
        this.page = page;
        this.previousSlot = previousSlot;
        this.nextSlot = nextSlot;
    }

    /**
     * @return every currency row shown by the GUI, across all pages
     */
    public List<CurrencyBalanceEntry> getEntries() {
        return entries;
    }

    /**
     * @return the zero-based page this inventory displays
     */
    public int getPage() {
        return page;
    }

    /**
     * @return the slot of the previous-page arrow, or {@code -1} when the GUI is a single page
     */
    public int getPreviousSlot() {
        return previousSlot;
    }

    /**
     * @return the slot of the next-page arrow, or {@code -1} when the GUI is a single page
     */
    public int getNextSlot() {
        return nextSlot;
    }

    /**
     * Binds the created inventory to this holder. Called once by the GUI builder.
     * @param inventory the inventory displaying this page
     */
    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
