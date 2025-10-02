package com.fadehq.fsync.playerprofile;

import com.fadehq.flibraries.mongodb.MongoDBManager;
import com.fadehq.flibraries.redis.RedisManager;
import com.fadehq.fsync.api.ExternalDataProvider;
import com.fadehq.fsync.config.Config;
import com.fadehq.fsync.fSync;
import com.google.gson.Gson;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerProfile {

    private static final Gson GSON = new Gson();

    private final UUID uuid;
    private String name;
    private double health;
    private double food;
    private String inventoryData;
    private String enderchestData;
    private String armorData;
    private List<String> potionEffects;
    private String offhandData;
    private int selectedSlot;
    private float saturation;
    private int level;
    private float exp;
    private String gamemode;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.health = 20.0;
        this.food = 20.0;
        this.inventoryData = serializeInventory(Bukkit.createInventory(null, 36));
        this.armorData = serializeItemArray(new ItemStack[4]);
        this.enderchestData = serializeInventory(Bukkit.createInventory(null, 27));
        this.potionEffects = new ArrayList<>();
        this.offhandData = null;
        this.selectedSlot = 0;
        this.saturation = 20.0f;
        this.level = 0;
        this.exp = 0.0f;
        this.gamemode = GameMode.SURVIVAL.name();
    }

    // API Usage
    private static final List<ExternalDataProvider> externalProviders = new ArrayList<>();

    public static void registerExternalProvider(ExternalDataProvider provider) {
        externalProviders.add(provider);
    }

    public static List<ExternalDataProvider> getExternalProviders() {
        return externalProviders;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    private String getRedisKey() {
        return "fsync:playerprofile:" + uuid;
    }

    private Document toDocument() {
        Document document = new Document("uuid", uuid.toString())
                .append("name", name)
                .append("health", health)
                .append("food", food)
                .append("inventory", inventoryData)
                .append("enderchest", enderchestData)
                .append("armor", armorData)
                .append("potions", potionEffects)
                .append("offhand", offhandData)
                .append("selectedSlot", selectedSlot)
                .append("saturation", saturation)
                .append("level", level)
                .append("exp", exp)
                .append("gamemode", gamemode);

        for (ExternalDataProvider provider : externalProviders) {
            provider.writeToDocument(uuid, document);
        }

        return document;
    }

    private static PlayerProfile fromDocument(Document doc) {
        UUID uuid = UUID.fromString(doc.getString("uuid"));
        String name = doc.getString("name");
        PlayerProfile profile = new PlayerProfile(uuid, name);
        profile.health = doc.getDouble("health");
        profile.food = doc.getDouble("food");
        profile.inventoryData = doc.getString("inventory");
        profile.enderchestData = doc.getString("enderchest");
        profile.armorData = doc.getString("armor");
        profile.potionEffects = (List<String>) doc.get("potions");
        profile.offhandData = doc.getString("offhand");
        profile.selectedSlot = doc.getInteger("selectedSlot");
        profile.level = doc.getInteger("level");
        profile.gamemode = doc.getString("gamemode");
        Float saturation = null;
        Float exp = null;
        Object expRaw = doc.get("exp");
        if (expRaw instanceof Number) {
            exp = ((Number) expRaw).floatValue();
        }
        profile.exp = exp;
        Object raw = doc.get("saturation");
        if (raw instanceof Number) {
            saturation = ((Number) raw).floatValue();
        }
        profile.saturation = saturation;

        for (ExternalDataProvider provider : externalProviders) {
            provider.readFromDocument(uuid, doc);
        }

        return profile;
    }

    public CompletableFuture<Void> save(String currentServer) {
        RedisManager redis = fSync.getRedisManager();
        MongoDBManager mongo = fSync.getMongoDBManager();
        Player player = Bukkit.getPlayer(uuid);

        return CompletableFuture.runAsync(() -> {
            if (player != null) {
                name = player.getName();
                health = player.getHealth();
                food = player.getFoodLevel();
                inventoryData = serializeInventory(player.getInventory());
                armorData = serializeItemArray(player.getInventory().getArmorContents());
                enderchestData = serializeInventory(player.getEnderChest());
                potionEffects = serializePotions(player.getActivePotionEffects());
                offhandData = serializeItem(player.getInventory().getItemInOffHand());
                selectedSlot = player.getInventory().getHeldItemSlot();
                saturation = player.getSaturation();
                level = player.getLevel();
                exp = player.getExp();
                gamemode = player.getGameMode().name();
            }

            boolean saved = redis.setAndWaitAsync(getRedisKey(), toDocument().toJson(),
                    Config.replicas, 8000, 3, 1800).join();
            if (saved) {
                redis.setWithExpirySync("fsync:currentserver:" + uuid, currentServer, 1800);
                redis.publishAsync("player_sync_done", uuid.toString());
            } else {
                fSync.getPlugin().getLogger().warning("Warning: Redis replication failed, data may not be on all replicas!");
            }

            Document filter = new Document("uuid", uuid.toString());
            Document update = toDocument();
            update.remove("uuid");
            mongo.updateDocument("fsync_profiles", filter, update);
        });
    }

    public void saveRedis() {
        RedisManager redis = fSync.getRedisManager();
        redis.setAndWaitAsync(getRedisKey(), GSON.toJson(this), Config.replicas, 8000, 3, 1800);
    }

    public static CompletableFuture<PlayerProfile> load(UUID uuid, String name) {
        RedisManager redis = fSync.getRedisManager();
        MongoDBManager mongo = fSync.getMongoDBManager();

        return CompletableFuture.supplyAsync(() -> {
            String redisKey = "fsync:playerprofile:" + uuid;
            String json = redis.getAsync(redisKey, Config.region).join();

            PlayerProfile profile = null;
            if (json != null) {
                try {
                    Document doc = Document.parse(json);
                    profile = fromDocument(doc);
                } catch (Exception ignored) {}
            }

            if (profile == null) {
                Document doc = mongo.findDocument("fsync_profiles", new Document("uuid", uuid.toString())).join();
                if (doc != null) {
                    profile = fromDocument(doc);
                    Document rebuilt = profile.toDocument(); // includes external provider write
                    // Save updated document to Mongo
                    mongo.updateDocument("fsync_profiles", new Document("uuid", uuid.toString()), rebuilt);
                    redis.setAndWaitAsync(redisKey, rebuilt.toJson(), Config.replicas, 8000, 3, 1800);
                } else {
                    profile = new PlayerProfile(uuid, name);
                    profile.save(Config.server);
                }
            }

            double heath = profile.health;
            double food = profile.food;
            String inventoryData = profile.inventoryData;
            String enderchestData = profile.enderchestData;
            String armorData = profile.armorData;
            List<String> potionEffects = profile.potionEffects;
            String offhand = profile.offhandData;
            int selectedSlot = profile.selectedSlot;
            float saturation = profile.saturation;
            int level = profile.level;
            float exp = profile.exp;
            String gamemode = profile.gamemode;
            Bukkit.getScheduler().runTask(fSync.getPlugin(), () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(new ItemStack[4]);
                    player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    player.getEnderChest().clear();

                    player.setHealth(heath);
                    player.setFoodLevel((int) food);
                    player.getInventory().setContents(deserializeInventory(inventoryData, 36).getContents());
                    player.getInventory().setArmorContents(deserializeItemArray(armorData));
                    player.getEnderChest().setContents(deserializeInventory(enderchestData, 27).getContents());
                    player.getInventory().setItemInOffHand(deserializeItem(offhand));
                    player.getInventory().setHeldItemSlot(selectedSlot);
                    player.setSaturation(saturation);
                    player.setLevel(level);
                    player.setExp(exp);
                    player.setGameMode(GameMode.valueOf(gamemode));
                    player.updateInventory();

                    player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
                    for (PotionEffect effect : deserializePotions(potionEffects)) {
                        player.addPotionEffect(effect);
                    }
                }
            });

            return profile;
        });
    }

    private static String serializeInventory(Inventory inv) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeInt(inv.getSize());
            for (ItemStack item : inv.getContents()) {
                out.writeObject(item);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Inventory deserializeInventory(String data, int expectedSize) {
        if (data == null) {
            return Bukkit.createInventory(null, expectedSize);
        }

        try (
                ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                BukkitObjectInputStream in = new BukkitObjectInputStream(bis)
        ) {
            in.readInt(); // ignored, trust expectedSize
            Inventory inv = Bukkit.createInventory(null, expectedSize);

            for (int i = 0; i < expectedSize; i++) {
                ItemStack item = (ItemStack) in.readObject();
                inv.setItem(i, item == null ? new ItemStack(Material.AIR) : item);
            }

            return inv;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return Bukkit.createInventory(null, expectedSize);
        }
    }

    private static String serializeItemArray(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                out.writeObject(item);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static ItemStack[] deserializeItemArray(String data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(data)); BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            int len = in.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            return items;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
    }

    private static List<String> serializePotions(Collection<PotionEffect> effects) {
        List<String> data = new ArrayList<>();
        for (PotionEffect effect : effects) {
            data.add(effect.getType().getName() + ":" + effect.getDuration() + ":" + effect.getAmplifier());
        }
        return data;
    }

    private static List<PotionEffect> deserializePotions(List<String> data) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String s : data) {
            String[] split = s.split(":");
            PotionEffectType type = PotionEffectType.getByName(split[0]);
            int duration = Integer.parseInt(split[1]);
            int amp = Integer.parseInt(split[2]);
            effects.add(new PotionEffect(type, duration, amp));
        }
        return effects;
    }

    private static String serializeItem(ItemStack item) {
        if (item != null) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
                out.writeObject(item);
                out.flush();
                return Base64.getEncoder().encodeToString(bos.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    private static ItemStack deserializeItem(String data) {
        if (data != null) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                 BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
                return (ItemStack) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                return new ItemStack(Material.AIR);
            }
        }
        return null;
    }
}