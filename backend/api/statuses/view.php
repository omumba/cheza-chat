<?php
require_once __DIR__ . '/../../config/helpers.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth     = requireAuth();
$myId     = (int)$auth['sub'];
$body     = getBody();
$statusId = (int)($body['status_id'] ?? 0);
if (!$statusId) jsonError('status_id required');

$db = getDB();
$db->prepare("
    INSERT IGNORE INTO status_views (status_id, viewer_id, viewed_at) VALUES (?, ?, ?)
")->execute([$statusId, $myId, nowMs()]);

jsonSuccess([], 'Viewed');
