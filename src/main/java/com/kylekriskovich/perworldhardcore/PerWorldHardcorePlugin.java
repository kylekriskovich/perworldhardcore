package com.kylekriskovich.perworldhardcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;




public class PerWorldHardcorePlugin extends JavaPlugin {

    private final Set<String> hardcoreWorlds = new HashSet<>();
    private final Map<UUID, Set<String>> deadWorlds = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Debug: log exactly what Bukkit thinks the config is
        getLogger().info("Config hardcore-worlds raw: " + getConfig().getStringList("hardcore-worlds"));

        loadHardcoreWorlds();
        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreWorlds);

        Bukkit.getPluginManager().registerEvents(
                new SimpleHardcoreListener(this),
                this
        );
    }

    public void loadHardcoreWorlds() {
        hardcoreWorlds.clear();
        List<String> worldsFromConfig = getConfig().getStringList("hardcore-worlds");
        getLogger().info("Config hardcore-worlds raw: " + worldsFromConfig);

        if (worldsFromConfig == null || worldsFromConfig.isEmpty()) {
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

    public boolean isHardcoreWorld(String worldName) {
        return worldName != null && hardcoreWorlds.contains(worldName);
    }

    public boolean isPlayerDeadInWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return false;
        Set<String> worlds = deadWorlds.get(uuid);
        return worlds != null && worlds.contains(worldName);
    }

    public void markPlayerDeadInWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return;
        deadWorlds.computeIfAbsent(uuid, k -> new HashSet<>()).add(worldName);
        getLogger().info("Marked " + uuid + " as dead in hardcore world '" + worldName + "'.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PerWorldHardcore disabled.");
    }
}
