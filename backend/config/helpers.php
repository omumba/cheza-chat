<?php
// backend/config/helpers.php

require_once __DIR__ . '/database.php';

// ── CORS — only send headers in web context, not CLI (WebSocket server) ────────
if (php_sapi_name() !== 'cli') {
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, Authorization');
    header('Content-Type: application/json; charset=utf-8');
    if (!empty($_SERVER['REQUEST_METHOD']) && $_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
        http_response_code(204); exit;
    }
}

// ── DB — single PDO instance ──────────────────────────────────────────────────
function getDB(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=' . DB_CHARSET;
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, [
                PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES   => false,
            ]);
        } catch (PDOException $e) {
            http_response_code(500);
            die(json_encode(['success' => false, 'message' => 'Database connection failed: ' . $e->getMessage()]));
        }
    }
    return $pdo;
}

// ── JSON helpers ──────────────────────────────────────────────────────────────
function jsonSuccess(array $data = [], string $message = 'OK', int $code = 200): never {
    if (php_sapi_name() !== 'cli') http_response_code($code);
    echo json_encode(array_merge(['success' => true, 'message' => $message], $data));
    exit;
}

function jsonError(string $message, int $code = 400): never {
    if (php_sapi_name() !== 'cli') http_response_code($code);
    echo json_encode(['success' => false, 'message' => $message]);
    exit;
}

function getBody(): array {
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : [];
}

// ── JWT ───────────────────────────────────────────────────────────────────────
function base64UrlEncode(string $data): string {
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

function base64UrlDecode(string $data): string {
    return base64_decode(strtr($data, '-_', '+/') . str_repeat('=', 3 - (3 + strlen($data)) % 4));
}

function generateJWT(int $userId, string $email): string {
    $header  = base64UrlEncode(json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
    $payload = base64UrlEncode(json_encode([
        'sub'   => $userId,
        'email' => $email,
        'iat'   => time(),
        'exp'   => time() + JWT_EXPIRY,
    ]));
    $sig = base64UrlEncode(hash_hmac('sha256', "$header.$payload", JWT_SECRET, true));
    return "$header.$payload.$sig";
}

function verifyJWT(string $token): ?array {
    $parts = explode('.', $token);
    if (count($parts) !== 3) return null;
    [$header, $payload, $sig] = $parts;
    $expected = base64UrlEncode(hash_hmac('sha256', "$header.$payload", JWT_SECRET, true));
    if (!hash_equals($expected, $sig)) return null;
    $data = json_decode(base64UrlDecode($payload), true);
    if (!$data || $data['exp'] < time()) return null;
    return $data;
}

// ── Auth middleware ───────────────────────────────────────────────────────────
function requireAuth(): array {
    $auth = '';
    if (!empty($_SERVER['HTTP_AUTHORIZATION'])) {
        $auth = $_SERVER['HTTP_AUTHORIZATION'];
    } elseif (function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        $auth = $headers['Authorization'] ?? '';
    }
    if (!str_starts_with($auth, 'Bearer ')) jsonError('Unauthorized', 401);
    $token = substr($auth, 7);
    $data  = verifyJWT($token);
    if (!$data) jsonError('Invalid or expired token', 401);
    if (!isset($data['sub']) && isset($data['user_id'])) {
        $data['sub'] = $data['user_id'];
    }
    if (empty($data['sub'])) jsonError('Invalid token payload', 401);
    return $data;
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function required(array $body, array $fields): void {
    foreach ($fields as $f) {
        if (!isset($body[$f]) || ($body[$f] === '' && $body[$f] !== 0)) {
            jsonError("Field '$f' is required");
        }
    }
}

function paginate(): array {
    $page  = max(1, (int)($_GET['page']  ?? 1));
    $limit = min(100, max(1, (int)($_GET['limit'] ?? 50)));
    return ['page' => $page, 'limit' => $limit, 'offset' => ($page - 1) * $limit];
}

function nowMs(): int {
    return (int)round(microtime(true) * 1000);
}

// ── File upload ───────────────────────────────────────────────────────────────
function handleUpload(string $field, string $type = 'file'): array {
    if (empty($_FILES[$field])) jsonError('No file uploaded');
    $file = $_FILES[$field];
    if ($file['error'] !== UPLOAD_ERR_OK) {
        $errs = [1=>'File too large (php.ini)',2=>'File too large (form)',3=>'Partial upload',
                 4=>'No file',6=>'No tmp dir',7=>'Write failed',8=>'Extension blocked'];
        jsonError('Upload error: ' . ($errs[$file['error']] ?? $file['error']));
    }
    if ($file['size'] > MAX_FILE_SIZE) jsonError('File too large (max 50 MB)');

    $mime = mime_content_type($file['tmp_name']);
    if ($type === 'file') {
        if (str_starts_with($mime, 'image/'))      $type = 'image';
        elseif (str_starts_with($mime, 'video/'))  $type = 'video';
        elseif (str_starts_with($mime, 'audio/'))  $type = 'audio';
    }

    $allowed = [
        'image' => ['image/jpeg','image/png','image/gif','image/webp','image/bmp'],
        'video' => ['video/mp4','video/webm','video/ogg','video/quicktime','video/x-msvideo'],
        'audio' => ['audio/mpeg','audio/mp4','audio/ogg','audio/wav','audio/aac','audio/x-m4a'],
    ];
    if (isset($allowed[$type]) && !in_array($mime, $allowed[$type])) {
        jsonError("File type '$mime' not allowed for '$type'");
    }

    $ext      = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    $ext      = preg_replace('/[^a-z0-9]/', '', $ext);
    $filename = $type . '_' . time() . '_' . bin2hex(random_bytes(6)) . '.' . $ext;
    $subdir   = $type . 's';
    $dir      = UPLOAD_DIR . $subdir . '/';
    if (!is_dir($dir)) mkdir($dir, 0755, true);

    if (!move_uploaded_file($file['tmp_name'], $dir . $filename)) {
        jsonError('Failed to save file');
    }

    return [
        'url'      => UPLOAD_URL . $subdir . '/' . $filename,
        'size'     => (string)$file['size'],
        'type'     => $type,
        'filename' => $file['name'],
        'mime'     => $mime,
    ];
}

// ── Push notification (FCM Legacy — key stored in app_settings) ───────────────
function sendPush(string $token, string $title, string $body, array $data = []): void {
    try {
        $stmt = getDB()->query("SELECT `value` FROM app_settings WHERE `key`='fcm_server_key'");
        $fcmKey = $stmt ? $stmt->fetchColumn() : '';
    } catch (Exception $e) { return; }
    if (!$fcmKey) return;

    $payload = json_encode([
        'to'           => $token,
        'notification' => ['title' => $title, 'body' => $body, 'sound' => 'default'],
        'data'         => $data,
        'priority'     => 'high',
    ]);
    $ch = curl_init('https://fcm.googleapis.com/fcm/send');
    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_POSTFIELDS     => $payload,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 5,
        CURLOPT_HTTPHEADER     => ['Content-Type: application/json', "Authorization: key=$fcmKey"],
    ]);
    curl_exec($ch);
    curl_close($ch);
}

// ── Maintenance mode check ────────────────────────────────────────────────────
function checkMaintenance(): void {
    try {
        $stmt = getDB()->query("SELECT `value` FROM app_settings WHERE `key`='maintenance_mode'");
        $row  = $stmt ? $stmt->fetch(PDO::FETCH_ASSOC) : null;
        if ($row && $row['value'] === '1') {
            $msgStmt = getDB()->query("SELECT `value` FROM app_settings WHERE `key`='maintenance_msg'");
            $msg = $msgStmt ? ($msgStmt->fetchColumn() ?: 'Down for maintenance') : 'Down for maintenance';
            http_response_code(503);
            die(json_encode(['success' => false, 'message' => $msg, 'maintenance' => true]));
        }
    } catch (Exception $e) { /* DB not ready — skip */ }
}

// ── Backwards-compat stubs ────────────────────────────────────────────────────
if (!function_exists('sanitize')) {
    function sanitize(string $v): string {
        return htmlspecialchars(trim($v), ENT_QUOTES, 'UTF-8');
    }
}
if (!function_exists('setCorsHeaders')) {
    function setCorsHeaders(): void { /* headers already sent above */ }
}
