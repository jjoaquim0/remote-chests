package dev.joaquim.remotechests.service;

import dev.joaquim.remotechests.RemoteChestsPlugin;
import dev.joaquim.remotechests.config.PluginConfig;
import dev.joaquim.remotechests.model.ChestException;
import dev.joaquim.remotechests.model.RemoteChest;
import dev.joaquim.remotechests.storage.ChestStorage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Registro central dos baus. Todas as operacoes rodam no thread principal;
 * as buscas por bloco sao indexadas para nao varrer a lista a cada evento.
 */
public final class ChestService {

    /** Nome ASCII para nao brigar com o parser de argumentos do Brigadier. */
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final RemoteChestsPlugin plugin;
    private final ChestStorage storage;

    private final Map<UUID, RemoteChest> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, RemoteChest>> byOwner = new ConcurrentHashMap<>();
    private final Map<String, RemoteChest> byBlock = new ConcurrentHashMap<>();

    /** Mudancas baratas (ultimo acesso) sao acumuladas e gravadas em lote. */
    private boolean dirty;

    public ChestService(RemoteChestsPlugin plugin, ChestStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load() {
        byId.clear();
        byOwner.clear();
        byBlock.clear();
        for (RemoteChest chest : storage.loadAll()) {
            index(chest);
        }
        plugin.getLogger().info("Carregados " + byId.size() + " baus remotos.");
    }

    public void save() {
        dirty = false;
        storage.saveAll(new ArrayList<>(byId.values()));
    }

    /** Marca que existe alteracao pendente, sem tocar no disco agora. */
    public void markDirty() {
        dirty = true;
    }

    public void flushIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void saveBlocking() {
        dirty = false;
        storage.saveAllBlocking(new ArrayList<>(byId.values()));
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public Optional<RemoteChest> find(UUID ownerId, String name) {
        Map<String, RemoteChest> owned = byOwner.get(ownerId);
        return owned == null ? Optional.empty() : Optional.ofNullable(owned.get(RemoteChest.key(name)));
    }

    public RemoteChest require(UUID ownerId, String name) {
        return find(ownerId, name)
                .orElseThrow(() -> new ChestException("not-found", Map.of("name", name)));
    }

    public List<RemoteChest> listOf(UUID ownerId) {
        Map<String, RemoteChest> owned = byOwner.get(ownerId);
        if (owned == null) {
            return List.of();
        }
        List<RemoteChest> list = new ArrayList<>(owned.values());
        list.sort(Comparator.comparing(RemoteChest::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public List<String> namesOf(UUID ownerId) {
        return listOf(ownerId).stream().map(RemoteChest::name).toList();
    }

    public Optional<RemoteChest> findAtBlock(Block block) {
        return Optional.ofNullable(byBlock.get(blockKey(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ())));
    }

    public Collection<RemoteChest> all() {
        return List.copyOf(byId.values());
    }

    public int count() {
        return byId.size();
    }

    // ------------------------------------------------------------------
    // Mutacoes
    // ------------------------------------------------------------------

    public RemoteChest register(Player owner, String name, Block block) {
        PluginConfig config = plugin.pluginConfig();

        if (!isValidName(name)) {
            throw new ChestException("invalid-name");
        }

        Material type = block.getType();
        if (!config.isAllowedContainer(type)) {
            throw new ChestException("container-not-allowed", Map.of("type", type.name()));
        }

        findAtBlock(block).ifPresent(existing -> {
            throw new ChestException("already-registered", Map.of(
                    "name", existing.name(),
                    "owner", existing.ownerName()));
        });

        if (find(owner.getUniqueId(), name).isPresent()) {
            throw new ChestException("name-taken", Map.of("name", name));
        }

        int limit = config.maxChestsPerPlayer();
        if (limit >= 0 && !owner.hasPermission("remotechests.unlimited")
                && listOf(owner.getUniqueId()).size() >= limit) {
            throw new ChestException("limit-reached", Map.of("limit", String.valueOf(limit)));
        }

        long now = System.currentTimeMillis();
        RemoteChest chest = new RemoteChest(
                UUID.randomUUID(),
                owner.getUniqueId(),
                owner.getName(),
                name,
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                type.name(),
                now,
                0L);

        index(chest);
        save();
        return chest;
    }

    public void remove(RemoteChest chest) {
        byId.remove(chest.id());
        Map<String, RemoteChest> owned = byOwner.get(chest.ownerId());
        if (owned != null) {
            owned.remove(chest.key());
            if (owned.isEmpty()) {
                byOwner.remove(chest.ownerId());
            }
        }
        byBlock.remove(blockKey(chest.worldName(), chest.x(), chest.y(), chest.z()));
        save();
    }

    public void rename(RemoteChest chest, String newName) {
        if (!isValidName(newName)) {
            throw new ChestException("invalid-name");
        }
        Optional<RemoteChest> clash = find(chest.ownerId(), newName);
        if (clash.isPresent() && !clash.get().id().equals(chest.id())) {
            throw new ChestException("name-taken", Map.of("name", newName));
        }

        Map<String, RemoteChest> owned = byOwner.get(chest.ownerId());
        if (owned != null) {
            owned.remove(chest.key());
        }
        chest.name(newName);
        byOwner.computeIfAbsent(chest.ownerId(), ignored -> new ConcurrentHashMap<>())
                .put(chest.key(), chest);
        save();
    }

    public void touchOpened(RemoteChest chest, Player opener) {
        chest.lastOpenedAt(System.currentTimeMillis());
        if (chest.ownerId().equals(opener.getUniqueId())) {
            chest.ownerName(opener.getName());
        }
        markDirty();
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    // ------------------------------------------------------------------

    private void index(RemoteChest chest) {
        byId.put(chest.id(), chest);
        byOwner.computeIfAbsent(chest.ownerId(), ignored -> new ConcurrentHashMap<>())
                .put(chest.key(), chest);
        byBlock.put(blockKey(chest.worldName(), chest.x(), chest.y(), chest.z()), chest);
    }

    private static String blockKey(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }
}
