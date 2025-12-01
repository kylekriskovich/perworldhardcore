package com.kylekriskovich.perworldhardcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PerWorldHardcorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("PerWorldHardcore enabled.");

        // For Phase 1 we just hook a tiny listener that logs deaths in 'world'
        Bukkit.getPluginManager().registerEvents(
                new SimpleHardcoreListener(this),
                this
        );
    }

    @Override
    public void onDisable() {
        getLogger().info("PerWorldHardcore disabled.");
    }
}
