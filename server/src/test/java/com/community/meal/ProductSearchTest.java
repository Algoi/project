package com.community.meal;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProductSearchTest extends BusinessTestSupport {
  @Test void searchesActuallyFilterAndRetainResourceScope() throws Exception {
    assertThat(call("GET","/sites?keyword=不存在",null,"admin",200).path("total").asInt()).isZero();
    assertThat(call("GET","/sites?keyword=第二",null,"canteen",200).path("total").asInt()).isZero();
    assertThat(call("GET","/sites?keyword=第一",null,"canteen",200).path("total").asInt()).isEqualTo(1);
    call("POST","/tickets",Map.of("siteId",1,"type","FEEDBACK","content","特定查询内容"),"family",200);
    assertThat(call("GET","/tickets?keyword=不存在",null,"family",200).path("total").asInt()).isZero();
    assertThat(call("GET","/tickets?keyword=特定",null,"family",200).path("total").asInt()).isEqualTo(1);
    jdbc.update("INSERT INTO elder(id,site_id,name)VALUES(100,1,'特定老人')");
    jdbc.update("INSERT INTO subsidy_application(elder_id,applicant_user_id,proof_file_ids_json)VALUES(100,13,'[]')");
    assertThat(call("GET","/subsidy-applications?keyword=不存在",null,"community",200).path("total").asInt()).isZero();
    assertThat(call("GET","/subsidy-applications?keyword=特定",null,"community",200).path("total").asInt()).isEqualTo(1);
  }
}
