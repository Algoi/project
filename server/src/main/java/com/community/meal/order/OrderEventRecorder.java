package com.community.meal.order;
import com.community.meal.common.Db;import com.community.meal.contracts.BusinessEvent;import java.util.*;
import org.springframework.context.event.EventListener;import org.springframework.stereotype.Component;
@Component
public class OrderEventRecorder {
 private final Db db;public OrderEventRecorder(Db db){this.db=db;}
 private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("ORDER_CREATE","订单已创建"),Map.entry("MOCK_PAYMENT_SUCCESS","支付成功"),Map.entry("WALLET_PAYMENT_SUCCESS","钱包支付成功"),Map.entry("ORDER_PREPARING","开始备餐"),Map.entry("ORDER_READY","已出餐，等待取餐或配送"),Map.entry("REDEMPTION","取餐完成"),Map.entry("DELIVERY_STATUS","配送状态更新"),Map.entry("ORDER_CANCEL","订单已取消"),Map.entry("REFUND_REQUEST","已申请退款"),Map.entry("REFUND_APPROVE","退款审核通过"),Map.entry("REFUND_REJECT","退款审核未通过"),Map.entry("REFUND_RETRY","退款重试"));
 @EventListener public void on(BusinessEvent e){if(!LABELS.containsKey(e.action()))return;long id=e.resource().equals("REFUND")?db.count("SELECT order_id FROM refund_record WHERE id=?",e.resourceId()):Long.parseLong(e.resourceId());db.exec("INSERT IGNORE INTO order_event(order_id,source_key,event_type,label) VALUES(?,?,?,?)",id,"audit:"+e.id(),e.action(),LABELS.get(e.action()));}
}
