package dev.joaquim.remotechests.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Snapshot imutavel do config.yml, recriado a cada reload. */
public final class PluginConfig {

    private final int maxChestsPerPlayer;
    private final int setDistance;
    private final boolean allowCrossWorld;
    private final int openCooldownSeconds;
    private final boolean animateRealBlock;
    private final boolean protectRegisteredBlocks;
    private final boolean protectFromExplosions;
    private final boolean autoRemoveMissing;
    private final Set<Material> allowedContainers;

    private PluginConfig(int maxChestsPerPlayer, int setDistance, boolean allowCrossWorld,
                         int openCooldownSeconds, boolean animateRealBlock,
                         boolean protectRegisteredBlocks, boolean protectFromExplosions,
                         boolean autoRemoveMissing, Set<Material> allowedContainers) {
        this.maxChestsPerPlayer = maxChestsPerPlayer;
        this.setDistance = setDistance;
        this.allowCrossWorld = allowCrossWorld;
        this.openCooldownSeconds = openCooldownSeconds;
        this.animateRealBlock = animateRealBlock;
        this.protectRegisteredBlocks = protectRegisteredBlocks;
        this.protectFromExplosions = protectFromExplosions;
        this.autoRemoveMissing = autoRemoveMissing;
        this.allowedContainers = allowedContainers;
    }

    public static PluginConfig from(FileConfiguration config, Logger logger) {
        Set<Material> allowed = EnumSet.noneOf(Material.class);
        List<String> raw = config.getStringList("settings.allowed-containers");
        for (String entry : raw) {
            Material material = Material.matchMaterial(entry.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                logger.warning("Material desconhecido em allowed-containers: " + entry);
                continue;
            }
            allowed.add(material);
        }
        if (allowed.isEmpty()) {
            allowed.add(Material.CHEST);
            allowed.add(Material.TRAPPED_CHEST);
            allowed.add(Material.BARREL);
        }

        return new PluginConfig(
                config.getInt("settings.max-chests-per-player", 20),
                Math.max(1, config.getInt("settings.set-distance", 6)),
                config.getBoolean("settings.allow-cross-world", true),
                Math.max(0, config.getInt("settings.open-cooldown-seconds", 2)),
                config.getBoolean("settings.animate-real-block", true),
                config.getBoolean("settings.protect-registered-blocks", true),
                config.getBoolean("settings.protect-from-explosions", true),
                config.getBoolean("settings.auto-remove-missing", false),
                allowed
        );
    }

    public int maxChestsPerPlayer() {
        return maxChestsPerPlayer;
    }

    public int setDistance() {
        return setDistance;
    }

    public boolean allowCrossWorld() {
        return allowCrossWorld;
    }

    public int openCooldownSeconds() {
        return openCooldownSeconds;
    }

    public boolean animateRealBlock() {
        return animateRealBlock;
    }

    public boolean protectRegisteredBlocks() {
        return protectRegisteredBlocks;
    }

    public boolean protectFromExplosions() {
        return protectFromExplosions;
    }

    public boolean autoRemoveMissing() {
        return autoRemoveMissing;
    }

    public boolean isAllowedContainer(Material material) {
        return allowedContainers.contains(material);
    }
}
