-- Read-only additions; all failures must be zero. Original snapshot and wallet checks run separately.
SELECT 'multiple_default_addresses' test,COUNT(*) failures FROM (
 SELECT owner_user_id,elder_id FROM delivery_address WHERE is_default=1 GROUP BY owner_user_id,elder_id HAVING COUNT(*)>1
) a;
SELECT 'duplicate_message_sources' test,COUNT(*) failures FROM (
 SELECT user_id,source_key FROM inbox_message GROUP BY user_id,source_key HAVING COUNT(*)>1
) m;
SELECT 'invalid_subsidy_validity' test,COUNT(*) failures FROM subsidy_entitlement WHERE valid_from IS NOT NULL AND valid_to IS NOT NULL AND valid_to<valid_from;
SELECT 'wallet_payer_missing' test,COUNT(*) failures FROM payment_record WHERE channel='WALLET' AND status='SUCCESS' AND payer_user_id IS NULL;
SELECT 'duplicate_order_sources' test,COUNT(*) failures FROM (
 SELECT source_key FROM order_event GROUP BY source_key HAVING COUNT(*)>1
) e;
