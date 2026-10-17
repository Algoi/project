package com.community.meal.dev;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "app.seed.business", havingValue = "true")
public class BusinessDemoSeed implements CommandLineRunner {
  private final Db db;

  public BusinessDemoSeed(Db db) {
    this.db = db;
  }

  @Override
  @Transactional
  public void run(String... ignored) {
    if (db.count("SELECT COUNT(*) FROM app_seed_marker WHERE code='business_demo_v1'") > 0) return;
    long admin = num(db.one("SELECT id FROM sys_user WHERE username='admin'").get("id")),
        family = num(db.one("SELECT id FROM sys_user WHERE username='family'").get("id")),
        community = num(db.one("SELECT id FROM sys_user WHERE username='community'").get("id")),
        canteen = num(db.one("SELECT id FROM sys_user WHERE username='canteen'").get("id"));
    var sites =
        db.list("SELECT id FROM service_site WHERE name IN('幸福社区助餐点','康乐社区助餐点') ORDER BY id");
    rule(sites.size() == 2, "请先初始化两个演示网点");
    long primary = num(sites.get(0).get("id")), secondary = num(sites.get(1).get("id"));
    long vegetable = db.insert("INSERT INTO dish_category(name)VALUES(?)", "演示·蔬菜与主食"),
        protein = db.insert("INSERT INTO dish_category(name)VALUES(?)", "演示·蛋白质菜品"),
        peanut = db.insert("INSERT INTO allergen(name)VALUES(?)", "演示·花生");
    long[] dishes = new long[4];
    String[] names = {"清蒸鸡肉（演示）", "时蔬豆腐（演示）", "杂粮饭（演示）", "花生鸡丁（演示）"};
    String[][] ingredients = {{"鸡肉", "姜"}, {"豆腐", "青菜"}, {"大米", "小米"}, {"花生", "鸡肉"}};
    String[] prices = {"12.00", "8.00", "3.00", "13.00"},
        elderPrices = {"8.00", "5.00", "2.00", "9.00"};
    for (int i = 0; i < 4; i++) {
      dishes[i] =
          db.insert(
              "INSERT INTO"
                  + " dish(name,category_id,description,ingredients_json,weight_grams,original_price,elder_price,calories_kcal,protein_grams,fat_grams,carb_grams,sodium_mg,allergen_data_complete)VALUES(?,?,?,?,200,?,?,300,18,8,35,450,1)",
              names[i],
              i == 0 || i == 3 ? protein : vegetable,
              "本地演示数据",
              db.json(List.of(ingredients[i])),
              new BigDecimal(prices[i]),
              new BigDecimal(elderPrices[i]));
      if (i == 3) db.exec("INSERT INTO dish_allergen VALUES(?,?)", dishes[i], peanut);
    }
    long[] elders = new long[3];
    for (int i = 0; i < 3; i++) {
      long site = i == 2 ? secondary : primary;
      elders[i] =
          db.insert(
              "INSERT INTO elder(site_id,name,birth_date,created_by)VALUES(?,?,?,?)",
              site,
              new String[] {"李阿姨（演示）", "王大爷（演示）", "赵阿姨（演示）"}[i],
              LocalDate.of(1948 + i, 5, 12),
              community);
      db.exec(
          "INSERT INTO"
              + " elder_health(elder_id,diseases_json,allergen_ids_json,avoid_ingredients_json,preferred_categories_json,disliked_categories_json,health_notes,allergen_data_complete,health_data_complete)VALUES(?,'[]',?,'[]',?,'[]','合成演示档案，不对应真实个人',1,1)",
          elders[i],
          i == 0 ? db.json(List.of(peanut)) : "[]",
          db.json(List.of(vegetable)));
      if (i < 2)
        db.exec(
            "INSERT INTO"
                + " family_binding(family_user_id,elder_id,status,reviewer_id,review_note,reviewed_at)VALUES(?,?,'APPROVED',?,'本地演示关系',UTC_TIMESTAMP())",
            family,
            elders[i],
            community);
      db.exec(
          "INSERT INTO"
              + " subsidy_entitlement(elder_id,is_active,activated_at)VALUES(?,1,UTC_TIMESTAMP())",
          elders[i]);
    }
    LocalDate today = LocalDate.now(com.community.meal.common.BusinessTime.ZONE);
    for (int offset = -28; offset <= 7; offset++) {
      LocalDate day = today.plusDays(offset);
      if (offset == 0) continue;
      for (long site : List.of(primary, secondary)) {
        var existing =
            db.optional(
                "SELECT id FROM daily_menu WHERE site_id=? AND service_date=? AND"
                    + " meal_type='LUNCH'",
                site,
                day);
        if (existing != null) continue;
        long menu =
            db.insert(
                "INSERT INTO"
                    + " daily_menu(site_id,service_date,meal_type,status,submitted_by,published_by,submitted_at,published_at)VALUES(?,?,'LUNCH','PUBLISHED',?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",
                site,
                day,
                canteen,
                community);
        Map<Long, Long> itemIds = new HashMap<>();
        for (long dish : dishes)
          itemIds.put(
              dish,
              db.insert(
                  "INSERT INTO daily_menu_item(menu_id,dish_id,planned_qty)VALUES(?,?,50)",
                  menu,
                  dish));
        if (offset > 0) continue;
        int attendance = 0;
        for (int person = 0; person < elders.length; person++) {
          if ((person == 2 ? secondary : primary) != site) continue;
          if (person == 1 && offset % 2 == 0) continue;
          attendance++;
          String orderNo = "DEMO_HISTORY_" + day + "_" + elders[person];
          long order =
              db.insert(
                  "INSERT INTO"
                      + " meal_order(order_no,elder_id,buyer_user_id,proxy_operator_id,site_id,meal_date,meal_type,delivery_type,status,original_amount,subsidy_amount,payable_amount)VALUES(?,?,?,?,?,?,'LUNCH','DINE_IN','COMPLETED',15,5,10)",
                  orderNo,
                  elders[person],
                  family,
                  community,
                  site,
                  day);
          for (int selected : new int[] {0, 2})
            db.exec(
                "INSERT INTO"
                    + " meal_order_item(order_id,menu_item_id,dish_id,dish_name_snapshot,qty,original_unit_price,actual_unit_price,unit_subsidy,nutrition_snapshot_json)VALUES(?,?,?,?,1,?,?,?,?)",
                order,
                itemIds.get(dishes[selected]),
                dishes[selected],
                names[selected],
                new BigDecimal(prices[selected]),
                new BigDecimal(elderPrices[selected]),
                new BigDecimal(prices[selected]).subtract(new BigDecimal(elderPrices[selected])),
                db.json(
                    map(
                        "caloriesKcal",
                        "300.00",
                        "proteinGrams",
                        "18.00",
                        "fatGrams",
                        "8.00",
                        "carbGrams",
                        "35.00",
                        "sodiumMg",
                        "450.00")));
          db.exec(
              "INSERT INTO"
                  + " payment_record(order_id,channel,payment_no,amount,status,gateway_transaction_no,paid_at,payer_user_id)VALUES(?,'MOCK_A',?,10,'SUCCESS',?,UTC_TIMESTAMP(),?)",
              order,
              "DEMO_PAY_" + order,
              "MOCK_DEMO_" + order,
              family);
          db.exec(
              "INSERT INTO subsidy_ledger(order_id,amount,entry_type)VALUES(?,5,'GRANT')", order);
          db.exec(
              "INSERT INTO"
                  + " redemption(order_id,verifier_user_id,verify_method)VALUES(?,?,'DEMO_HISTORY')",
              order,
              canteen);
        }
        db.exec(
            "INSERT INTO"
                + " attendance_daily(site_id,attendance_date,meal_type,diner_count,is_open)VALUES(?,?,'LUNCH',?,1)",
            site,
            day,
            attendance);
      }
    }
    db.exec(
        "INSERT INTO"
            + " notice(title,type,content,status,target_roles_json,target_sites_json,publish_at,created_by)VALUES('欢迎使用邻里膳（演示）','IMPORTANT','系统中的演示餐食、历史订单、健康档案和模拟支付均为本地测试数据。','PUBLISHED','[]','[]',UTC_TIMESTAMP(),?)",
        admin);
    db.exec("INSERT INTO app_seed_marker(code)VALUES('business_demo_v1')");
  }
}
