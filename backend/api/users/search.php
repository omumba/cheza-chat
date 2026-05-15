<?php
// backend/api/users/search.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'GET') jsonError('Method not allowed', 405);

$auth  = requireAuth();
$myId  = (int)$auth['sub'];
$q     = trim($_GET['q'] ?? '');

if (strlen($q) < 2) jsonError('Search query must be at least 2 characters');

$db   = getDB();
$like = '%' . $q . '%';

$stmt = $db->prepare("
    SELECT id, name, email, phone, avatar_url, status, is_online, last_seen
    FROM users
    WHERE id != ? AND (name LIKE ? OR email LIKE ? OR phone LIKE ?)
    ORDER BY is_online DESC, name ASC
    LIMIT 30
");
$stmt->execute([$myId, $like, $like, $like]);
$users = $stmt->fetchAll();

$users = array_map(function($u) {
    return [
        'id'         => (int)$u['id'],
        'name'       => $u['name'],
        'email'      => $u['email'],
        'phone'      => $u['phone'],
        'avatar_url' => $u['avatar_url'],
        'status'     => $u['status'],
        'is_online'  => (bool)$u['is_online'],
        'last_seen'  => (int)$u['last_seen'],
    ];
}, $users);

jsonSuccess(['users' => $users]);
