package com.community.meal.site;

import com.community.meal.contracts.SiteSummary;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SiteMapper extends com.community.meal.contracts.SiteDirectory {
  @Select(
      "SELECT id,name,address,phone,business_status FROM service_site ORDER BY id LIMIT"
          + " #{offset},#{size}")
  List<SiteSummary> listAll(@Param("offset") long offset, @Param("size") int size);

  @Select("SELECT COUNT(*) FROM service_site")
  long countAll();

  @Select(
      "SELECT s.id,s.name,s.address,s.phone,s.business_status FROM service_site s JOIN"
          + " sys_user_site us ON us.site_id=s.id WHERE us.user_id=#{userId} ORDER BY s.id LIMIT"
          + " #{offset},#{size}")
  List<SiteSummary> listAssigned(
      @Param("userId") long userId, @Param("offset") long offset, @Param("size") int size);

  @Select("SELECT COUNT(*) FROM sys_user_site WHERE user_id=#{userId}")
  long countAssigned(long userId);

  @Select("SELECT id,name,address,phone,business_status FROM service_site WHERE id=#{id}")
  SiteSummary find(long id);

  @Select("SELECT COUNT(*) FROM sys_user_site WHERE user_id=#{userId} AND site_id=#{siteId}")
  long hasAssignment(@Param("userId") long userId, @Param("siteId") long siteId);

  @Select("SELECT s.id,s.name,s.address,s.phone,s.business_status FROM service_site s WHERE (#{all} OR s.id IN(SELECT site_id FROM sys_user_site WHERE user_id=#{user})) AND (s.name LIKE CONCAT('%',#{keyword},'%') OR COALESCE(s.address,'') LIKE CONCAT('%',#{keyword},'%')) ORDER BY s.id LIMIT #{offset},#{size}")
  List<SiteSummary> search(@Param("user") long user,@Param("all") boolean all,@Param("keyword") String keyword,@Param("offset") long offset,@Param("size") int size);
  @Select("SELECT COUNT(*) FROM service_site s WHERE (#{all} OR s.id IN(SELECT site_id FROM sys_user_site WHERE user_id=#{user})) AND (s.name LIKE CONCAT('%',#{keyword},'%') OR COALESCE(s.address,'') LIKE CONCAT('%',#{keyword},'%'))")
  long searchCount(@Param("user") long user,@Param("all") boolean all,@Param("keyword") String keyword);
}
