package com.community.meal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

class ApiRouteCompatibilityTest extends BusinessTestSupport {
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void refactoredControllersPreserveAllDocumentedHttpOperations() {
    var resource = getClass().getResourceAsStream("/static/api/v1/openapi.yaml");
    Map<String, Object> spec = new Yaml().load(resource);
    Map<String, Map<String, Object>> paths = (Map) spec.get("paths");
    Set<String> expected = new TreeSet<>();
    paths.forEach(
        (path, operations) ->
            operations.keySet().stream()
                .filter(v -> Set.of("get", "post", "put", "patch", "delete").contains(v))
                .forEach(method -> expected.add(method.toUpperCase() + " " + path)));
    Set<String> actual = new TreeSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (info, handler) -> {
              for (String path : info.getPatternValues())
                if (path.startsWith("/api/v1/"))
                  for (String expanded : expand(path.substring(7)))
                    for (var method : info.getMethodsCondition().getMethods())
                      actual.add(method.name() + " " + expanded);
            });
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
  }

  private List<String> expand(String path) {
    Matcher matcher = Pattern.compile("\\{\\w+:([^}]+)\\}").matcher(path);
    if (!matcher.find()) return List.of(path);
    List<String> values = new ArrayList<>();
    for (String option : matcher.group(1).split("\\|"))
      values.addAll(
          expand(path.substring(0, matcher.start()) + option + path.substring(matcher.end())));
    return values;
  }
}
