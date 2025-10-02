package com.fadehq.fsync;

import com.fadehq.flibraries.mongodb.MongoDBManager;
import com.fadehq.flibraries.redis.RedisManager;
import com.fadehq.fsync.commands.fSyncCommand;
import com.fadehq.fsync.config.Config;
import com.fadehq.fsync.events.*;
import com.fadehq.fsync.playerprofile.PlayerProfile;
import com.fadehq.fsync.playerprofile.PlayerProfileManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class fSync extends JavaPlugin {

    @Getter
    private static Plugin plugin;

    @Getter
    private static RedisManager redisManager;
    @Getter
    private static MongoDBManager mongoDBManager;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();

        // Config setup
        FileConfiguration config = getConfig();

        Config.region = config.getString("region");
        Config.replicas = config.getInt("replicas");
        Config.server = config.getString("server");

        String mongoHost = config.getString("mongodb.host");
        int mongoPort = config.getInt("mongodb.port");
        String mongoUsername = config.getString("mongodb.username");
        String mongoPassword = config.getString("mongodb.password");
        String mongoDb = config.getString("mongodb.database");

        mongoDBManager = new MongoDBManager(mongoHost, mongoPort, mongoUsername, mongoPassword, mongoDb);
        mongoDBManager.connect();

        mongoDBManager.createCollection("fsync_profiles");

        // Master
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");
        String redisPassword = config.getString("redis.password");
        int redisDb = config.getInt("redis.database");

        // Slaves
        String slaveHost = config.getString("region-host");
        int slavePort = config.getInt("region-port");
        String slavePassword = config.getString("region-password");

        Map<String, RedisManager.RedisServerConfig> slaves = new HashMap<>();
        slaves.put(Config.region, new RedisManager.RedisServerConfig(slaveHost, slavePort, slavePassword));

        redisManager = new RedisManager(redisHost, redisPort, redisPassword,
                slaves, redisDb);

        getServer().getPluginManager().registerEvents(new JoinEvent(), this);
        getServer().getPluginManager().registerEvents(new QuitEvent(), this);
        getServer().getPluginManager().registerEvents(new DamageEvent(), this);
        getServer().getPluginManager().registerEvents(new DropEvent(), this);
        getServer().getPluginManager().registerEvents(new InteractEvent(), this);
        getServer().getPluginManager().registerEvents(new PickupEvent(), this);
        getServer().getPluginManager().registerEvents(new MoveEvent(), this);

        this.getCommand("fsync").setExecutor(new fSyncCommand());

        // Call during onEnable (startup)
        redisManager.initPlayerSyncListener();

        // Save cache/recache
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, ()
                -> PlayerProfileManager.get().saveAll(), 20*60, 20*60);
    }

    @Override
    public void onDisable() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<UUID, PlayerProfile> entry : PlayerProfileManager.get().getCache().entrySet()) {
            UUID uuid = entry.getKey();
            PlayerProfile profile = entry.getValue();
            CompletableFuture<Void> future = profile.save("none").thenRun(()
                    -> PlayerProfileManager.get().removeProfile(uuid));
            futures.add(future);
        }

        // Wait for all saves to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        mongoDBManager.close();
        redisManager.close();
    }
}
