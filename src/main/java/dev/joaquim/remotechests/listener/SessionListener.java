package dev.joaquim.remotechests.listener;

import dev.joaquim.remotechests.service.ChestAccessService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Libera o chunk ticket e fecha a tampa quando o jogador sai do inventario. */
public final class SessionListener implements Listener {

    private final ChestAccessService access;

    public SessionListener(ChestAccessService access) {
        this.access = access;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        access.endSession(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        access.endSession(event.getPlayer().getUniqueId());
        access.clearCooldown(event.getPlayer().getUniqueId());
    }
}
