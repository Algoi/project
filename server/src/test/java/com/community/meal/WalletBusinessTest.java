package com.community.meal;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WalletBusinessTest extends WalletTestSupport {
  @Test void aLinkedFamilyMayPayButRefundGoesOnlyToTheActualPayersWallet() throws Exception {
    long other=call("POST","/users",Map.of("username","walletOther","password",PASSWORD,"realName","另一位家属","roles",List.of("FAMILY"),"siteIds",List.of()),"admin",200).path("id").asLong();
    tokens.put("other",call("POST","/auth/login",Map.of("username","walletOther","password",PASSWORD),null,200).path("token").asText());
    long first=call("POST","/wallet/topups",Map.of("amount","100.00","requestId","owner-one"),"family",200).path("id").asLong();
    call("POST","/dev/wallet/topups/"+first+"/confirm",Map.of(),"other",403);
    call("POST","/dev/wallet/topups/"+first+"/confirm",Map.of(),"family",200);
    long second=call("POST","/wallet/topups",Map.of("amount","50.00","requestId","owner-two"),"other",200).path("id").asLong();
    call("POST","/dev/wallet/topups/"+second+"/confirm",Map.of(),"other",200);
    long order=order();long elder=jdbc.queryForObject("SELECT elder_id FROM meal_order WHERE id=?",Long.class,order);
    jdbc.update("INSERT INTO family_binding(family_user_id,elder_id,status)VALUES(?,?,'APPROVED')",other,elder);
    call("POST","/orders/"+order+"/payments",Map.of("channel","WALLET"),"other",403);
    // A different family cannot pay a family-created private order merely by binding the elder.
    jdbc.update("UPDATE meal_order SET proxy_operator_id=14,buyer_user_id=14 WHERE id=?",order);
    call("POST","/orders/"+order+"/payments",Map.of("channel","WALLET"),"other",200);
    assertThat(call("GET","/wallet",null,"other",200).path("balance").asText()).isEqualTo("41.50");
    long refund=call("POST","/orders/"+order+"/cancel",Map.of("reason","代付退款"),"family",200).path("refundId").asLong();
    call("POST","/refunds/"+refund+"/approve",Map.of("note","退还实际付款家属"),"community",200);
    assertThat(balance()).isEqualTo("100.00");
    assertThat(call("GET","/wallet",null,"other",200).path("balance").asText()).isEqualTo("50.00");
    assertThat(call("GET","/billing",null,"family",200).path("total").asInt()).isZero();
    assertThat(call("GET","/billing",null,"other",200).path("total").asInt()).isEqualTo(2);
  }
  @Test void topupPaymentAndRefundAreIdempotentAndStatementsUseActualCashFlows() throws Exception {
    assertThat(call("GET","/wallet",null,"family",200).path("balance").asText()).isEqualTo("0.00");
    call("GET","/wallet",null,"community",403);
    var input=Map.of("amount","100.00","requestId","wallet-test-request");
    long topup=call("POST","/wallet/topups",input,"family",200).path("id").asLong();
    assertThat(call("POST","/wallet/topups",input,"family",200).path("id").asLong()).isEqualTo(topup);
    call("POST","/wallet/topups",Map.of("amount","101.00","requestId","wallet-test-request"),"family",409);
    call("POST","/dev/wallet/topups/"+topup+"/confirm",Map.of(),"community",403);
    call("POST","/dev/wallet/topups/"+topup+"/confirm",Map.of(),"family",200);
    call("POST","/dev/wallet/topups/"+topup+"/confirm",Map.of(),"family",200);
    assertThat(balance()).isEqualTo("100.00");
    long order=order();
    var paid=call("POST","/orders/"+order+"/payments",Map.of("channel","WALLET","amount","0.01"),"family",200);
    assertThat(paid.path("status").asText()).isEqualTo("SUCCESS");
    call("POST","/orders/"+order+"/payments",Map.of("channel","WALLET"),"family",200);
    assertThat(balance()).isEqualTo("91.50");
    assertThat(call("GET","/orders/"+order,null,"family",200).path("status").asText()).isEqualTo("PAID");
    long refund=call("POST","/orders/"+order+"/cancel",Map.of("reason","钱包原路退款"),"family",200).path("refundId").asLong();
    call("POST","/refunds/"+refund+"/approve",Map.of("note","核实后退款"),"community",200);
    call("POST","/refunds/"+refund+"/approve",Map.of("note","重复提交"),"community",200);
    assertThat(balance()).isEqualTo("100.00");
    assertThat(call("GET","/wallet/transactions",null,"family",200).path("total").asInt()).isEqualTo(3);
    var statement=call("GET","/billing",null,"family",200);
    assertThat(statement.path("summary").path("paidAmount").asText()).isEqualTo("8.50");
    assertThat(statement.path("summary").path("refundAmount").asText()).isEqualTo("8.50");
    assertThat(statement.path("summary").path("netExpense").asText()).isEqualTo("0.00");
    assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM subsidy_ledger WHERE order_id=?",java.math.BigDecimal.class,order)).isEqualByComparingTo("0");
  }

  @Test void invalidTopupsAndInsufficientBalanceNeverCreateSuccessfulPayments() throws Exception {
    for(String amount:List.of("0","-1","0.001","10000001"))
      call("POST","/wallet/topups",Map.of("amount",amount,"requestId",UUID.randomUUID().toString()),"family",400);
    long order=order();
    call("POST","/orders/"+order+"/payments",Map.of("channel","WALLET"),"family",422);
    assertThat(balance()).isEqualTo("0.00");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE order_id=? AND status='SUCCESS'",Integer.class,order)).isZero();
    assertThat(call("GET","/orders/"+order,null,"family",200).path("status").asText()).isEqualTo("PENDING_PAYMENT");
  }

}

abstract class WalletTestSupport extends BusinessTestSupport {
  protected String balance() throws Exception {return call("GET","/wallet",null,"family",200).path("balance").asText();}
  private Long menuItem;private String date;
  protected long order() throws Exception {
    long elder=call("POST","/elders",Map.of("name","钱包测试老人","siteId",1),"community",200).path("id").asLong();
    jdbc.update("INSERT INTO family_binding(family_user_id,elder_id,status)VALUES(13,?,'APPROVED')",elder);
    jdbc.update("INSERT INTO subsidy_entitlement(elder_id,is_active)VALUES(?,1)",elder);
    call("PUT","/elders/"+elder+"/health",Map.of("healthDataComplete",true,"allergenDataComplete",true),"family",200);
    if(menuItem==null){
      date=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
      long dish=call("POST","/dishes",Map.of("name","钱包测试餐品","originalPrice","12.50","elderPrice","8.50","ingredients",List.of("米饭"),"allergenDataComplete",true),"admin",200).path("id").asLong();
      long menu=call("POST","/sites/1/menus",Map.of("serviceDate",date,"mealType","LUNCH","items",List.of(Map.of("dishId",dish,"plannedQty",100))),"canteen",200).path("id").asLong();
      call("POST","/sites/1/menus/"+menu+"/submit",Map.of(),"canteen",200);
      menuItem=call("POST","/sites/1/menus/"+menu+"/publish",Map.of(),"community",200).path("items").get(0).path("id").asLong();
    }
    return call("POST","/orders",Map.of("elderId",elder,"siteId",1,"mealDate",date,"mealType","LUNCH","deliveryType","DINE_IN","items",List.of(Map.of("menuItemId",menuItem,"quantity",1))),"family",200).path("id").asLong();
  }
}
