package com.community.meal.order;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MenuAvailabilityHandler implements OrderExceptions {
  private final Db db;

  public MenuAvailabilityHandler(Db db) {
    this.db = db;
  }

  public void menuItemUnavailable(long item) {
    db.exec(
        "UPDATE meal_order o JOIN meal_order_item i ON i.order_id=o.id SET"
            + " o.exception_flag=1,o.exception_note='所选餐品临时下架，请联系网点处理' WHERE i.menu_item_id=? AND"
            + " o.status IN('PAID','PREPARING','READY')",
        item);
  }
}
