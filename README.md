# fSync

A server synchronization system designed to sync player and world data efficiently. Utilizes MongoDB for persistent storage and Redis with multi-region support to provide low-latency performance across servers in different regions.

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

---

## 🧩 Register Your Provider

Call this during plugin startup **before any players join**:

```java
PlayerProfile.registerExternalProvider(new ShardsDataProvider());
```

---

## 📌 Summary

- Works with both Redis and MongoDB
- Supports hot plugin reloads and restarts
- Keeps your logic modular and independent
