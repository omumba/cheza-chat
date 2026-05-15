<?php
// backend/api/auth/logout.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth = requireAuth();
$db   = getDB();

$db->prepare('UPDATE users SET is_online = 0, last_seen = ?, fcm_token = NULL WHERE id = ?')
   ->execute([round(microtime(true) * 1000), $auth['sub']]);

jsonSuccess([], 'Logged out successfully');
