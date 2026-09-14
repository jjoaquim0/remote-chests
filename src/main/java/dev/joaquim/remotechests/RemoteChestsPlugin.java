package dev.joaquim.remotechests;

import dev.joaquim.remotechests.command.ChestCommand;
import dev.joaquim.remotechests.config.PluginConfig;
import dev.joaquim.remotechests.listener.ChestProtectionListener;
import dev.joaquim.remotechests.listener.SessionListener;
import dev.joaquim.remotechests.service.ChestAccessService;
import dev.joaquim.remotechests.service.ChestService;
import dev.joaquim.remotechests.storage.ChestStorage;
import dev.joaquim.remotechests.storage.YamlChestStorage;
import dev.joaquim.remotechests.util.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class RemoteChestsPlugin extends JavaPlugin {

    private final Messages messages = new Messages();

    private volatile PluginConfig pluginConfig;
    private ChestService chestService;
    private ChestAccessService accessService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();

        ChestStorage storage = new YamlChestStorage(this, getDataFolder().toPath().resolve("chests.yml"));
        this.chestService = new ChestService(this, storage);
        this.chestService.load();

        this.accessService = new ChestAccessService(this, chestService, messages);

        getServer().getPluginManager().registerEvents(new SessionListener(accessService), this);
        getServer().getPluginManager().registerEvents(
                new ChestProtectionListener(this, chestService, messages), this);

        ChestCommand command = new ChestCommand(this, chestService, accessService, messages);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> command.register(event.registrar()));

        // "Ultimo acesso" muda a cada abertura; grava em lote a cada minuto
        // em vez de reserializar o arquivo inteiro toda vez.
        long everyMinute = 20L * 60L;
        getServer().getScheduler().runTaskTimer(this, chestService::flushIfDirty,
                everyMinute, everyMinute);
    }

    @Override
    public void onDisable() {
        if (accessService != null) {
            accessService.closeAll();
        }
        if (chestService != null) {
            chestService.saveBlocking();
        }
    }

    /** Relê o config.yml e troca o snapshot usado por todo o plugin. */
    public void reloadPluginConfig() {
        reloadConfig();
        this.pluginConfig = PluginConfig.from(getConfig(), getLogger());
        this.messages.load(getConfig());
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public ChestService chestService() {
        return chestService;
    }

    public ChestAccessService accessService() {
        return accessService;
    }
}
