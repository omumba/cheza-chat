<?php
require_once __DIR__ . '/../../config/helpers.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth     = requireAuth();
$myId     = (int)$auth['sub'];
$body     = getBody();
$statusId = (int)($body['status_id'] ?? 0);
if (!$statusId) jsonError('status_id required');

$db   = getDB();
$stmt = $db->prepare('DELETE FROM statuses WHERE id = ? AND user_id = ?');
$stmt->execute([$statusId, $myId]);
if ($stmt->rowCount() === 0) jsonError('Status not found or not yours', 403);

jsonSuccess([], 'Deleted');
