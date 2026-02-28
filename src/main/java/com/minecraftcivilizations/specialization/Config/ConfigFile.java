package com.minecraftcivilizations.specialization.Config;

import com.google.gson.Gson;
import com.minecraftcivilizations.specialization.Recipe.RecipeBlocker;
import com.typesafe.config.*;
import lombok.experimental.Delegate;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.typesafe.config.*;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ConfigFile {
    private final String CONFIG_FILE;
    private final Gson gson = new Gson();
    private final @NotNull Plugin plugin;
    private final @NotNull Logger logger;
    private final Supplier defaults;
    @Getter
    private Config config;
    public <K,V> ConfigFile(@NotNull Plugin plugin, @NotNull String filename, @NotNull Supplier<Map<K, V>> defaults) {
        this.CONFIG_FILE = plugin.getDataFolder() + "/" + filename + ".json";
        this.defaults = defaults;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        initialize();
    }
    private void initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
            logger.info("Created plugin data folder: " + plugin.getDataFolder().getPath());
        }
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            this.config = ConfigFactory.parseMap((Map) defaults.get());
            try {
                file.createNewFile();
            } catch (IOException ignored) {}
            try (FileWriter writer = new FileWriter(file)) {
                ConfigRenderOptions options = ConfigRenderOptions.defaults()
                        .setOriginComments(false)
                        .setJson(true)
                        .setFormatted(true);

                // Render the root
                writer.write(this.config.root().render(options));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            load();
        }
        ConfigFilesManager.getConfigFiles().add(this);
    }
    public void load() {
        File file = new File(CONFIG_FILE);
        config = ConfigFactory.parseFile(file).resolve();
    }

    public void reload() {
        load();
        RecipeBlocker.INSTANCE.reloadCache();
        logger.info("Loaded config files for: " + CONFIG_FILE);
    }

    public String getString(String key) {
        return config.getString(key);
    }
    public void setString(String key, String value) {
        config.withValue(key, ConfigValueFactory.fromAnyRef(value));
    }
    public Integer getInt(String key) {
        return config.getInt(key);
    }
    public void setInteger(String key, Integer value) {
        config.withValue(key, ConfigValueFactory.fromAnyRef(value));
    }
    public Double getDouble(String key) {
        return config.getDouble(key);
    }
    public void setDouble(String key, Double value) {
        config.withValue(key, ConfigValueFactory.fromAnyRef(value));
    }
    public Boolean getBoolean(String key) {
        return config.getBoolean(key);
    }
    public void setBoolean(String key, Boolean value) {
        config.withValue(key, ConfigValueFactory.fromAnyRef(value));
    }
    public List<String> getStringList(String key) {
        return config.getStringList(key);
    }
    public void setStringList(String key, List<String> value) {
        config.withValue(key, ConfigValueFactory.fromAnyRef(value));
    }
    public List<Double> getDoubleList(String key) {
        return config.getDoubleList(key);
    }

    public List<? extends Config> getConfigList(String key) {
        return config.getConfigList(key);
    }
    public Config getObject(String key) {
        return config.getConfig(key);
    }

}