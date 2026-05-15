<?php
require_once __DIR__ . '/../../config/helpers.php';
$auth = requireAuth();
$myId = (int)$auth['sub'];
$db   = getDB();
$now  = nowMs();

// Ensure tables exist
$db->exec("
    CREATE TABLE IF NOT EXISTS statuses (
        id         INT AUTO_INCREMENT PRIMARY KEY,
        user_id    INT NOT NULL,
        type       ENUM('text','image','video') NOT NULL DEFAULT 'text',
        content    TEXT,
        media_url  VARCHAR(500) DEFAULT NULL,
        bg_color   VARCHAR(7)   DEFAULT '#1D9E75',
        created_at BIGINT NOT NULL,
        expires_at BIGINT NOT NULL,
        INDEX idx_user_expires (user_id, expires_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
");
$db->exec("
    CREATE TABLE IF NOT EXISTS status_views (
        id        INT AUTO_INCREMENT PRIMARY KEY,
        status_id INT NOT NULL,
        viewer_id INT NOT NULL,
        viewed_at BIGINT NOT NULL,
        UNIQUE KEY uq_view (status_id, viewer_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
");

// My own active statuses
$myStmt = $db->prepare("
    SELECT s.*, u.name AS user_name, u.avatar_url AS user_avatar,
           (SELECT COUNT(*) FROM status_views sv WHERE sv.status_id = s.id) AS view_count,
           0 AS viewed_by_me
    FROM statuses s
    JOIN users u ON u.id = s.user_id
    WHERE s.user_id = ? AND s.expires_at > ?
    ORDER BY s.created_at ASC
");
$myStmt->execute([$myId, $now]);
$myStatuses = $myStmt->fetchAll();

// Friends' active statuses
$friendsStmt = $db->prepare("
    SELECT s.*, u.name AS user_name, u.avatar_url AS user_avatar,
           (SELECT COUNT(*) FROM status_views sv WHERE sv.status_id = s.id) AS view_count,
           (SELECT COUNT(*) FROM status_views sv WHERE sv.status_id = s.id AND sv.viewer_id = ?) AS viewed_by_me
    FROM statuses s
    JOIN users u ON u.id = s.user_id
    JOIN (
        SELECT CASE WHEN user_one = ? THEN user_two ELSE user_one END AS friend_id
        FROM friends
        WHERE user_one = ? OR user_two = ?
    ) f ON f.friend_id = s.user_id
    WHERE s.expires_at > ?
    ORDER BY s.user_id, s.created_at ASC
");
$friendsStmt->execute([$myId, $myId, $myId, $myId, $now]);
$friendStatuses = $friendsStmt->fetchAll();

// Group by user
$grouped = [];

// Add my own
if (!empty($myStatuses)) {
    $grouped[] = [
        'user_id'      => $myId,
        'user_name'    => $myStatuses[0]['user_name'],
        'user_avatar'  => $myStatuses[0]['user_avatar'],
        'statuses'     => array_map('formatStatus', $myStatuses),
        'has_unviewed' => false,
        'is_mine'      => true,
    ];
}

// Group friends
$byUser = [];
foreach ($friendStatuses as $s) {
    $uid = (int)$s['user_id'];
    if (!isset($byUser[$uid])) {
        $byUser[$uid] = [
            'user_id'      => $uid,
            'user_name'    => $s['user_name'],
            'user_avatar'  => $s['user_avatar'],
            'statuses'     => [],
            'has_unviewed' => false,
            'is_mine'      => false,
        ];
    }
    $formatted = formatStatus($s);
    $byUser[$uid]['statuses'][] = $formatted;
    if (!$formatted['viewed_by_me']) $byUser[$uid]['has_unviewed'] = true;
}

$grouped = array_merge($grouped, array_values($byUser));

jsonSuccess(['updates' => $grouped]);

function formatStatus(array $s): array {
    return [
        'id'          => (int)$s['id'],
        'user_id'     => (int)$s['user_id'],
        'type'        => $s['type'],
        'content'     => $s['content'],
        'media_url'   => $s['media_url'],
        'bg_color'    => $s['bg_color'],
        'created_at'  => (int)$s['created_at'],
        'expires_at'  => (int)$s['expires_at'],
        'view_count'  => (int)$s['view_count'],
        'viewed_by_me'=> (bool)$s['viewed_by_me'],
    ];
}
