-- Read-only post-regression checks; counts must be zero.
SELECT 'amount_snapshot_mismatch' test, COUNT(*) failures
FROM meal_order o LEFT JOIN (
 SELECT order_id,SUM(qty*original_unit_price) original,SUM(qty*actual_unit_price) payable,SUM(qty*unit_subsidy) subsidy
 FROM meal_order_item GROUP BY order_id
) i ON i.order_id=o.id
WHERE o.original_amount<>COALESCE(i.original,0) OR o.payable_amount<>COALESCE(i.payable,0) OR o.subsidy_amount<>COALESCE(i.subsidy,0);
SELECT 'multiple_successful_payments' test,COUNT(*) failures FROM (
 SELECT order_id FROM payment_record WHERE status='SUCCESS' GROUP BY order_id HAVING COUNT(*)>1
) t;
SELECT 'duplicate_active_meal' test,COUNT(*) failures FROM (
 SELECT elder_id,meal_date,meal_type FROM meal_order WHERE status NOT IN('CANCELLED','REFUNDED')
 GROUP BY elder_id,meal_date,meal_type HAVING COUNT(*)>1
) t;
SELECT 'duplicate_redemption' test,COUNT(*) failures FROM (
 SELECT order_id FROM redemption GROUP BY order_id HAVING COUNT(*)>1
) t;
SELECT 'wrong_refund_ledger' test,COUNT(*) failures FROM refund_record r
WHERE r.status='REFUNDED' AND (
 (SELECT COUNT(*) FROM subsidy_ledger WHERE order_id=r.order_id AND entry_type='GRANT')<>1 OR
 (SELECT COUNT(*) FROM subsidy_ledger WHERE order_id=r.order_id AND entry_type='REVERSAL')<>1 OR
 (SELECT SUM(amount) FROM subsidy_ledger WHERE order_id=r.order_id)<>0
);
SELECT 'completed_without_handover' test,COUNT(*) failures FROM meal_order o WHERE o.status='COMPLETED' AND (
 (o.delivery_type='DELIVERY' AND NOT EXISTS(SELECT 1 FROM delivery_record WHERE order_id=o.id AND status='DELIVERED')) OR
 (o.delivery_type<>'DELIVERY' AND NOT EXISTS(SELECT 1 FROM redemption WHERE order_id=o.id))
);
