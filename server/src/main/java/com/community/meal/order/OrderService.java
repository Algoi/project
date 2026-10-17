package com.community.meal.order;

import static com.community.meal.common.Values.*;

import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {
  private final OrderQueryService queries;
  private final OrderingService ordering;
  private final PaymentService payments;
  private final RefundService refunds;
  private final FulfillmentService fulfillment;

  public OrderService(
      OrderQueryService queries,
      OrderingService ordering,
      PaymentService payments,
      RefundService refunds,
      FulfillmentService fulfillment) {
    this.queries = queries;
    this.ordering = ordering;
    this.payments = payments;
    this.refunds = refunds;
    this.fulfillment = fulfillment;
  }

  public Map<String, Object> raw(long id, boolean lock) {
    return queries.raw(id, lock);
  }

  public void authorize(Actor actor, Map<String, Object> order) {
    queries.authorize(actor, order);
  }

  public Map<String, Object> get(Actor actor, long id) {
    return queries.get(actor, id);
  }

  public Map<String, Object> list(
      Actor actor, int page, int size, String status, Long site, String date) {
    return queries.list(actor, page, size, status, site, date);
  }

  public Map<String,Object> list(Actor p,int page,int size,String status,Long site,String date,String keyword){return queries.list(p,page,size,status,site,date,keyword);}

  public Map<String,Object> list(Actor p,int page,int size,String status,Long site,String date,String keyword,Long elderId,String mealType,String dateFrom,String dateTo){return queries.list(p,page,size,status,site,date,keyword,elderId,mealType,dateFrom,dateTo);}

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> create(Actor actor, Map<String, Object> input, String key) {
    return ordering.create(actor, input, key);
  }

  public List<String> channels(Actor actor) {
    return payments.channels(actor);
  }

  public Map<String, Object> payment(Actor actor, long id, String channel) {
    return payments.payment(actor, id, channel);
  }

  public List<Map<String, Object>> payments(Actor actor, long id) {
    return payments.payments(actor, id);
  }

  public Map<String, Object> simulate(Actor actor, long payment) {
    return payments.simulate(actor, payment);
  }

  public Map<String, Object> cancel(Actor actor, long id, Map<String, Object> input) {
    return refunds.cancel(actor, id, input);
  }

  public Map<String, Object> prepare(Actor actor, long id, boolean ready) {
    return fulfillment.prepare(actor, id, ready);
  }

  public Map<String, Object> voucher(Actor actor, long id) {
    return fulfillment.voucher(actor, id);
  }

  public Map<String, Object> redemption(Actor actor, Map<String, Object> input, boolean confirm) {
    return fulfillment.redemption(actor, input, confirm);
  }

  public void attendance(long site, LocalDate date, String meal) {
    fulfillment.attendance(site, date, meal);
  }

  public List<Map<String, Object>> deliveryList(
      Actor actor, long site, LocalDate date, String meal) {
    return fulfillment.deliveryList(actor, site, date, meal);
  }

  public Map<String, Object> delivery(Actor actor, long id, String status) {
    return fulfillment.delivery(actor, id, status);
  }

  public Map<String, Object> refunds(Actor actor, int page, int size, String keyword) {
    return refunds.refunds(actor, page, size, keyword);
  }

  public Map<String,Object> refunds(Actor p,int page,int size,String keyword,String status,Long siteId){return refunds.refunds(p,page,size,keyword,status,siteId);}
  public Map<String,Object> refunds(Actor p,int page,int size,String keyword,String status){return refunds.refunds(p,page,size,keyword,status);}
  public Map<String, Object> refund(Actor actor, long id) {
    return refunds.refund(actor, id);
  }

  public Map<String, Object> reviewRefund(
      Actor actor, long id, String action, Map<String, Object> input) {
    return refunds.reviewRefund(actor, id, action, input);
  }

  public Map<String, Object> exception(Actor actor, long id, Map<String, Object> input) {
    return queries.exception(actor, id, input);
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
  public void expire() {
    ordering.expire();
  }
}
