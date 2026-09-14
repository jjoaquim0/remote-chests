package dev.joaquim.remotechests.service;

import dev.joaquim.remotechests.RemoteChestsPlugin;
import dev.joaquim.remotechests.config.PluginConfig;
import dev.joaquim.remotechests.model.RemoteChest;
import dev.joaquim.remotechests.util.Messages;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abre baus remotos.
 *
 * <p>A ideia central: nao existe inventario "virtual". Carregamos o chunk do
 * bloco real, pegamos o {@link Container#getInventory()} dele e entregamos esse
 * mesmo inventario para o jogador. Por isso tudo funciona como se ele estivesse
 * na frente do bau — funis continuam puxando, dois jogadores veem o mesmo
 * conteudo, bau duplo abre com 54 slots, e nada precisa ser sincronizado.
 *
 * <p>Enquanto alguem esta com o bau aberto seguramos um chunk ticket para o
 * chunk nao descarregar debaixo do inventario.
 */
public final class ChestAccessService {

    /** Uma sessao = um jogador com um bau remoto aberto. */
    private record Session(UUID chestId, String worldName, int chunkX, int chunkZ,
                           int blockX, int blockY, int blockZ, boolean lidOpened) {
    }

    private final RemoteChestsPlugin plugin;
    private final ChestService chests;
    private final Messages messages;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, Integer> chunkTickets = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ChestAccessService(RemoteChestsPlugin plugin, ChestService chests, Messages messages) {
        this.plugin = plugin;
        this.chests = chests;
        this.messages = messages;
    }

    // ------------------------------------------------------------------
    // Abertura
    // ------------------------------------------------------------------

    public void open(Player player, RemoteChest chest) {
        PluginConfig config = plugin.pluginConfig();

        World world = chest.world();
        if (world == null) {
            messages.send(player, "world-not-loaded", "world", chest.worldName());
            return;
        }
        if (!config.allowCrossWorld() && !world.equals(player.getWorld())) {
            messages.send(player, "cross-world-denied", "world", chest.worldName());
            return;
        }

        long remaining = cooldownRemaining(player, config);
        if (remaining > 0) {
            messages.send(player, "cooldown", "seconds", String.valueOf((remaining + 999) / 1000));
            return;
        }

        boolean loaded = world.isChunkLoaded(chest.chunkX(), chest.chunkZ());
        if (!loaded) {
            messages.send(player, "opening", "name", chest.name());
        }

        withChunk(world, chest, chunk -> {
            if (!player.isOnline()) {
                return;
            }
            Container container = containerAt(chunk.getWorld(), chest);
            if (container == null) {
                reportMissing(player, chest);
                return;
            }
            doOpen(player, chest, chunk, container, config);
        });
    }

    private void doOpen(Player player, RemoteChest chest, Chunk chunk,
                        Container container, PluginConfig config) {
        // Fecha qualquer inventario anterior antes de registrar a nova sessao,
        // senao o InventoryCloseEvent do anterior apagaria a sessao nova.
        player.closeInventory();

        String ticketKey = ticketKey(chest);
        acquireTicket(chunk, ticketKey);

        Inventory inventory = container.getInventory();
        InventoryView view = player.openInventory(inventory);
        if (view == null) {
            releaseTicket(chunk.getWorld(), chest.chunkX(), chest.chunkZ(), ticketKey);
            messages.send(player, "missing-block",
                    "name", chest.name(), "world", chest.worldName(),
                    "x", String.valueOf(chest.x()), "y", String.valueOf(chest.y()),
                    "z", String.valueOf(chest.z()));
            return;
        }

        boolean lidOpened = false;
        if (config.animateRealBlock()) {
            lidOpened = setLid(chest, true);
        }

        sessions.put(player.getUniqueId(), new Session(
                chest.id(), chest.worldName(), chest.chunkX(), chest.chunkZ(),
                chest.x(), chest.y(), chest.z(), lidOpened));

        if (config.openCooldownSeconds() > 0 && !player.hasPermission("remotechests.admin")) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
        chests.touchOpened(chest, player);
    }

    /**
     * Le o conteudo do bau sem abrir (usado pelo /bau info). O callback recebe
     * {@code null} se o bloco nao existir mais.
     */
    public void peek(Player viewer, RemoteChest chest, Consumer<Container> callback) {
        World world = chest.world();
        if (world == null) {
            messages.send(viewer, "world-not-loaded", "world", chest.worldName());
            return;
        }
        withChunk(world, chest, chunk -> {
            Container container = containerAt(chunk.getWorld(), chest);
            if (container == null) {
                reportMissing(viewer, chest);
                return;
            }
            callback.accept(container);
        });
    }

    // ------------------------------------------------------------------
    // Fim de sessao
    // ------------------------------------------------------------------

    /** Idempotente: pode ser chamado pelo close, pelo quit e pelo shutdown. */
    public void endSession(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session == null) {
            return;
        }

        // O refcount sai do mapa mesmo se o mundo tiver sido descarregado,
        // senao a contagem fica presa e o proximo release nao bate.
        String key = session.worldName() + ':' + session.chunkX() + ':' + session.chunkZ();
        boolean lastHolder = decrementTicket(key);

        World world = plugin.getServer().getWorld(session.worldName());
        if (world == null) {
            return;
        }
        if (session.lidOpened()) {
            setLid(world, session.blockX(), session.blockY(), session.blockZ(), false);
        }
        if (lastHolder) {
            world.removePluginChunkTicket(session.chunkX(), session.chunkZ(), plugin);
        }
    }

    public void closeAll() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            }
            endSession(playerId);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    public static int usedSlots(Inventory inventory) {
        int used = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                used++;
            }
        }
        return used;
    }

    private void withChunk(World world, RemoteChest chest, Consumer<Chunk> callback) {
        world.getChunkAtAsync(chest.chunkX(), chest.chunkZ(), true).thenAccept(chunk -> {
            if (plugin.getServer().isPrimaryThread()) {
                callback.accept(chunk);
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(chunk));
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Falha ao carregar o chunk do bau " + chest.name()
                    + ": " + throwable.getMessage());
            return null;
        });
    }

    private Container containerAt(World world, RemoteChest chest) {
        // Nao filtramos por allowed-containers aqui: tirar um material da lista
        // do config nao pode quebrar baus que ja estavam registrados.
        BlockState state = world.getBlockAt(chest.x(), chest.y(), chest.z()).getState();
        return state instanceof Container container ? container : null;
    }

    private void reportMissing(Player player, RemoteChest chest) {
        messages.send(player, "missing-block",
                "name", chest.name(), "world", chest.worldName(),
                "x", String.valueOf(chest.x()), "y", String.valueOf(chest.y()),
                "z", String.valueOf(chest.z()));

        if (plugin.pluginConfig().autoRemoveMissing()) {
            chests.remove(chest);
            messages.send(player, "auto-removed", "name", chest.name());
        }
    }

    private boolean setLid(RemoteChest chest, boolean open) {
        World world = chest.world();
        return world != null && setLid(world, chest.x(), chest.y(), chest.z(), open);
    }

    private boolean setLid(World world, int x, int y, int z, boolean open) {
        BlockState state = world.getBlockAt(x, y, z).getState();
        if (!(state instanceof Lidded lidded)) {
            return false;
        }
        if (open) {
            lidded.open();
        } else {
            lidded.close();
        }
        return true;
    }

    private long cooldownRemaining(Player player, PluginConfig config) {
        if (config.openCooldownSeconds() <= 0 || player.hasPermission("remotechests.admin")) {
            return 0;
        }
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, config.openCooldownSeconds() * 1000L - elapsed);
    }

    private static String ticketKey(RemoteChest chest) {
        return chest.worldName() + ':' + chest.chunkX() + ':' + chest.chunkZ();
    }

    private void acquireTicket(Chunk chunk, String key) {
        int count = chunkTickets.merge(key, 1, Integer::sum);
        if (count == 1) {
            chunk.addPluginChunkTicket(plugin);
        }
    }

    /** Retorna true se esta era a ultima sessao usando o chunk. */
    private boolean decrementTicket(String key) {
        Integer count = chunkTickets.get(key);
        if (count == null) {
            return false;
        }
        if (count <= 1) {
            chunkTickets.remove(key);
            return true;
        }
        chunkTickets.put(key, count - 1);
        return false;
    }

    private void releaseTicket(World world, int chunkX, int chunkZ, String key) {
        if (decrementTicket(key)) {
            world.removePluginChunkTicket(chunkX, chunkZ, plugin);
        }
    }

    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
    }
}
