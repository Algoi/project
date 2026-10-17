package com.community.meal.contracts;

/** Opaque keys only. Implementations must never expose files through a public URL. */
public interface PrivateFileStorage {
  String store(String extension, byte[] bytes);

  byte[] read(String key);

  void delete(String key);
}
