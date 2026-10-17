-- Opaque Bearer tokens: only SHA-256 digests are persisted.
CREATE TABLE auth_session (
  token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  user_id BIGINT NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_session_user(user_id),
  KEY idx_session_expiry(expires_at),
  FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
