package com.community.meal.audit;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditMapper extends com.community.meal.contracts.AuditEvents {
  @Insert(
      "INSERT INTO audit_log(operator_user_id,action,resource_type,resource_id,success,summary)"
          + " VALUES(#{userId},#{action},'USER',#{resourceId},#{success},#{summary})")
  void record(
      @Param("userId") Long userId,
      @Param("action") String action,
      @Param("resourceId") String resourceId,
      @Param("success") boolean success,
      @Param("summary") String summary);
}
