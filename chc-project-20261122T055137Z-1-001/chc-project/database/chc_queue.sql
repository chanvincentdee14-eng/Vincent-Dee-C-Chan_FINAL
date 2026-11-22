    -- Community Health Center Queue Management System
    -- XAMPP / MySQL: phpMyAdmin -> SQL tab (or Import) -> Go
    -- Passwords are stored as plain text, so you see exactly what you typed.

    CREATE DATABASE IF NOT EXISTS `chc_queue`
      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    USE `chc_queue`;

    -- ---------------------------------------------------------------- users
    DROP TABLE IF EXISTS users;
    CREATE TABLE users (
      id INT AUTO_INCREMENT PRIMARY KEY,
      username VARCHAR(50) NOT NULL UNIQUE,
      password VARCHAR(100) NOT NULL,
      role ENUM('ADMIN','STAFF') NOT NULL DEFAULT 'STAFF',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;

    INSERT INTO users (username, password, role) VALUES
     ('admin', 'admin123', 'ADMIN'),
     ('staff', 'staff123', 'STAFF');

    -- ---------------------------------------------------------------- visits (patients / queue)
    CREATE TABLE IF NOT EXISTS visits (
      id INT AUTO_INCREMENT PRIMARY KEY,
      queue_no VARCHAR(12) NOT NULL,
      line_seq BIGINT NOT NULL,
      full_name VARCHAR(100) NOT NULL,
      contact VARCHAR(30) NULL,
      birthday DATE NULL,
      address VARCHAR(255) NULL,
      status ENUM('WAITING','SERVING','ON_HOLD','COMPLETED','DEQUEUED','DELETED') NOT NULL DEFAULT 'WAITING',
      previous_numbers VARCHAR(120) NULL,
      registered_by VARCHAR(50) NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      called_at DATETIME NULL,
      finished_at DATETIME NULL,
      INDEX idx_visits_status (status),
      INDEX idx_visits_created (created_at),
      INDEX idx_visits_line (line_seq)
    ) ENGINE=InnoDB;

    -- ---------------------------------------------------------------- counters (queue numbers)
    CREATE TABLE IF NOT EXISTS counters (
      name VARCHAR(30) PRIMARY KEY,
      value BIGINT NOT NULL DEFAULT 0
    ) ENGINE=InnoDB;
