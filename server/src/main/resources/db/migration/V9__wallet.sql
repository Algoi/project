CREATE TABLE wallet_account (
 user_id BIGINT PRIMARY KEY, balance DECIMAL(12,2) NOT NULL DEFAULT 0,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES sys_user(id), CHECK(balance>=0)
);
CREATE TABLE wallet_topup (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 amount DECIMAL(10,2) NOT NULL, request_id VARCHAR(80) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', confirmed_at DATETIME,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_topup_request(user_id,request_id), FOREIGN KEY(user_id) REFERENCES sys_user(id),
 CHECK(amount>0)
);
CREATE TABLE wallet_ledger (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 entry_type VARCHAR(20) NOT NULL, amount DECIMAL(12,2) NOT NULL,
 balance_after DECIMAL(12,2) NOT NULL, reference_key VARCHAR(100) NOT NULL, order_id BIGINT,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_wallet_reference(user_id,reference_key),
 KEY idx_wallet_time(user_id,created_at),
 FOREIGN KEY(user_id) REFERENCES sys_user(id), FOREIGN KEY(order_id) REFERENCES meal_order(id),
 CHECK(balance_after>=0)
);
