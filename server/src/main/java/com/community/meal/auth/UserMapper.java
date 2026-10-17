package com.community.meal.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper extends BaseMapper<AppUser> {
  @Select(
      "SELECT permission_code FROM sys_user_permission WHERE user_id=#{userId} ORDER BY"
          + " permission_code")
  List<String> deniedPermissions(long userId);

  @Select("SELECT * FROM sys_user WHERE username=#{username} FOR UPDATE")
  AppUser lockByUsername(String username);

  @Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
  AppUser lockById(long id);

  @Select(
      "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE"
          + " ur.user_id=#{userId} ORDER BY r.id")
  List<String> roles(long userId);

  @Update("UPDATE sys_user SET real_name=#{realName},phone=COALESCE(#{phone},phone) WHERE id=#{id}")
  int updateProfile(
      @Param("id") long id, @Param("realName") String realName, @Param("phone") String phone);

  @Update("UPDATE sys_user SET password_hash=#{hash} WHERE id=#{id}")
  int updatePassword(@Param("id") long id, @Param("hash") String hash);
}
