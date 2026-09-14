package dev.joaquim.remotechests.listener;

import dev.joaquim.remotechests.RemoteChestsPlugin;
import dev.joaquim.remotechests.model.RemoteChest;
import dev.joaquim.remotechests.service.ChestService;
import dev.joaquim.remotechests.util.Messages;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;
import java.util.Optional;

/**
 * Evita que um bau registrado desapareca sem o dono saber.
 * Se o proprio dono (ou um admin) quebrar, o registro e removido junto.
 */
public final class ChestProtectionListener implements Listener {

    private final RemoteChestsPlugin plugin;
    private final ChestService chests;
    private final Messages messages;

    public ChestProtectionListener(RemoteChestsPlugin plugin, ChestService chests, Messages messages) {
        this.plugin = plugin;
        this.chests = chests;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<RemoteChest> found = chests.findAtBlock(event.getBlock());
        if (found.isEmpty()) {
            return;
        }
        RemoteChest chest = found.get();
        Player breaker = event.getPlayer();
        boolean allowed = breaker.getUniqueId().equals(chest.ownerId())
                || breaker.hasPermission("remotechests.admin");

        if (!allowed && plugin.pluginConfig().protectRegisteredBlocks()) {
            event.setCancelled(true);
            messages.send(breaker, "protected-block",
                    "name", chest.name(), "owner", chest.ownerName());
            return;
        }

        chests.remove(chest);
        if (breaker.getUniqueId().equals(chest.ownerId())) {
            messages.send(breaker, "owner-broke-block", "name", chest.name());
            return;
        }
        // Protecao desligada, ou um admin quebrou: avisa o dono se ele estiver online.
        Player owner = plugin.getServer().getPlayer(chest.ownerId());
        if (owner != null) {
            messages.send(owner, "chest-destroyed",
                    "name", chest.name(), "player", breaker.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    private void protect(List<Block> blocks) {
        if (!plugin.pluginConfig().protectFromExplosions() || chests.count() == 0) {
            return;
        }
        blocks.removeIf(block -> chests.findAtBlock(block).isPresent());
    }
}
