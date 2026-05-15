<?php
// backend/api/auth/login.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$body = getBody();
required($body, ['email', 'password']);

$email    = strtolower(trim($body['email']));
$password = $body['password'];
$fcmToken = $body['fcm_token'] ?? null;

$db   = getDB();
$stmt = $db->prepare('SELECT * FROM users WHERE email = ?');
$stmt->execute([$email]);
$user = $stmt->fetch();

if (!$user || !password_verify($password, $user['password'])) {
    jsonError('Invalid email or password', 401);
}

// Update online status + FCM token
$db->prepare('UPDATE users SET is_online = 1, last_seen = ?, fcm_token = ? WHERE id = ?')
   ->execute([round(microtime(true) * 1000), $fcmToken, $user['id']]);

$token = generateJWT((int)$user['id'], $user['email']);

unset($user['password']);
$user['id']        = (int)$user['id'];
$user['is_online'] = true;
$user['last_seen'] = round(microtime(true) * 1000);

jsonSuccess(['token' => $token, 'user' => $user], 'Login successful');
