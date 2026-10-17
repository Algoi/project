ALTER TABLE meal_order ADD COLUMN active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status NOT IN('CANCELLED','REFUNDED') THEN 1 ELSE NULL END) STORED,
 ADD UNIQUE KEY uk_active_elder_meal(elder_id,meal_date,meal_type,active_slot),
 ADD COLUMN idempotency_key VARCHAR(80), ADD COLUMN request_hash CHAR(64), ADD COLUMN pickup_cipher TEXT,
 ADD UNIQUE KEY uk_buyer_request(buyer_user_id,idempotency_key);
ALTER TABLE payment_record ADD COLUMN payer_user_id BIGINT;
CREATE INDEX idx_order_expiry ON meal_order(status,payment_expires_at);
