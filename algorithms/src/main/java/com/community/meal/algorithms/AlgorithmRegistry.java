package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

public final class AlgorithmRegistry<T extends VersionedAlgorithm> {
  private final Map<String, T> implementations;

  public AlgorithmRegistry(Collection<T> algorithms) {
    Map<String, T> entries = new LinkedHashMap<>();
    for (T algorithm : algorithms) {
      if (algorithm.id() == null
          || algorithm.id().isBlank()
          || entries.putIfAbsent(algorithm.id(), algorithm) != null)
        throw new IllegalArgumentException("算法标识重复或无效");
    }
    implementations = Map.copyOf(entries);
  }

  public T select(String id) {
    T implementation = implementations.get(id);
    if (implementation == null) throw new IllegalArgumentException("未注册的算法: " + id);
    return implementation;
  }

  public Set<String> ids() {
    return implementations.keySet();
  }
}
