<?php
// backend/api/conversations/create_group.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth    = requireAuth();
$myId    = (int)$auth['sub'];
$body    = getBody();
required($body, ['name', 'member_ids']);

$name      = sanitize($body['name']);
$memberIds = array_map('intval', (array)$body['member_ids']);

if (strlen($name) < 2)       jsonError('Group name must be at least 2 characters');
if (count($memberIds) < 2)   jsonError('A group needs at least 2 other members');
if (count($memberIds) > 256) jsonError('Maximum 256 members');

// Ensure creator is included
if (!in_array($myId, $memberIds, true)) {
    $memberIds[] = $myId;
}

$db = getDB();
$db->beginTransaction();

$db->prepare("INSERT INTO conversations (type, name, created_by) VALUES ('group', ?, ?)")
   ->execute([$name, $myId]);
$convId = (int)$db->lastInsertId();

$placeholders = implode(',', array_fill(0, count($memberIds), '(?,?,?)'));
$params = [];
foreach ($memberIds as $uid) {
    $role     = ($uid === $myId) ? 'admin' : 'member';
    $params[] = $convId;
    $params[] = $uid;
    $params[] = $role;
}
$db->prepare("INSERT INTO conversation_members (conversation_id, user_id, role) VALUES $placeholders")
   ->execute($params);

$db->commit();

// Fetch member details to return
$inList = implode(',', array_fill(0, count($memberIds), '?'));
$stmt   = $db->prepare("SELECT id, name, avatar_url FROM users WHERE id IN ($inList)");
$stmt->execute($memberIds);
$members = $stmt->fetchAll();

$conversation = [
    'id'                => $convId,
    'type'              => 'group',
    'name'              => $name,
    'avatar_url'        => null,
    'last_message'      => null,
    'last_message_time' => 0,
    'unread_count'      => 0,
    'is_online'         => false,
    'other_user_id'     => null,
    'is_typing'         => false,
    'members'           => $members,
];

jsonSuccess(['data' => $conversation], 'Group created', 201);
