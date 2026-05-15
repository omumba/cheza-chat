<?php
// backend/api/users/update_fcm.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth  = requireAuth();
$myId  = (int)$auth['sub'];
$body  = getBody();
required($body, ['fcm_token']);

$db = getDB();
$db->prepare('UPDATE users SET fcm_token = ? WHERE id = ?')
   ->execute([$body['fcm_token'], $myId]);

jsonSuccess([], 'FCM token updated');
