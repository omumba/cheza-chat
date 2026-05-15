-- ============================================================
--  Cheza Chat  –  MySQL Schema  (canonical, run once on fresh DB)
--  mysql -u root cheza_chat < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS cheza_chat
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE cheza_chat;

-- ── Users ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                INT UNSIGNED    AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100)    NOT NULL,
    email             VARCHAR(150)    NOT NULL UNIQUE,
    phone             VARCHAR(20)     DEFAULT NULL,
    password          VARCHAR(255)    NOT NULL,
    avatar_url        VARCHAR(500)    DEFAULT NULL,
    status            VARCHAR(200)    DEFAULT 'Hey there! I am using Cheza Chat',
    is_online         TINYINT(1)      NOT NULL DEFAULT 0,
    last_seen         BIGINT          NOT NULL DEFAULT 0,
    fcm_token         VARCHAR(500)    DEFAULT NULL,
    last_seen_visible TINYINT(1)      NOT NULL DEFAULT 1,
    read_receipts_on  TINYINT(1)      NOT NULL DEFAULT 1,
    is_banned         TINYINT(1)      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email  (email),
    INDEX idx_online (is_online)
) ENGINE=InnoDB;

-- ── Conversations ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversations (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    type       ENUM('direct','group') NOT NULL DEFAULT 'direct',
    name       VARCHAR(100)  DEFAULT NULL,
    avatar_url VARCHAR(500)  DEFAULT NULL,
    created_by INT UNSIGNED  NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_updated (updated_at)
) ENGINE=InnoDB;

-- ── Conversation Members ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversation_members (
    id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id INT UNSIGNED NOT NULL,
    user_id         INT UNSIGNED NOT NULL,
    role            ENUM('member','admin') NOT NULL DEFAULT 'member',
    joined_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_conv_user (conversation_id, user_id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE
) ENGINE=InnoDB;

-- ── Messages ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS messages (
    id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id INT UNSIGNED NOT NULL,
    sender_id       INT UNSIGNED NOT NULL,
    content         TEXT         NOT NULL,
    type            ENUM('text','image','audio','video','file') NOT NULL DEFAULT 'text',
    media_url       VARCHAR(500) DEFAULT NULL,
    media_size      BIGINT       DEFAULT NULL,
    reply_to_id     INT UNSIGNED DEFAULT NULL,
    reply_preview   TEXT         DEFAULT NULL,
    status          ENUM('sending','sent','delivered','read') NOT NULL DEFAULT 'sent',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      BIGINT       NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id)       REFERENCES users(id)         ON DELETE CASCADE,
    FOREIGN KEY (reply_to_id)     REFERENCES messages(id)      ON DELETE SET NULL,
    INDEX idx_conv_time (conversation_id, created_at)
) ENGINE=InnoDB;

-- ── Message Read Receipts ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS message_reads (
    message_id INT UNSIGNED NOT NULL,
    user_id    INT UNSIGNED NOT NULL,
    read_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, user_id),
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
) ENGINE=InnoDB;

-- ── Message Reactions ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS message_reactions (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    message_id INT UNSIGNED NOT NULL,
    user_id    INT UNSIGNED NOT NULL,
    emoji      VARCHAR(10)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_reaction (message_id, user_id),
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
) ENGINE=InnoDB;

-- ── Password Resets ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS password_resets (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(150) NOT NULL,
    otp        VARCHAR(255) NOT NULL,
    expires_at DATETIME     NOT NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_email (email),
    INDEX idx_email (email)
) ENGINE=InnoDB;

-- ── Friends ───────────────────────────────────────────────────────────────────
-- One row per friendship (user_one < user_two by convention).
-- Query both directions: WHERE user_one=? OR user_two=?
CREATE TABLE IF NOT EXISTS friend_requests (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    sender_id   INT NOT NULL,
    receiver_id INT NOT NULL,
    status      ENUM('pending','accepted','rejected') DEFAULT 'pending',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_request (sender_id, receiver_id),
    FOREIGN KEY (sender_id)   REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_receiver (receiver_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS friends (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_one   INT NOT NULL,
    user_two   INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_friendship (user_one, user_two),
    FOREIGN KEY (user_one) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user_two) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ── Push Notification Log ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    recipient_id INT UNSIGNED NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT         NOT NULL,
    data         JSON         DEFAULT NULL,
    is_sent      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_recipient (recipient_id, is_sent)
) ENGINE=InnoDB;

-- ── Admin Users ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admins (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       ENUM('super_admin','admin') NOT NULL DEFAULT 'admin',
    is_active  TINYINT(1)   NOT NULL DEFAULT 1,
    last_login DATETIME     DEFAULT NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Default super admin: admin@cheza.app / Admin@1234 — CHANGE IMMEDIATELY
INSERT IGNORE INTO admins (name, email, password, role)
VALUES ('Super Admin', 'admin@cheza.app',
        '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'super_admin');

-- ── Admin Sessions ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_sessions (
    id         VARCHAR(128) NOT NULL PRIMARY KEY,
    admin_id   INT UNSIGNED NOT NULL,
    ip_address VARCHAR(45)  DEFAULT NULL,
    user_agent VARCHAR(255) DEFAULT NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME     NOT NULL,
    FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ── Audit Log ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    admin_id   INT UNSIGNED NOT NULL,
    action     VARCHAR(100) NOT NULL,
    target     VARCHAR(200) DEFAULT NULL,
    detail     TEXT         DEFAULT NULL,
    ip_address VARCHAR(45)  DEFAULT NULL,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin   (admin_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB;

-- ── App Settings ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_settings (
    `key`       VARCHAR(100) NOT NULL PRIMARY KEY,
    `value`     TEXT         DEFAULT NULL,
    `type`      ENUM('text','number','boolean','json','color','image') NOT NULL DEFAULT 'text',
    `group`     VARCHAR(50)  NOT NULL DEFAULT 'general',
    `label`     VARCHAR(150) NOT NULL,
    `updated_at` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT IGNORE INTO app_settings (`key`,`value`,`type`,`group`,`label`) VALUES
('app_name',           'Cheza Chat',                 'text',    'general',      'App Name'),
('app_tagline',        'Connect & Chat',              'text',    'general',      'App Tagline'),
('app_logo_url',       '',                            'image',   'general',      'App Logo URL'),
('app_primary_color',  '#1B98E0',                    'color',   'general',      'Primary Color'),
('max_file_size_mb',   '50',                          'number',  'general',      'Max Upload Size (MB)'),
('allowed_file_types', 'image,video,audio,file',     'text',    'general',      'Allowed File Types'),
('allow_registration', '1',                           'boolean', 'registration', 'Allow New Registrations'),
('require_email_verify','0',                          'boolean', 'registration', 'Require Email Verification'),
('allow_guest',        '0',                           'boolean', 'registration', 'Allow Guest Access'),
('smtp_host',          'smtp.gmail.com',              'text',    'email',        'SMTP Host'),
('smtp_port',          '587',                         'number',  'email',        'SMTP Port'),
('smtp_user',          '',                            'text',    'email',        'SMTP Username'),
('smtp_pass',          '',                            'text',    'email',        'SMTP Password'),
('smtp_from_name',     'Cheza Chat',                 'text',    'email',        'From Name'),
('smtp_from_email',    'noreply@chezachat.app',      'text',    'email',        'From Email'),
('smtp_encryption',    'tls',                         'text',    'email',        'Encryption (tls/ssl/none)'),
('maintenance_mode',   '0',                           'boolean', 'maintenance',  'Maintenance Mode'),
('maintenance_msg',    'We are upgrading. Back soon!','text',   'maintenance',  'Maintenance Message'),
('fcm_server_key',     '',                            'text',    'push',         'FCM Server Key'),
('push_enabled',       '1',                           'boolean', 'push',         'Push Notifications Enabled');
