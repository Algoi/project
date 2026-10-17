package com.community.meal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import com.community.meal.preparation.PreparationService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class PreparationDegradationTest {
 @Test void computationFailurePreservesConfirmedOrdersButDataFailureDoesNotBecomeAnEmptySuccess(){
  var db=mock(Db.class);var access=mock(Access.class);var menus=mock(MenuCatalog.class);var forecasts=mock(Forecasts.class);var demand=mock(OrderDemand.class);var actor=mock(Actor.class);var day=LocalDate.now();
  when(menus.get(actor,1,day,"LUNCH")).thenReturn(Map.of("items",List.of(Map.of("id",7,"dishId",8,"dishName","米饭"))));
  when(forecasts.calculate(1,"LUNCH",day,1)).thenThrow(new AlgorithmUnavailableException());
  when(demand.preparationHistory(1,day,"LUNCH")).thenReturn(Map.of("total",0,"diners",0,"validDays",0));when(demand.confirmed(7)).thenReturn(3L);
  var service=new PreparationService(db,access,menus,forecasts,demand);
  var result=service.preparation(actor,1,day,"LUNCH");
  var row=(Map<?,?>)((List<?>)result.get("items")).get(0);
  assertThat(row.get("confirmedQty")).isEqualTo(3L);assertThat(row.get("suggestedQty")).isEqualTo(3L);assertThat(row.get("forecastCount")).isNull();assertThat(result.get("warning").toString()).contains("预测");
  when(demand.confirmed(7)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("数据库不可用"));
  assertThatThrownBy(()->service.preparation(actor,1,day,"LUNCH")).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
}
