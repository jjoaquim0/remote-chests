package dev.joaquim.remotechests.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Le a secao "messages" do config.yml e renderiza com MiniMessage.
 * Placeholders sao passados como pares chave/valor: send(p, "registered", "name", "Estoque").
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> templates = new HashMap<>();
    private String prefix = "";

    public void load(FileConfiguration config) {
        templates.clear();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                templates.put(key, value);
            }
        }
        prefix = templates.getOrDefault("prefix", "");
    }

    /** Renderiza sem prefixo (util para listas e hovers). */
    public Component render(String key, String... placeholders) {
        return MINI.deserialize(template(key), resolvers(placeholders));
    }

    public Component renderPrefixed(String key, String... placeholders) {
        return MINI.deserialize(prefix + template(key), resolvers(placeholders));
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(renderPrefixed(key, placeholders));
    }

    public void sendRaw(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(render(key, placeholders));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(MINI.deserialize(prefix + template(key), resolvers(placeholders)));
    }

    /** Texto cru do template, para pedacos que entram como placeholder de outra mensagem. */
    public String plain(String key) {
        return template(key);
    }

    private String template(String key) {
        return templates.getOrDefault(key, "<red>Mensagem ausente no config.yml: " + key);
    }

    private static TagResolver resolvers(String... placeholders) {
        if (placeholders.length == 0) {
            return TagResolver.empty();
        }
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders devem vir em pares chave/valor");
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i < placeholders.length; i += 2) {
            builder.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        return builder.build();
    }

    private static TagResolver resolvers(Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return TagResolver.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        placeholders.forEach((key, value) -> builder.resolver(Placeholder.unparsed(key, value)));
        return builder.build();
    }
}
