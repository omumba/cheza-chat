<?php
// backend/api/messages/list.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'GET') jsonError('Method not allowed', 405);

$auth   = requireAuth();
$myId   = (int)$auth['sub'];
$convId = (int)($_GET['conversation_id'] ?? 0);
if (!$convId) jsonError('conversation_id is required');

$db = getDB();

// Verify membership
$stmt = $db->prepare('SELECT id FROM conversation_members WHERE conversation_id = ? AND user_id = ?');
$stmt->execute([$convId, $myId]);
if (!$stmt->fetch()) jsonError('Conversation not found or access denied', 403);

$pg     = paginate();
$limit  = $pg['limit'];
$offset = $pg['offset'];

$sql = "
    SELECT
        m.id,
        m.conversation_id,
        m.sender_id,
        u.name          AS sender_name,
        u.avatar_url    AS sender_avatar,
        CASE WHEN m.is_deleted = 1 THEN 'This message was deleted' ELSE m.content END AS content,
        m.type,
        m.media_url,
        m.media_size,
        m.reply_to_id,
        rm.content      AS reply_preview,
        m.status,
        m.is_deleted,
        m.created_at,
        -- Reactions as JSON array
        (
            SELECT JSON_ARRAYAGG(
                JSON_OBJECT('emoji', r.emoji, 'user_id', r.user_id, 'user_name', ru.name)
            )
            FROM message_reactions r
            JOIN users ru ON ru.id = r.user_id
            WHERE r.message_id = m.id
        ) AS reactions
    FROM messages m
    JOIN users u ON u.id = m.sender_id
    LEFT JOIN messages rm ON rm.id = m.reply_to_id
    WHERE m.conversation_id = ?
    ORDER BY m.created_at DESC
    LIMIT ? OFFSET ?
";

$stmt = $db->prepare($sql);
$stmt->execute([$convId, $limit, $offset]);
$rows = $stmt->fetchAll();

// Reverse so oldest first (pagination goes newest first, UI shows oldest first)
$rows = array_reverse($rows);

$total = (int)$db->query("SELECT COUNT(*) FROM messages WHERE conversation_id = $convId")->fetchColumn();

$messages = array_map(function($row) {
    $reactions = $row['reactions'] ? json_decode($row['reactions'], true) : [];
    return [
        'id'              => (int)$row['id'],
        'conversation_id' => (int)$row['conversation_id'],
        'sender_id'       => (int)$row['sender_id'],
        'sender_name'     => $row['sender_name'],
        'sender_avatar'   => $row['sender_avatar'],
        'content'         => $row['content'],
        'type'            => $row['type'],
        'media_url'       => $row['media_url'],
        'media_size'      => $row['media_size'] ? (int)$row['media_size'] : null,
        'reply_to_id'     => $row['reply_to_id'] ? (int)$row['reply_to_id'] : null,
        'reply_preview'   => $row['reply_preview'],
        'status'          => $row['status'],
        'is_deleted'      => (bool)$row['is_deleted'],
        'created_at'      => (int)$row['created_at'],
        'reactions'       => $reactions,
    ];
}, $rows);

// Mark all as delivered for this user
$db->prepare("
    UPDATE messages
    SET status = 'delivered'
    WHERE conversation_id = ? AND sender_id != ? AND status = 'sent'
")->execute([$convId, $myId]);

jsonSuccess(['messages' => $messages, 'total' => $total, 'page' => $pg['page']]);
