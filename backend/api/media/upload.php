<?php
// backend/api/media/upload.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth   = requireAuth();
$myId   = (int)$auth['sub'];
$type   = $_POST['type'] ?? 'image';
$convId = (int)($_POST['conversation_id'] ?? 0);

if (!$convId) jsonError('conversation_id required');

$db = getDB();
$stmt = $db->prepare('SELECT id FROM conversation_members WHERE conversation_id = ? AND user_id = ?');
$stmt->execute([$convId, $myId]);
if (!$stmt->fetch()) jsonError('Access denied', 403);

$allowedByType = [
    'image' => ['image/jpeg', 'image/png', 'image/webp', 'image/gif'],
    'audio' => ['audio/mpeg', 'audio/ogg', 'audio/wav', 'audio/mp4', 'audio/aac', 'audio/webm'],
    'video' => ['video/mp4', 'video/webm', 'video/ogg'],
    'file'  => [
        'application/pdf', 'application/zip',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'text/plain',
    ],
];

$allowed = $allowedByType[$type] ?? $allowedByType['image'];
$upload  = handleUpload('file', $allowed);

jsonSuccess([
    'data' => [
        'url'      => $upload['url'],
        'filename' => $upload['filename'],
        'mime'     => $upload['mime'],
        'type'     => $type,
    ]
], 'File uploaded', 201);
