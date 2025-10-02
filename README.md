# fSync

A server synchronization system designed to sync player and world data efficiently. Utilizes MongoDB for persistent storage and Redis with multi-region support to provide low-latency performance across servers in different regions.

Made for play.fadehq.com

# fSync ExternalDataProvider API

This guide explains how to extend the `fSync` system with your own data fields using the `ExternalDataProvider` interface.

## ✅ What Is ExternalDataProvider?

It lets you attach custom data (e.g. shards, stats) to `PlayerProfile` without modifying the core class.

- ✅ Stored in MongoDB on save
- ✅ Loaded on login or cross-server sync
- ✅ Cached in Redis for fast regional access

---

## 🛠️ Example Implementation

```java
public class ShardsDataProvider implements ExternalDataProvider {

    private final Map<UUID, Long> shardCache = new ConcurrentHashMap<>();

    public void addShards(UUID uuid, long amount) {
        shardCache.merge(uuid, amount, Long::sum);
    }

    public void setShards(UUID uuid, long shards) {
        shardCache.put(uuid, shards);
    }

    public long getShards(UUID uuid) {
        return shardCache.getOrDefault(uuid, 0L);
    }

    @Override
    public void writeToDocument(UUID uuid, Document target) {
        Long shards = shardCache.get(uuid);
        if (shards != null) {
            target.append("shards", shards);
        }
    }

    @Override
    public void readFromDocument(UUID uuid, Document source) {
        Object value = source.get("shards");
        if (value instanceof Number) {
            shardCache.put(uuid, ((Number) value).longValue());
        }
    }

    @Override
    public void remove(UUID uuid) {
        shardCache.remove(uuid);
    }
}
```

```java
public class HomesDataProvider implements ExternalDataProvider {

    private final Map<UUID, Map<String, Home>> homeCache = new HashMap<>();

    public void setHome(UUID uuid, String name, Home home) {
        homeCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(name.toLowerCase(), home);
    }

    public void deleteHome(UUID uuid, String name) {
        Map<String, Home> homes = homeCache.get(uuid);
        if (homes != null) {
            homes.remove(name.toLowerCase());
            if (homes.isEmpty()) {
                homeCache.remove(uuid);
            }
        }
    }

    public Home getHome(UUID uuid, String name) {
        return homeCache.getOrDefault(uuid, Collections.emptyMap()).get(name.toLowerCase());
    }

    public Map<String, Home> getHomes(UUID uuid) {
        return Collections.unmodifiableMap(homeCache.getOrDefault(uuid, Collections.emptyMap()));
    }

    @Override
    public void writeToDocument(UUID uuid, Document target) {
        Map<String, Home> homes = homeCache.get(uuid);
        if (homes == null) return;

        Document homesDoc = new Document();
        for (Map.Entry<String, Home> entry : homes.entrySet()) {
            Home h = entry.getValue();
            homesDoc.append(entry.getKey(), new Document()
                    .append("world", h.world())
                    .append("x", h.x())
                    .append("y", h.y())
                    .append("z", h.z())
                    .append("yaw", h.yaw())
                    .append("pitch", h.pitch())
                    .append("server", h.server()));
        }
        target.append("homes", homesDoc);
    }

    @Override
    public void readFromDocument(UUID uuid, Document source) {
        Map<String, Home> homes = new HashMap<>();
        Document homesDoc = (Document) source.get("homes");
        if (homesDoc != null) {
            for (String key : homesDoc.keySet()) {
                Document h = (Document) homesDoc.get(key);
                homes.put(key, new Home(
                        h.getString("world"),
                        h.getDouble("x"),
                        h.getDouble("y"),
                        h.getDouble("z"),
                        h.getDouble("yaw").floatValue(),
                        h.getDouble("pitch").floatValue(),
                        h.getString("server")
                ));
            }
        }
        homeCache.put(uuid, homes);
    }

    @Override
    public void remove(UUID uuid) {
        homeCache.remove(uuid);
    }

    public record Home(String world, double x, double y, double z, float yaw, float pitch, String server) {}
}
```

---

## 🧩 Register Your Provider

Call this during plugin startup **before any players join**:

```java
PlayerProfile.registerExternalProvider(new ShardsDataProvider());
```

---

## 📌 Summary

- Works with both Redis and MongoDB
- Keeps your logic modular and independent
