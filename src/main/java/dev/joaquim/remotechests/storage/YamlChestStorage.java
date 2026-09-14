package dev.joaquim.remotechests.storage;

import dev.joaquim.remotechests.model.RemoteChest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class YamlChestStorage implements ChestStorage {

    private final Plugin plugin;
    private final Path file;
    private final Path tempFile;

    public YamlChestStorage(Plugin plugin, Path file) {
        this.plugin = plugin;
        this.file = file;
        this.tempFile = file.resolveSibling(file.getFileName() + ".tmp");
    }

    @Override
    public List<RemoteChest> loadAll() {
        List<RemoteChest> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("chests");
        if (root == null) {
            return result;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            try {
                result.add(new RemoteChest(
                        UUID.fromString(rawId),
                        UUID.fromString(require(section, "owner")),
                        section.getString("owner-name", "?"),
                        require(section, "name"),
                        require(section, "world"),
                        section.getInt("x"),
                        section.getInt("y"),
                        section.getInt("z"),
                        section.getString("type", "CHEST"),
                        section.getLong("created-at"),
                        section.getLong("last-opened-at")
                ));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Registro de bau invalido ignorado: " + rawId, ex);
            }
        }
        return result;
    }

    @Override
    public void saveAll(Collection<RemoteChest> chests) {
        String contents = serialize(chests);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> write(contents));
    }

    @Override
    public void saveAllBlocking(Collection<RemoteChest> chests) {
        write(serialize(chests));
    }

    private String serialize(Collection<RemoteChest> chests) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Gerado pelo RemoteChests. Editar com o servidor ligado pode ser sobrescrito."
        ));
        for (RemoteChest chest : chests) {
            String path = "chests." + chest.id();
            yaml.set(path + ".name", chest.name());
            yaml.set(path + ".owner", chest.ownerId().toString());
            yaml.set(path + ".owner-name", chest.ownerName());
            yaml.set(path + ".world", chest.worldName());
            yaml.set(path + ".x", chest.x());
            yaml.set(path + ".y", chest.y());
            yaml.set(path + ".z", chest.z());
            yaml.set(path + ".type", chest.blockType());
            yaml.set(path + ".created-at", chest.createdAt());
            yaml.set(path + ".last-opened-at", chest.lastOpenedAt());
        }
        return yaml.saveToString();
    }

    private synchronized void write(String contents) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tempFile, contents, StandardCharsets.UTF_8);
            try {
                Files.move(tempFile, file,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Nao foi possivel salvar " + file, ex);
        }
    }

    private static String require(ConfigurationSection section, String key) {
        String value = section.getString(key);
        if (value == null) {
            throw new IllegalStateException("Campo obrigatorio ausente: " + key);
        }
        return value;
    }
}
