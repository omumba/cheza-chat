<?php
// backend/api/messages/react.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth  = requireAuth();
$myId  = (int)$auth['sub'];
$body  = getBody();
required($body, ['message_id', 'emoji']);

$msgId = (int)$body['message_id'];
$emoji = mb_substr(trim($body['emoji']), 0, 10);
$db    = getDB();

// Upsert reaction (toggle off if same emoji)
$stmt = $db->prepare('SELECT id, emoji FROM message_reactions WHERE message_id = ? AND user_id = ?');
$stmt->execute([$msgId, $myId]);
$existing = $stmt->fetch();

if ($existing && $existing['emoji'] === $emoji) {
    // Remove reaction (toggle)
    $db->prepare('DELETE FROM message_reactions WHERE message_id = ? AND user_id = ?')
       ->execute([$msgId, $myId]);
    jsonSuccess([], 'Reaction removed');
} else {
    // Insert or update emoji
    $db->prepare('
        INSERT INTO message_reactions (message_id, user_id, emoji)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE emoji = VALUES(emoji), created_at = CURRENT_TIMESTAMP
    ')->execute([$msgId, $myId, $emoji]);
    jsonSuccess([], 'Reaction added');
}
