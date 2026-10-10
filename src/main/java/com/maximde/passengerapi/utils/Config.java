package com.maximde.passengerapi.utils;

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@Getter
public class Config {

    private File file;
    private YamlConfiguration cfg;
    private final File dataFolder;

    private boolean listenToPassengerSet;
    private boolean listenToEntityDestroy;
    private boolean vehicleExitEvent;
    private boolean vehicleExitPacket;
    private boolean resendToViewerOnly;

    public Config(File dataFolder) {
        this.dataFolder = dataFolder;
        checkConfigs();
        loadConfig();
        initDefaults();
        initValues();
    }

    private void initDefaults() {
        setIfNot("AutoPassengerDetection.SetPassengerPacket", true);
        setIfNot("AutoPassengerDetection.EntityDestroyPacket", true);
        setIfNot("AutoPassengerDetection.VehicleExitEvent", true);
        setIfNot("AutoPassengerDetection.VehicleExitPacket", true);
        setIfNot("AutoPassengerDetection.ResendToViewerOnly", true);
    }

    private void initValues() {
        this.listenToPassengerSet = cfg.getBoolean("AutoPassengerDetection.SetPassengerPacket");
        this.listenToEntityDestroy = cfg.getBoolean("AutoPassengerDetection.EntityDestroyPacket");
        this.vehicleExitEvent = cfg.getBoolean("AutoPassengerDetection.VehicleExitEvent");
        this.vehicleExitPacket = cfg.getBoolean("AutoPassengerDetection.VehicleExitPacket");
        this.resendToViewerOnly = cfg.getBoolean("AutoPassengerDetection.ResendToViewerOnly", true);
    }

    public boolean isConfigEmpty() {
        return cfg.getKeys(false).isEmpty();
    }

    private void checkConfigs() {
        File file = new File("plugins/PassengerAPI", "config.yml");
        if (file.exists()) return;

        file.getParentFile().mkdirs();
        try (InputStream inputStream = getClass().getResourceAsStream("/" + "config.yml")) {
            Files.copy(inputStream, file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        file = new File("plugins/PassengerAPI", "config.yml");
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        this.cfg = YamlConfiguration.loadConfiguration(file);
        initValues();
    }

    public void saveConfig() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setIfNot(String path, Object value) {
        if(!cfg.isSet(path)) setValue(path, value);
    }

    public void setValue(String path, Object value) {
        this.cfg.set(path, value);
    }

    public Object getValue(String path) {
        return this.cfg.get(path);
    }

}
