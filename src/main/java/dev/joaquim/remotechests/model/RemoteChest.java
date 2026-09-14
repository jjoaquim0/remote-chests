package dev.joaquim.remotechests.model;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Locale;
import java.util.UUID;

/**
 * Um bau registrado. Guardamos apenas a coordenada do bloco real: o inventario
 * nunca e copiado, ele e sempre lido direto do mundo na hora de abrir.
 */
public final class RemoteChest {

    private final UUID id;
    private final UUID ownerId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final long createdAt;

    private String ownerName;
    private String name;
    private String blockType;
    private long lastOpenedAt;

    public RemoteChest(UUID id, UUID ownerId, String ownerName, String name,
                       String worldName, int x, int y, int z,
                       String blockType, long createdAt, long lastOpenedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
        this.createdAt = createdAt;
        this.lastOpenedAt = lastOpenedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public void ownerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    /** Chave usada nos indices: comparacao de nome e case-insensitive. */
    public String key() {
        return key(name);
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String blockType() {
        return blockType;
    }

    public void blockType(String blockType) {
        this.blockType = blockType;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastOpenedAt() {
        return lastOpenedAt;
    }

    public void lastOpenedAt(long lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }
}
