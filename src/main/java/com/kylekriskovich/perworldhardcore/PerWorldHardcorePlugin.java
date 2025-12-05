package com.kylekriskovich.perworldhardcore;

import com.kylekriskovich.perworldhardcore.command.HardcoreCommands;
import com.kylekriskovich.perworldhardcore.listener.HardcorePlayerListener;
import com.kylekriskovich.perworldhardcore.model.HardcoreWorldSettings;
import com.kylekriskovich.perworldhardcore.storage.HardcoreDataStorage;
import com.kylekriskovich.perworldhardcore.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PerWorldHardcorePlugin extends JavaPlugin {

    private final Map<String, HardcoreWorldSettings> hardcoreWorlds = new HashMap<>();

    private HardcoreDataStorage dataStorage;

    private boolean hasMultiverseInventories;
    private Plugin multiverseInventories;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);

        // Check hard deps (Core + NetherPortals)
        if (!checkHardDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setHasMultiverseInventories();

        // Make sure config.yml exists
        saveDefaultConfig();

        // Load hardcore worlds + their settings from config
        loadHardcoreWorlds();

        // Init storage (data.yml)
        dataStorage = new HardcoreDataStorage(this);
        dataStorage.init();

        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreWorlds.keySet());

        // Register listeners
        getServer().getPluginManager().registerEvents(
                new HardcorePlayerListener(this),
                this
        );

        // Register command
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
            getLogger().warning("No hardcore-worlds defined in config.yml");
            return;
        }

        boolean globalSpectator = getConfig().getBoolean("allow-spectator-on-death", true);
        boolean globalTpAfterDeath = getConfig().getBoolean("allow-tp-after-death", false);

        for (String name : worldsFromConfig) {
            if (name == null || name.isBlank()) {
                continue;
            }

            HardcoreWorldSettings settings = new HardcoreWorldSettings(name, getConfig());
            settings.setAllowSpectatorOnDeath(globalSpectator);
            settings.setAllowTpAfterDeath(globalTpAfterDeath);

            hardcoreWorlds.put(name, settings);
        }
    }

    // ------------------------------------------------------------------------
    // Player state helpers (proxy to HardcoreDataStorage)
    // ------------------------------------------------------------------------

    public boolean hasDiedInWorld(UUID uuid, String worldName) {
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
        // HardcoreDataStorage expects Set<String> of world names
        return dataStorage.findCullableWorlds(getHardcoreWorlds(), getHubWorldName());
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

    // ------------------------------------------------------------------------
    // Hardcore world registry
    // ------------------------------------------------------------------------

    /** Returns hardcore world names (not settings). */
    public Set<String> getHardcoreWorlds() {
        return new HashSet<>(hardcoreWorlds.keySet());
    }

    /** Adds a new hardcore world, updates in-memory map and config list. */
    public void addHardcoreWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return;

        // Create settings in memory if not already present
        if (!hardcoreWorlds.containsKey(worldName)) {
            HardcoreWorldSettings settings = new HardcoreWorldSettings(worldName, getConfig());
            settings.setAllowSpectatorOnDeath(isAllowSpectatorOnDeath());
            settings.setAllowTpAfterDeath(isAllowTpAfterDeath());
            hardcoreWorlds.put(worldName, settings);
        }

        // Keep config list in sync
        List<String> current = getConfig().getStringList("hardcore-worlds");
        if (!current.contains(worldName)) {
            current.add(worldName);
            getConfig().set("hardcore-worlds", current);
            saveConfig();
        }
    }

    // ------------------------------------------------------------------------
    // Dependency wiring
    // ------------------------------------------------------------------------

    public boolean checkHardDependencies() {
        PluginManager pm = getServer().getPluginManager();

        if (pm.getPlugin("Multiverse-Core") == null) {
            getLogger().severe("Multiverse-Core not found. Disabling plugin.");
            return false;
        }

        if (pm.getPlugin("Multiverse-NetherPortals") == null) {
            getLogger().severe("Multiverse-NetherPortals not found. Disabling plugin.");
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

    @SuppressWarnings("unused")
    public boolean hasMultiverseInventories() {
        return hasMultiverseInventories;
    }

    @SuppressWarnings("unused")
    public Plugin getMultiverseInventories() {
        return multiverseInventories;
    }

    // ------------------------------------------------------------------------
    // Accessors used by listeners / commands
    // ------------------------------------------------------------------------

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public boolean isHardcoreWorld(String worldName) {
        return worldName != null && hardcoreWorlds.containsKey(worldName);
    }

    public HardcoreWorldSettings getHardcoreWorldSettings(String worldName) {
        return hardcoreWorlds.get(worldName);
    }

    // Global defaults (still used for now, and for new worlds)
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
