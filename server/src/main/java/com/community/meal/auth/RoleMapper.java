package com.community.meal.auth;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper {
  @Select("SELECT id,code,name FROM sys_role ORDER BY id")
  List<RoleController.RoleView> list();
}
