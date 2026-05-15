<?php
// backend/api/users/update_avatar.php
require_once __DIR__ . '/../../config/helpers.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth = requireAuth();
$myId = (int)$auth['sub'];

$upload = handleUpload('avatar', 'image');

$db = getDB();
$db->prepare('UPDATE users SET avatar_url = ? WHERE id = ?')
   ->execute([$upload['url'], $myId]);

jsonSuccess(['data' => ['avatar_url' => $upload['url']]], 'Avatar updated');
