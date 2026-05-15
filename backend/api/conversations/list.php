<?php
// backend/api/conversations/list.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'GET') jsonError('Method not allowed', 405);

$auth   = requireAuth();
$myId   = (int)$auth['sub'];
$db     = getDB();

// Fetch all conversations the user is a member of,
// with the last message and the other user's details for direct chats
$sql = "
    SELECT
        c.id,
        c.type,
        -- For direct chats: use the other user's name & avatar
        CASE WHEN c.type = 'direct'
             THEN ou.name
             ELSE c.name
        END AS name,
        CASE WHEN c.type = 'direct'
             THEN ou.avatar_url
             ELSE c.avatar_url
        END AS avatar_url,
        -- Last message details
        m.content        AS last_message,
        m.created_at     AS last_message_time,
        m.type           AS last_message_type,
        -- Online status for direct chats
        CASE WHEN c.type = 'direct' THEN ou.is_online   ELSE 0   END AS is_online,
        CASE WHEN c.type = 'direct' THEN ou.id          ELSE NULL END AS other_user_id,
        -- Unread count
        (
            SELECT COUNT(*)
            FROM messages um
            WHERE um.conversation_id = c.id
              AND um.sender_id != :myId1
              AND um.id NOT IN (
                  SELECT message_id FROM message_reads WHERE user_id = :myId2
              )
              AND um.is_deleted = 0
        ) AS unread_count
    FROM conversations c
    JOIN conversation_members cm ON cm.conversation_id = c.id AND cm.user_id = :myId3
    -- last message
    LEFT JOIN messages m ON m.id = (
        SELECT id FROM messages
        WHERE conversation_id = c.id AND is_deleted = 0
        ORDER BY created_at DESC LIMIT 1
    )
    -- other user for direct chat
    LEFT JOIN conversation_members ocm
        ON ocm.conversation_id = c.id AND ocm.user_id != :myId4 AND c.type = 'direct'
    LEFT JOIN users ou ON ou.id = ocm.user_id
    ORDER BY COALESCE(m.created_at, UNIX_TIMESTAMP(c.created_at)*1000) DESC
";

$stmt = $db->prepare($sql);
$stmt->execute([
    ':myId1' => $myId,
    ':myId2' => $myId,
    ':myId3' => $myId,
    ':myId4' => $myId,
]);

$rows = $stmt->fetchAll();

$conversations = array_map(function($row) {
    return [
        'id'                => (int)$row['id'],
        'type'              => $row['type'],
        'name'              => $row['name'] ?? 'Unknown',
        'avatar_url'        => $row['avatar_url'],
        'last_message'      => $row['last_message_type'] !== 'text'
                                ? '📎 ' . ucfirst($row['last_message_type'] ?? '')
                                : $row['last_message'],
        'last_message_time' => $row['last_message_time'] ? (int)$row['last_message_time'] : 0,
        'unread_count'      => (int)$row['unread_count'],
        'is_online'         => (bool)$row['is_online'],
        'other_user_id'     => $row['other_user_id'] ? (int)$row['other_user_id'] : null,
        'is_typing'         => false,
    ];
}, $rows);

jsonSuccess(['conversations' => $conversations]);
