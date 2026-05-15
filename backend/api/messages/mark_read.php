<?php
require_once __DIR__ . '/../../config/helpers.php';

$payload = requireAuth();
$myId    = $payload['user_id'];
$body    = json_decode(file_get_contents('php://input'), true) ?? [];
$convId  = (int)($body['conversation_id'] ?? 0);

if (!$convId) respond(false, 'conversation_id is required', null, 422);

$pdo = getDB();

// Ensure caller is a member of this conversation
$chk = $pdo->prepare('SELECT id FROM conversation_members WHERE conversation_id = ? AND user_id = ?');
$chk->execute([$convId, $myId]);
if (!$chk->fetch()) respond(false, 'Access denied', null, 403);

// Mark all unread messages in this conversation as read (excluding own messages)
$stmt = $pdo->prepare(
    'UPDATE messages SET is_read = 1
     WHERE conversation_id = ? AND sender_id != ? AND is_read = 0'
);
$stmt->execute([$convId, $myId]);

respond(true, 'Marked as read', ['updated' => $stmt->rowCount()]);
