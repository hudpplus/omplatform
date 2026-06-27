-- TCC persistence tables
CREATE TABLE IF NOT EXISTS tcc_transaction (
  tx_id varchar(64) PRIMARY KEY,
  order_no varchar(64),
  status varchar(32),
  gmt_create datetime,
  version bigint DEFAULT 0,
  gmt_modified datetime,
  deleted int DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tcc_participant_state (
  id bigint PRIMARY KEY AUTO_INCREMENT,
  tx_id varchar(64) NOT NULL,
  participant_id varchar(128) NOT NULL,
  status varchar(32) NOT NULL,
  try_data text,
  last_attempt datetime,
  version bigint DEFAULT 0,
  gmt_create datetime,
  gmt_modified datetime,
  deleted int DEFAULT 0,
  INDEX idx_tx_id (tx_id),
  INDEX idx_status (status),
  UNIQUE KEY ux_tx_participant (tx_id, participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

