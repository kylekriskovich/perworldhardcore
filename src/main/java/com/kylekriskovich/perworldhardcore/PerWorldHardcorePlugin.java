package com.kylekriskovich.perworldhardcore;

import com.kylekriskovich.perworldhardcore.listener.HardcorePlayerListener;
import com.kylekriskovich.perworldhardcore.command.HardcoreCommands;
import com.kylekriskovich.perworldhardcore.storage.HardcoreDataStorage;
import com.kylekriskovich.perworldhardcore.model.HardcoreWorldSettings;
import com.kylekriskovich.perworldhardcore.storage.PlayerWorldStateStore;
import com.kylekriskovich.perworldhardcore.util.MessageManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;

import java.util.*;
import java.util.Objects;

public class PerWorldHardcorePlugin extends JavaPlugin {

    private final Set<String> hardcoreWorlds = new HashSet<>();

    private HardcoreDataStorage dataStorage;
    private PlayerWorldStateStore playerWorldStateStore;
    private HardcoreWorldSettings hardcoreWorldSettings;

    private boolean hasMultiverseInventories;

    private Plugin multiverseInventories;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);
        checkHardDependencies();
        setHasMultiverseInventories();

        saveDefaultConfig();

        dataStorage = new HardcoreDataStorage(this);
        dataStorage.init();

        this.hardcoreWorldSettings = new HardcoreWorldSettings(getConfig());
        this.playerWorldStateStore = new PlayerWorldStateStore();

        loadHardcoreWorlds();
        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreWorlds);

        getServer().getPluginManager().registerEvents(
                new HardcorePlayerListener(this, hardcoreWorldSettings, playerWorldStateStore),
                this
        );

        Objects.requireNonNull(getCommand("hardcore"))
                .setExecutor(new HardcoreCommands(this));
    }

    @Override
    public void onDisable() {
        if (dataStorage != null) {
            dataStorage.shutdown();
        }
        getLogger().info("PerWorldHardcore disabled.");
    }

    public void loadHardcoreWorlds() {
        hardcoreWorlds.clear();
        List<String> worldsFromConfig = getConfig().getStringList("hardcore-worlds");
        getLogger().info("Config hardcore-worlds raw: " + worldsFromConfig);

        if (worldsFromConfig.isEmpty()) {
            // Fallback so you're never stuck with an empty set while testing
            //hardcoreWorlds.add("world");
            getLogger().warning("No hardcore-worlds defined in config.yml");
            return;
        }

        for (String name : worldsFromConfig) {
            if (name != null && !name.isBlank()) {
                hardcoreWorlds.add(name);
            }
        }
    }

    // Called by listener / commands
    public boolean isPlayerDeadInWorld(UUID uuid, String worldName) {
        return dataStorage != null && dataStorage.isPlayerDeadInWorld(uuid, worldName);
    }

    public void markPlayerDeadInWorld(UUID uuid, String worldName) {
        if (dataStorage != null) {
            dataStorage.markPlayerDeadInWorld(uuid, worldName);
        }
    }

    public void markPlayerVisitedWorld(UUID uuid, String worldName) {
        if (dataStorage != null) {
            dataStorage.markPlayerVisitedWorld(uuid, worldName);
        }
    }

    public Set<String> findCullableWorlds() {
        if (dataStorage == null) return Collections.emptySet();
        return dataStorage.findCullableWorlds(hardcoreWorlds, getHubWorldName());
    }

    public void removeWorldData(String worldName) {
        if (dataStorage != null) {
            dataStorage.removeWorldData(worldName);
        }

        // Also keep the hardcore list and config in sync
        hardcoreWorlds.remove(worldName);
        List<String> current = getConfig().getStringList("hardcore-worlds");
        current.removeIf(w -> w.equalsIgnoreCase(worldName));
        getConfig().set("hardcore-worlds", current);
        saveConfig();
    }

    public Set<String> getHardcoreWorlds() {
        return new HashSet<>(hardcoreWorlds);
    }

    public void addHardcoreWorld(String worldName) {
        hardcoreWorlds.add(worldName);
        List<String> current = getConfig().getStringList("hardcore-worlds");
        if (!current.contains(worldName)) {
            current.add(worldName);
            getConfig().set("hardcore-worlds", current);
            saveConfig();
        }
    }

    public boolean checkHardDependencies() {
        PluginManager pm = getServer().getPluginManager();

        // --- Hard dependencies (Core + NetherPortals) ---
        if (pm.getPlugin("Multiverse-Core") == null) {
            getLogger().severe("Multiverse-Core not found. Disabling plugin.");
            pm.disablePlugin(this);
            return false;
        }

        if (pm.getPlugin("Multiverse-NetherPortals") == null) {
            getLogger().severe("Multiverse-NetherPortals not found. Disabling plugin.");
            pm.disablePlugin(this);
            return false;
        }
        return true;
    }

    public void setHasMultiverseInventories() {
        PluginManager pm = getServer().getPluginManager();

        Plugin inventoriesPlugin = pm.getPlugin("Multiverse-Inventories");
        if (inventoriesPlugin != null && inventoriesPlugin.isEnabled()) {
            this.multiverseInventories = inventoriesPlugin;
            this.hasMultiverseInventories = true;
            getLogger().info("Hooked into Multiverse-Inventories.");
        } else {
            this.hasMultiverseInventories = false;
            getLogger().info("Multiverse-Inventories not found; inventory features disabled.");
        }
    }

    public boolean hasMultiverseInventories() {
        return hasMultiverseInventories;
    }

    public Plugin getMultiverseInventories() {
        return multiverseInventories;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public PlayerWorldStateStore getPlayerWorldStateStore() {
        return playerWorldStateStore;
    }

    public HardcoreWorldSettings getHardcoreWorldSettings() {
        return hardcoreWorldSettings;
    }

    public boolean isHardcoreWorld(String worldName) {
        return worldName != null && hardcoreWorlds.contains(worldName);
    }

    public boolean isAllowSpectatorOnDeath() {
        return getConfig().getBoolean("allow-spectator-on-death", true);
    }

    public boolean isAllowTpAfterDeath() {
        return getConfig().getBoolean("allow-tp-after-death", false);
    }

    public int getMaxOpenHardcoreWorlds() {
        return getConfig().getInt("max-open-hardcore-worlds", 3);
    }

    public String getHubWorldName() {
        return getConfig().getString("hub-world", "world");
    }

    public World getHubWorld() {
        return Bukkit.getWorld(getHubWorldName());
    }

}
