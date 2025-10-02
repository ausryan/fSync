package com.fadehq.fsync.api;

import org.bson.Document;

import java.util.UUID;

public interface ExternalDataProvider {
    void writeToDocument(UUID uuid, Document target);
    void readFromDocument(UUID uuid, Document source);
    default void remove(UUID uuid) {}
    default void onJoin(UUID uuid) {}
}