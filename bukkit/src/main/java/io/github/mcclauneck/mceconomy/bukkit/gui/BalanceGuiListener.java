package io.github.mcclauneck.mceconomy.bukkit.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles clicks inside the balance GUI: every interaction is display-only, so
 * clicks are cancelled, and the navigation arrows re-open the adjacent page.
 */
public class BalanceGuiListener implements Listener {

    /**
     * Cancels interactions with the balance GUI and paginates on arrow clicks.
     * @param event the click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BalanceGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        if (event.getSlot() == holder.getPreviousSlot() && holder.getPage() > 0) {
            BalanceGui.open(player, holder.getEntries(), holder.getPage() - 1);
        } else if (event.getSlot() == holder.getNextSlot()
                && holder.getPage() < BalanceGui.pageCount(holder.getEntries().size()) - 1) {
            BalanceGui.open(player, holder.getEntries(), holder.getPage() + 1);
        }
    }

    /**
     * Cancels drags inside the balance GUI, which is display-only.
     * @param event the drag event
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BalanceGuiHolder) {
            event.setCancelled(true);
        }
    }
}
