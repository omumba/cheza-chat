<?php
// backend/api/users/update_profile.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth  = requireAuth();
$myId  = (int)$auth['sub'];
$body  = getBody();
$db    = getDB();

$fields = [];
$params = [];

if (!empty($body['name'])) {
    $fields[] = 'name = ?';
    $params[] = sanitize($body['name']);
}
if (isset($body['status'])) {
    $fields[] = 'status = ?';
    $params[] = sanitize(mb_substr($body['status'], 0, 200));
}
if (!empty($body['phone'])) {
    $fields[] = 'phone = ?';
    $params[] = sanitize($body['phone']);
}

if (empty($fields)) jsonError('Nothing to update');

$params[] = $myId;
$db->prepare('UPDATE users SET ' . implode(', ', $fields) . ' WHERE id = ?')
   ->execute($params);

$stmt = $db->prepare('SELECT id, name, email, phone, avatar_url, status, is_online, last_seen FROM users WHERE id = ?');
$stmt->execute([$myId]);
$user = $stmt->fetch();
$user['id']        = (int)$user['id'];
$user['is_online'] = (bool)$user['is_online'];
$user['last_seen'] = (int)$user['last_seen'];

jsonSuccess(['data' => $user], 'Profile updated');
