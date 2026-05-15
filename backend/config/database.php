<?php
// backend/config/database.php
// Constants only — getDB() lives in helpers.php to avoid redeclaration errors.

define('DB_HOST',    'localhost');
define('DB_NAME',    'cheza_chat');
define('DB_USER',    'root');       // WAMP default; change for production
define('DB_PASS',    '');           // WAMP default; change for production
define('DB_CHARSET', 'utf8mb4');

define('JWT_SECRET', '7555223a9dfdd8a57f70826068787d47c3dc3c080f56bcfa07abb4403016a760');
define('JWT_EXPIRY', 86400 * 30);  // 30 days

define('UPLOAD_DIR', __DIR__ . '/../uploads/');
// ↓ Change YOUR_SERVER_IP to your machine's LAN IP (e.g. 192.168.1.68)
define('UPLOAD_URL', 'http://YOUR_SERVER_IP/cheza_backend/uploads/');
define('MAX_FILE_SIZE', 50 * 1024 * 1024); // 50 MB
