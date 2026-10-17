package com.community.meal.common;

import java.math.*;
import java.time.*;
import java.util.*;

public final class Values {
  private Values() {}

  public static Map<String, Object> map(Object... pairs) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]);
    return result;
  }

  public static String text(Map<String, Object> input, String key, int max, boolean required) {
    Object v = input.get(key);
    String s = v == null ? null : v.toString().strip();
    if ((required && (s == null || s.isBlank())) || (s != null && s.length() > max))
      throw ApiException.invalid(key + " 参数无效");
    return s;
  }

  public static String str(Object value) {
    return value == null ? "" : value.toString();
  }

  public static long id(Object value) {
    try {
      long n = Long.parseLong(str(value));
      if (n < 1) throw new Exception();
      return n;
    } catch (Exception e) {
      throw ApiException.invalid("标识无效");
    }
  }

  public static long num(Object value) {
    return value == null ? 0 : ((Number) value).longValue();
  }

  public static boolean yes(Object value) {
    return Boolean.TRUE.equals(value)
        || "1".equals(str(value))
        || "true".equalsIgnoreCase(str(value));
  }

  public static List<?> array(Object value) {
    if (value == null) return List.of();
    if (value instanceof List<?> l) return l;
    throw ApiException.invalid("需要数组参数");
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
    throw ApiException.invalid("需要对象参数");
  }

  public static List<Long> ids(Object value) {
    return array(value).stream().map(Values::id).distinct().sorted().toList();
  }

  public static List<String> strings(Object value) {
    return array(value).stream()
        .map(
            v -> {
              String s = str(v).strip();
              if (s.isBlank() || s.length() > 150) throw ApiException.invalid("文本项无效");
              return s;
            })
        .distinct()
        .toList();
  }

  public static BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(str(value));
    } catch (Exception e) {
      throw ApiException.invalid("数值无效");
    }
  }

  public static BigDecimal amount(Object value) {
    BigDecimal n = decimal(value);
    if (n.signum() < 0 || n.scale() > 2 || n.compareTo(new BigDecimal("99999999.99")) > 0)
      throw ApiException.invalid("金额或营养值无效");
    return n.setScale(2);
  }

  public static LocalDate date(Object value) {
    try {
      return LocalDate.parse(str(value));
    } catch (Exception e) {
      throw ApiException.invalid("日期无效");
    }
  }

  public static String choice(Object value, String... options) {
    String s = str(value);
    if (!List.of(options).contains(s)) throw ApiException.invalid("选项无效");
    return s;
  }

  public static String meal(Object value) {
    return choice(value, "LUNCH", "DINNER");
  }

  public static int integer(Object value, int min, int max) {
    try {
      int n = Integer.parseInt(str(value));
      if (n < min || n > max) throw new Exception();
      return n;
    } catch (Exception e) {
      throw ApiException.invalid("数量无效");
    }
  }

  public static void fields(Map<String, Object> input, String... keys) {
    if (!Set.of(keys).containsAll(input.keySet())) throw ApiException.invalid("请求包含不允许的字段");
  }

  public static void rule(boolean condition, String message) {
    if (!condition)
      throw new ApiException(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "BUSINESS_RULE_VIOLATION",
          message);
  }

  public static String placeholders(int count) {
    return String.join(",", Collections.nCopies(count, "?"));
  }
}
