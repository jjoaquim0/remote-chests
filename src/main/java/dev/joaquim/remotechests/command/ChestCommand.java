package dev.joaquim.remotechests.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.joaquim.remotechests.RemoteChestsPlugin;
import dev.joaquim.remotechests.model.ChestException;
import dev.joaquim.remotechests.model.RemoteChest;
import dev.joaquim.remotechests.service.ChestAccessService;
import dev.joaquim.remotechests.service.ChestService;
import dev.joaquim.remotechests.util.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ChestCommand {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final RemoteChestsPlugin plugin;
    private final ChestService chests;
    private final ChestAccessService access;
    private final Messages messages;

    public ChestCommand(RemoteChestsPlugin plugin, ChestService chests,
                        ChestAccessService access, Messages messages) {
        this.plugin = plugin;
        this.chests = chests;
        this.access = access;
        this.messages = messages;
    }

    public void register(Commands registrar) {
        registrar.register(build(), "Baus remotos", List.of("baus", "rchest"));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> ownChests = this::suggestOwnChests;

        return Commands.literal("bau")
                .requires(source -> source.getSender().hasPermission("remotechests.use"))
                .executes(this::help)
                .then(Commands.literal("set")
                        .then(Commands.argument("nome", StringArgumentType.word())
                                .executes(this::set)))
                .then(Commands.literal("abrir")
                        .then(Commands.argument("nome", StringArgumentType.word())
                                .suggests(ownChests)
                                .executes(this::open)))
                .then(Commands.literal("lista")
                        .executes(this::list))
                .then(Commands.literal("info")
                        .then(Commands.argument("nome", StringArgumentType.word())
                                .suggests(ownChests)
                                .executes(this::info)))
                .then(Commands.literal("renomear")
                        .then(Commands.argument("nome", StringArgumentType.word())
                                .suggests(ownChests)
                                .then(Commands.argument("novo", StringArgumentType.word())
                                        .executes(this::rename))))
                .then(Commands.literal("remover")
                        .then(Commands.argument("nome", StringArgumentType.word())
                                .suggests(ownChests)
                                .executes(this::remove)))
                .then(Commands.literal("admin")
                        .requires(source -> source.getSender().hasPermission("remotechests.admin"))
                        .then(Commands.literal("lista")
                                .then(Commands.argument("jogador", StringArgumentType.word())
                                        .suggests(this::suggestPlayers)
                                        .executes(this::adminList)))
                        .then(Commands.literal("recarregar")
                                .executes(this::reload)))
                .build();
    }

    // ------------------------------------------------------------------
    // Subcomandos
    // ------------------------------------------------------------------

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        messages.send(sender, "help-header");
        for (String key : List.of("help-set", "help-open", "help-list",
                "help-info", "help-rename", "help-remove")) {
            messages.sendRaw(sender, key);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        return withPlayer(ctx, player -> {
            String name = StringArgumentType.getString(ctx, "nome");
            int distance = plugin.pluginConfig().setDistance();

            Block target = player.getTargetBlockExact(distance);
            if (target == null || target.getType() == Material.AIR) {
                messages.send(player, "not-looking-at-container",
                        "distance", String.valueOf(distance));
                return;
            }

            RemoteChest chest = chests.register(player, name, target);
            messages.send(player, "registered",
                    "name", chest.name(),
                    "world", chest.worldName(),
                    "x", String.valueOf(chest.x()),
                    "y", String.valueOf(chest.y()),
                    "z", String.valueOf(chest.z()));
        });
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        return withPlayer(ctx, player -> {
            RemoteChest chest = chests.require(player.getUniqueId(),
                    StringArgumentType.getString(ctx, "nome"));
            access.open(player, chest);
        });
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        return withPlayer(ctx, player -> {
            List<RemoteChest> owned = chests.listOf(player.getUniqueId());
            if (owned.isEmpty()) {
                messages.send(player, "list-empty");
                return;
            }
            int limit = plugin.pluginConfig().maxChestsPerPlayer();
            messages.send(player, "list-header",
                    "count", String.valueOf(owned.size()),
                    "limit", limit < 0 ? "∞" : String.valueOf(limit));
            for (RemoteChest chest : owned) {
                messages.sendRaw(player, "list-entry",
                        "name", chest.name(),
                        "type", prettyType(chest.blockType()),
                        "world", chest.worldName(),
                        "x", String.valueOf(chest.x()),
                        "y", String.valueOf(chest.y()),
                        "z", String.valueOf(chest.z()));
            }
        });
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        return withPlayer(ctx, player -> {
            RemoteChest chest = chests.require(player.getUniqueId(),
                    StringArgumentType.getString(ctx, "nome"));

            messages.send(player, "info-header", "name", chest.name());
            messages.sendRaw(player, "info-owner", "owner", chest.ownerName());
            messages.sendRaw(player, "info-location",
                    "world", chest.worldName(),
                    "x", String.valueOf(chest.x()),
                    "y", String.valueOf(chest.y()),
                    "z", String.valueOf(chest.z()));
            messages.sendRaw(player, "info-type", "type", prettyType(chest.blockType()));
            messages.sendRaw(player, "info-created", "date", formatDate(chest.createdAt()));
            messages.sendRaw(player, "info-last-opened", "date", formatDate(chest.lastOpenedAt()));

            access.peek(player, chest, container -> messages.sendRaw(player, "info-usage",
                    "used", String.valueOf(ChestAccessService.usedSlots(container.getInventory())),
                    "size", String.valueOf(container.getInventory().getSize())));
        });
    }

    private int rename(CommandContext<CommandSourceStack> ctx) {
        return withPlayer(ctx, player -> {
            String oldName = StringArgumentType.getString(ctx, "nome");
            String newName = StringArgumentType.getString(ctx, "novo");
            RemoteChest chest = chests.require(player.getUniqueId(), oldName);
            chests.rename(chest, newName);
            messages.send(player, "renamed", "old", oldName, "name", newName);
        });
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        return withPlayer(ctx, player -> {
            RemoteChest chest = chests.require(player.getUniqueId(),
                    StringArgumentType.getString(ctx, "nome"));
            chests.remove(chest);
            messages.send(player, "removed", "name", chest.name());
        });
    }

    private int adminList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String target = StringArgumentType.getString(ctx, "jogador");

        UUID targetId = resolvePlayerId(target);
        if (targetId == null) {
            messages.send(sender, "player-not-found", "player", target);
            return 0;
        }

        List<RemoteChest> owned = chests.listOf(targetId);
        messages.send(sender, "admin-list-header",
                "player", target, "count", String.valueOf(owned.size()));
        for (RemoteChest chest : owned) {
            messages.sendRaw(sender, "list-entry",
                    "name", chest.name(),
                    "type", prettyType(chest.blockType()),
                    "world", chest.worldName(),
                    "x", String.valueOf(chest.x()),
                    "y", String.valueOf(chest.y()),
                    "z", String.valueOf(chest.z()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        plugin.reloadPluginConfig();
        messages.send(ctx.getSource().getSender(), "reloaded");
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------
    // Sugestoes
    // ------------------------------------------------------------------

    private CompletableFuture<Suggestions> suggestOwnChests(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getSender() instanceof Player player) {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String name : chests.namesOf(player.getUniqueId())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(online.getName());
            }
        }
        return builder.buildFuture();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Roda a acao so para jogadores e traduz ChestException em mensagem. */
    private int withPlayer(CommandContext<CommandSourceStack> ctx, PlayerAction action) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return 0;
        }
        try {
            action.run(player);
            return Command.SINGLE_SUCCESS;
        } catch (ChestException ex) {
            messages.send(player, ex.messageKey(), ex.placeholders());
            return 0;
        }
    }

    /**
     * Resolve pelo jogador online e, se nao achar, pelo dono registrado.
     * Evita varrer o playerdata inteiro so para listar baus.
     */
    private UUID resolvePlayerId(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (RemoteChest chest : chests.all()) {
            if (name.equalsIgnoreCase(chest.ownerName())) {
                return chest.ownerId();
            }
        }
        return null;
    }

    private String formatDate(long epochMillis) {
        if (epochMillis <= 0) {
            return messages.plain("info-never");
        }
        return DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String prettyType(String materialName) {
        String lower = materialName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player);
    }
}
