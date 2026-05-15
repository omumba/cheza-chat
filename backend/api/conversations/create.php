<?php
// backend/api/conversations/create.php  (direct chat)
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth   = requireAuth();
$myId   = (int)$auth['sub'];
$body   = getBody();
required($body, ['user_id']);

$otherId = (int)$body['user_id'];
if ($otherId === $myId) jsonError('Cannot start a chat with yourself');

$db = getDB();

// Check the other user exists
$stmt = $db->prepare('SELECT id, name, avatar_url, status, is_online FROM users WHERE id = ?');
$stmt->execute([$otherId]);
$other = $stmt->fetch();
if (!$other) jsonError('User not found', 404);

// Check if a direct conversation already exists between these two users
$sql = "
    SELECT c.id FROM conversations c
    JOIN conversation_members cm1 ON cm1.conversation_id = c.id AND cm1.user_id = ?
    JOIN conversation_members cm2 ON cm2.conversation_id = c.id AND cm2.user_id = ?
    WHERE c.type = 'direct'
    LIMIT 1
";
$stmt = $db->prepare($sql);
$stmt->execute([$myId, $otherId]);
$existing = $stmt->fetch();

if ($existing) {
    $convId = (int)$existing['id'];
} else {
    $db->beginTransaction();
    $db->prepare("INSERT INTO conversations (type, created_by) VALUES ('direct', ?)")
       ->execute([$myId]);
    $convId = (int)$db->lastInsertId();
    $db->prepare("INSERT INTO conversation_members (conversation_id, user_id, role) VALUES (?,?,'member'),(?,?,'member')")
       ->execute([$convId, $myId, $convId, $otherId]);
    $db->commit();
}

$conversation = [
    'id'                => $convId,
    'type'              => 'direct',
    'name'              => $other['name'],
    'avatar_url'        => $other['avatar_url'],
    'last_message'      => null,
    'last_message_time' => 0,
    'unread_count'      => 0,
    'is_online'         => (bool)$other['is_online'],
    'other_user_id'     => (int)$other['id'],
    'is_typing'         => false,
];

jsonSuccess(['data' => $conversation], 'Conversation ready');
