package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import com.community.meal.common.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateStorageTest {
  @TempDir Path root;

  @Test
  void roundTripUsesOpaqueUniqueKeys() {
    var storage = new LocalPrivateFileStorage(root.toString());
    String one = storage.store("png", new byte[] {1, 2, 3}),
        two = storage.store("png", new byte[] {4});
    assertThat(one).isNotEqualTo(two).doesNotContain("/");
    assertThat(storage.read(one)).containsExactly(1, 2, 3);
    storage.delete(one);
    assertThatThrownBy(() -> storage.read(one)).isInstanceOf(ApiException.class);
    assertThat(storage.read(two)).containsExactly(4);
  }

  @Test
  void keysAndExtensionsCannotEscapeStorage() {
    var storage = new LocalPrivateFileStorage(root.toString());
    assertThatThrownBy(() -> storage.store("../outside", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    for (String key : new String[] {"../outside", "/etc/passwd", "nested/file.png"})
      assertThatThrownBy(() -> storage.read(key)).isInstanceOf(ApiException.class);
  }

  @Test
  void symbolicLinksAreNotFollowed() throws Exception {
    var storage = new LocalPrivateFileStorage(root.toString());
    String key = storage.store("pdf", new byte[] {1});
    Path outside = Files.createTempFile("meal-storage-test-", ".pdf");
    try {
      Files.delete(root.resolve(key));
      Files.createSymbolicLink(root.resolve(key), outside);
      assertThatThrownBy(() -> storage.read(key)).isInstanceOf(ApiException.class);
    } finally {
      Files.deleteIfExists(outside);
    }
  }
}
