package com.community.meal.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Prepared SQL repository shared by business services; no client-provided SQL identifiers. */
@Repository
public class Db {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public Db(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  private org.springframework.context.ApplicationEventPublisher publisher;
  @org.springframework.beans.factory.annotation.Autowired
  public void setPublisher(org.springframework.context.ApplicationEventPublisher publisher) { this.publisher=publisher; }

  public int exec(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long count(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  public long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var s = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
          return s;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.query(
        sql,
        (r, index) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          var meta = r.getMetaData();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            Object value = r.getObject(i);
            if (label.endsWith("_json")) {
              label = label.substring(0, label.length() - 5);
              if (value != null)
                try {
                  value = json.readValue(value.toString(), Object.class);
                } catch (Exception e) {
                  throw new IllegalStateException("Invalid stored JSON", e);
                }
            }
            if (value instanceof Timestamp time) value = time.toInstant().toString();
            if (value instanceof java.time.LocalDateTime time)
              value = time.toInstant(java.time.ZoneOffset.UTC).toString();
            if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
            if (value instanceof Time time) value = time.toLocalTime().toString();
            StringBuilder camel = new StringBuilder();
            boolean upper = false;
            for (char c : label.toCharArray()) {
              if (c == '_') {
                upper = true;
                continue;
              }
              camel.append(upper ? Character.toUpperCase(c) : c);
              upper = false;
            }
            row.put(camel.toString(), value);
          }
          return row;
        },
        args);
  }

  public Map<String, Object> optional(String sql, Object... args) {
    var rows = list(sql, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var row = optional(sql, args);
    if (row == null) throw ApiException.notFound();
    return row;
  }

  public String json(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw ApiException.invalid("数据格式错误");
    }
  }

  public void audit(
      com.community.meal.contracts.Actor p, String action, String resource, Object id) {
    long eventId=insert(
        "INSERT INTO audit_log(operator_user_id,action,resource_type,resource_id,success,summary)"
            + " VALUES(?,?,?,?,1,?)",
        p.userId(),
        action,
        resource,
        String.valueOf(id),
        "操作完成：" + action);
    if(publisher!=null) publisher.publishEvent(new com.community.meal.contracts.BusinessEvent(eventId,p.userId(),action,resource,String.valueOf(id)));
  }

  public Map<String, Object> page(String sql, List<Object> args, int page, int size) {
    if (page < 1 || size < 1 || size > 100) throw ApiException.invalid("分页参数无效");
    long total = count("SELECT COUNT(*) FROM (" + sql + ") page_count", args.toArray());
    var all = new ArrayList<>(args);
    all.add((long) (page - 1) * size);
    all.add(size);
    return Values.map("records", list(sql + " LIMIT ?,?", all.toArray()), "total", total);
  }
}
