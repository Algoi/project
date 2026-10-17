package com.community.meal.auth;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SessionMapper {
  @Insert(
      "INSERT INTO auth_session(token_hash,user_id,expires_at)"
          + " VALUES(#{hash},#{userId},#{expiresAt})")
  void insert(
      @Param("hash") String hash,
      @Param("userId") long userId,
      @Param("expiresAt") LocalDateTime expiresAt);

  @Select(
      "SELECT u.* FROM sys_user u JOIN auth_session s ON u.id=s.user_id WHERE s.token_hash=#{hash}"
          + " AND s.expires_at>#{now} AND u.status='ACTIVE'")
  AppUser findActiveUser(@Param("hash") String hash, @Param("now") LocalDateTime now);

  @Delete("DELETE FROM auth_session WHERE token_hash=#{hash}")
  void delete(String hash);

  @Delete("DELETE FROM auth_session WHERE user_id=#{userId}")
  void deleteForUser(long userId);

  @Delete("DELETE FROM auth_session WHERE expires_at<=#{now}")
  void deleteExpired(LocalDateTime now);
}
