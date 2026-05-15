<?php
require_once __DIR__ . '/../../config/helpers.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth  = requireAuth();
$myId  = (int)$auth['sub'];
$body  = getBody();
required($body, ['conversation_id']);

$convId    = (int)$body['conversation_id'];
$content   = trim($body['content'] ?? '');
$msgType   = $body['message_type'] ?? 'text';
if (!in_array($msgType, ['text','image','audio','video','file'])) $msgType = 'text';
$mediaUrl  = $body['media_url']  ?? null;
$mediaSize = $body['media_size'] ?? null;
$replyToId = isset($body['reply_to_id']) ? (int)$body['reply_to_id'] : null;
$createdAt = nowMs();

if ($content === '' && !$mediaUrl) jsonError('Message cannot be empty');

$db = getDB();

$stmt = $db->prepare('SELECT id FROM conversation_members WHERE conversation_id = ? AND user_id = ?');
$stmt->execute([$convId, $myId]);
if (!$stmt->fetch()) jsonError('Access denied', 403);

// Fetch reply preview
$replyPreview = null;
if ($replyToId) {
    $r = $db->prepare('SELECT content, type FROM messages WHERE id = ?');
    $r->execute([$replyToId]);
    $orig = $r->fetch();
    if ($orig) {
        $replyPreview = $orig['type'] !== 'text'
            ? ucfirst($orig['type'])
            : mb_substr($orig['content'], 0, 80);
    }
}

$stmt = $db->prepare("
    INSERT INTO messages (conversation_id, sender_id, content, type, media_url, media_size, reply_to_id, reply_preview, status, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'sent', ?)
");
$stmt->execute([$convId, $myId, $content, $msgType, $mediaUrl, $mediaSize, $replyToId, $replyPreview, $createdAt]);
$msgId = (int)$db->lastInsertId();

$stmt = $db->prepare('SELECT name, avatar_url FROM users WHERE id = ?');
$stmt->execute([$myId]);
$sender = $stmt->fetch();

// Push to offline members
$stmt = $db->prepare("
    SELECT u.fcm_token FROM conversation_members cm
    JOIN users u ON u.id = cm.user_id
    WHERE cm.conversation_id = ? AND cm.user_id != ? AND u.fcm_token IS NOT NULL AND u.is_online = 0
");
$stmt->execute([$convId, $myId]);
foreach ($stmt->fetchAll() as $member) {
    sendPush(
        $member['fcm_token'],
        $sender['name'],
        $msgType === 'text' ? $content : '📎 ' . ucfirst($msgType),
        ['type' => 'message', 'conversation_id' => (string)$convId]
    );
}

jsonSuccess(['data' => [
    'id'              => $msgId,
    'conversation_id' => $convId,
    'sender_id'       => $myId,
    'sender_name'     => $sender['name'],
    'sender_avatar'   => $sender['avatar_url'],
    'content'         => $content,
    'type'            => $msgType,
    'media_url'       => $mediaUrl,
    'media_size'      => $mediaSize !== null ? (int)$mediaSize : null,
    'reply_to_id'     => $replyToId,
    'reply_preview'   => $replyPreview,
    'status'          => 'sent',
    'is_deleted'      => false,
    'created_at'      => $createdAt,
    'reactions'       => [],
]], 'Message sent', 201);
