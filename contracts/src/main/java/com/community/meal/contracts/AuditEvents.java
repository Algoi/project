package com.community.meal.contracts;

public interface AuditEvents {
  void record(Long userId, String action, String resourceId, boolean success, String summary);
}
