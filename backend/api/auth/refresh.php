<?php
// backend/api/auth/refresh.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth = requireAuth();
$myId = (int)$auth['sub'];
$db   = getDB();

$stmt = $db->prepare('SELECT id, name, email, phone, avatar_url, status, is_online, last_seen FROM users WHERE id = ?');
$stmt->execute([$myId]);
$user = $stmt->fetch();
if (!$user) jsonError('User not found', 404);

$token = generateJWT((int)$user['id'], $user['email']);

$user['id']        = (int)$user['id'];
$user['is_online'] = (bool)$user['is_online'];
$user['last_seen'] = (int)$user['last_seen'];

jsonSuccess(['token' => $token, 'user' => $user], 'Token refreshed');
