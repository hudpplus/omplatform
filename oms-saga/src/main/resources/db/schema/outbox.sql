CREATE TABLE IF NOT EXISTS `outbox_message` (
  `id` VARCHAR(64) NOT NULL,
  `topic` VARCHAR(128) NOT NULL,
  `payload` TEXT,
  `status` VARCHAR(32) DEFAULT 'PENDING',
  `created_at` DATETIME(3),
  `sent_at` DATETIME(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

