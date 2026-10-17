-- Read-only checks; all failures must be zero.
SELECT 'wallet_balance_mismatch' test,COUNT(*) failures FROM wallet_account a LEFT JOIN
 (SELECT user_id,SUM(amount) amount FROM wallet_ledger GROUP BY user_id) l ON l.user_id=a.user_id
 WHERE a.balance<>COALESCE(l.amount,0) OR a.balance<0;
SELECT 'wallet_running_balance_mismatch' test,COUNT(*) failures FROM (
 SELECT amount,balance_after,LAG(balance_after,1,0) OVER(PARTITION BY user_id ORDER BY id) previous FROM wallet_ledger
) l WHERE balance_after<>previous+amount;
SELECT 'wallet_payment_mismatch' test,COUNT(*) failures FROM payment_record p LEFT JOIN wallet_ledger l
 ON l.user_id=p.payer_user_id AND l.reference_key=CONCAT('P:',p.id)
 WHERE p.channel='WALLET' AND p.status='SUCCESS' AND (l.id IS NULL OR l.amount<>-p.amount OR l.order_id<>p.order_id);
SELECT 'wallet_topup_mismatch' test,COUNT(*) failures FROM wallet_topup t LEFT JOIN wallet_ledger l
 ON l.user_id=t.user_id AND l.reference_key=CONCAT('T:',t.id)
 WHERE (t.status='SUCCESS' AND (l.id IS NULL OR l.amount<>t.amount)) OR (t.status<>'SUCCESS' AND l.id IS NOT NULL);
SELECT 'wallet_refund_mismatch' test,COUNT(*) failures FROM refund_record r JOIN payment_record p
 ON p.order_id=r.order_id AND p.status='SUCCESS' AND p.channel='WALLET' LEFT JOIN wallet_ledger l
 ON l.user_id=p.payer_user_id AND l.reference_key=CONCAT('R:',r.id)
 WHERE r.status='REFUNDED' AND (l.id IS NULL OR l.amount<>r.amount);
