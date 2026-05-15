<?php
// backend/api/messages/delete.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth  = requireAuth();
$myId  = (int)$auth['sub'];
$body  = getBody();
required($body, ['message_id']);

$msgId = (int)$body['message_id'];
$db    = getDB();

$stmt = $db->prepare('SELECT sender_id FROM messages WHERE id = ?');
$stmt->execute([$msgId]);
$msg = $stmt->fetch();

if (!$msg) jsonError('Message not found', 404);
if ((int)$msg['sender_id'] !== $myId) jsonError('You can only delete your own messages', 403);

$db->prepare("UPDATE messages SET is_deleted = 1, content = 'This message was deleted' WHERE id = ?")
   ->execute([$msgId]);

jsonSuccess([], 'Message deleted');
