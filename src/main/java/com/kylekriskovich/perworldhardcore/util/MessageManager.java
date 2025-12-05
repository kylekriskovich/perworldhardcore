package com.kylekriskovich.perworldhardcore.util;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;

import org.bukkit.ChatColor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

public class MessageManager {

    private final Properties messages = new Properties();
    private String prefix = "";

    public MessageManager(PerWorldHardcorePlugin plugin) {
        String fileName = "perworldhardcore_en.properties";

        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                plugin.getLogger().warning("Could not find " + fileName + " in jar resources.");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load messages file", e);
        }

        String prefixRaw = messages.getProperty("prefix", "");
        this.prefix = color(prefixRaw);
    }

    @SuppressWarnings("unused")
    public String get(String key) {
        String raw = messages.getProperty(key, key);
        return prefix + color(raw);
    }

    @SuppressWarnings("unused")
    public String get(String key, Map<String, String> placeholders) {
        String result = messages.getProperty(key, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return prefix + color(result);
    }

    @SuppressWarnings("deprecation")
    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}

