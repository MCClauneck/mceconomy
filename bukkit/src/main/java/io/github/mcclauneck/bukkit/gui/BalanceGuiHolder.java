package io.github.mcclauneck.bukkit.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * {@link InventoryHolder} marking an inventory as a balance GUI page and
 * carrying the state the click listener needs to paginate.
 */
public final class BalanceGuiHolder implements InventoryHolder {

    private final List<CurrencyBalanceEntry> entries;
    private final int page;
    private Inventory inventory;

    /**
     * @param entries every currency row shown by the GUI, across all pages
     * @param page    the zero-based page this inventory displays
     */
    public BalanceGuiHolder(List<CurrencyBalanceEntry> entries, int page) {
        this.entries = entries;
        this.page = page;
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
