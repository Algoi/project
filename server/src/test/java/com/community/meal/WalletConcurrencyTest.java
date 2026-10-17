package com.community.meal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.*;

class WalletConcurrencyTest extends WalletTestSupport {
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 void simultaneousPurchasesCannotSpendTheSameBalanceTwice() throws Exception {
  var pool=Executors.newFixedThreadPool(2);
  try{
   for(int round=0;round<3;round++){
   long recharge=call("POST","/wallet/topups",Map.of("amount","10.00","requestId","race-recharge-"+round),"family",200).path("id").asLong();
   call("POST","/dev/wallet/topups/"+recharge+"/confirm",Map.of(),"family",200);
   long first=order(),second=order();var start=new CountDownLatch(1);List<Future<Integer>> results=new ArrayList<>();
   for(long id:List.of(first,second))results.add(pool.submit(()->{start.await(5,TimeUnit.SECONDS);return mvc.perform(post("/api/v1/orders/"+id+"/payments").header("Authorization","Bearer "+tokens.get("family")).contentType("application/json").content("{\"channel\":\"WALLET\"}")).andReturn().getResponse().getStatus();}));
   start.countDown();assertThat(List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,422);
   assertThat(balance()).isEqualTo(String.format(java.util.Locale.ROOT,"%.2f",(round+1)*1.50));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE status='SUCCESS'",Integer.class)).isEqualTo(round+1);
   }
  }finally{pool.shutdownNow();BusinessTestCleanup.clear(jdbc);}
 }
}
