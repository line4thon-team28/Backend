package com.pawpose.global.storage;
public interface PhotoStorage {
 StoredPhoto put(String key, byte[] content, String contentType);
 void delete(String key);
}
