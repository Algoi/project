package com.community.meal.common;

import com.community.meal.contracts.PrivateFileStorage;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalPrivateFileStorage implements PrivateFileStorage {
  private final Path root;

  public LocalPrivateFileStorage(@Value("${app.upload-path}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public String store(String extension, byte[] bytes) {
    if (!Set.of("png", "jpg", "webp", "pdf").contains(extension))
      throw new IllegalArgumentException("Unsupported extension");
    String key = UUID.randomUUID() + "." + extension;
    try {
      Files.createDirectories(root);
      Files.write(path(key), bytes, StandardOpenOption.CREATE_NEW);
      return key;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("附件存储失败", e);
    }
  }

  public byte[] read(String key) {
    try {
      return Files.readAllBytes(path(key));
    } catch (java.io.IOException e) {
      throw ApiException.notFound();
    }
  }

  public void delete(String key) {
    try {
      Files.deleteIfExists(path(key));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("附件删除失败", e);
    }
  }

  private Path path(String key) {
    if (!key.matches("[0-9a-fA-F-]{36}\\.(png|jpg|webp|pdf)")) throw ApiException.forbidden();
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root) || Files.isSymbolicLink(path)) throw ApiException.forbidden();
    return path;
  }
}
