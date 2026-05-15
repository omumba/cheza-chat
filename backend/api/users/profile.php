<?php
// backend/api/users/profile.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'GET') jsonError('Method not allowed', 405);

$auth = requireAuth();
$myId = (int)$auth['sub'];
$db   = getDB();

$stmt = $db->prepare('SELECT id, name, email, phone, avatar_url, status, is_online, last_seen FROM users WHERE id = ?');
$stmt->execute([$myId]);
$user = $stmt->fetch();
if (!$user) jsonError('User not found', 404);

$user['id']       = (int)$user['id'];
$user['is_online'] = (bool)$user['is_online'];
$user['last_seen'] = (int)$user['last_seen'];

jsonSuccess(['data' => $user]);
