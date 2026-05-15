<?php
require_once __DIR__ . '/../../config/helpers.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$auth   = requireAuth();
$myId   = (int)$auth['sub'];
$body   = getBody();

$type     = in_array($body['type'] ?? 'text', ['text','image','video']) ? $body['type'] : 'text';
$content  = trim($body['content'] ?? '');
$mediaUrl = $body['media_url'] ?? null;
$bgColor  = $body['bg_color']  ?? '#1D9E75';

if ($type === 'text' && $content === '') jsonError('Content is required for text status');
if ($type !== 'text' && !$mediaUrl)     jsonError('media_url is required for media status');

$db        = getDB();
$now       = nowMs();
$expiresAt = $now + (24 * 60 * 60 * 1000); // 24 hours

$db->prepare("
    INSERT INTO statuses (user_id, type, content, media_url, bg_color, created_at, expires_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
")->execute([$myId, $type, $content, $mediaUrl, $bgColor, $now, $expiresAt]);

$id = (int)$db->lastInsertId();
jsonSuccess(['data' => ['id' => $id, 'expires_at' => $expiresAt]], 'Status posted', 201);
