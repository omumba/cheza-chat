<?php
// backend/api/conversations/get.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'GET') jsonError('Method not allowed', 405);

$auth = requireAuth();
$myId = (int)$auth['sub'];
$convId = isset($_GET['id']) ? (int)$_GET['id'] : 0;
if (!$convId) jsonError('Conversation ID required');

$db = getDB();

// Verify membership
$stmt = $db->prepare('SELECT id FROM conversation_members WHERE conversation_id = ? AND user_id = ?');
$stmt->execute([$convId, $myId]);
if (!$stmt->fetch()) jsonError('Access denied', 403);

// Fetch conversation
$stmt = $db->prepare('SELECT * FROM conversations WHERE id = ?');
$stmt->execute([$convId]);
$conv = $stmt->fetch();
if (!$conv) jsonError('Conversation not found', 404);

// Fetch all members
$stmt = $db->prepare("
    SELECT u.id, u.name, u.email, u.avatar_url, u.status, u.is_online, u.last_seen,
           cm.role
    FROM conversation_members cm
    JOIN users u ON u.id = cm.user_id
    WHERE cm.conversation_id = ?
");
$stmt->execute([$convId]);
$members = $stmt->fetchAll();

foreach ($members as &$m) {
    $m['id']        = (int)$m['id'];
    $m['is_online'] = (bool)$m['is_online'];
    $m['last_seen'] = (int)$m['last_seen'];
}

// For direct chats: resolve display name / avatar from other user's perspective
$otherUserId = null;
if ($conv['type'] === 'direct') {
    foreach ($members as $m) {
        if ($m['id'] !== $myId) {
            $otherUserId       = $m['id'];
            $conv['name']      = $m['name'];
            $conv['avatar_url']= $m['avatar_url'];
            $conv['is_online'] = $m['is_online'];
            break;
        }
    }
}

$conv['id']            = (int)$conv['id'];
$conv['members']       = $members;
$conv['other_user_id'] = $otherUserId;
$conv['unread_count']  = 0;

jsonSuccess(['data' => $conv]);
