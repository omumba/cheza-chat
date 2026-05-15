<?php
// backend/websocket/server.php
// Run with: php server.php
// Requires: composer require cboden/ratchet

require __DIR__ . '/../vendor/autoload.php';
require __DIR__ . '/../config/helpers.php';  // already includes database.php

use Ratchet\MessageComponentInterface;
use Ratchet\ConnectionInterface;
use Ratchet\Server\IoServer;
use Ratchet\Http\HttpServer;
use Ratchet\WebSocket\WsServer;

class ChezaChatServer implements MessageComponentInterface {

    // userId => ConnectionInterface
    private \SplObjectStorage $clients;
    private array $userConnections = [];   // userId  => conn
    private array $connUsers       = [];   // connId  => userId
    private array $typingTimers    = [];   // "userId:convId" => timestamp

    public function __construct() {
        $this->clients = new \SplObjectStorage();
        echo "[Cheza Chat WS] Server started on port 8080\n";
    }

    public function onOpen(ConnectionInterface $conn): void {
        // Authenticate via token in query string: wss://host:8080?token=xxx
        $query = [];
        parse_str($conn->httpRequest->getUri()->getQuery(), $query);
        $token = $query['token'] ?? '';

        $data = verifyJWT($token);
        if (!$data) {
            $conn->send(json_encode(['type' => 'error', 'message' => 'Unauthorized']));
            $conn->close();
            return;
        }

        $userId = (int)$data['sub'];
        $this->clients->attach($conn);
        $this->userConnections[$userId] = $conn;
        $this->connUsers[$conn->resourceId] = $userId;

        // Mark user online in DB
        $db = getDB();
        $db->prepare('UPDATE users SET is_online = 1, last_seen = ? WHERE id = ?')
           ->execute([round(microtime(true) * 1000), $userId]);

        // Notify conversations partners
        $this->broadcastPresence($userId, true, $db);

        echo "[WS] User $userId connected (conn #{$conn->resourceId})\n";
    }

    public function onMessage(ConnectionInterface $from, $msg): void {
        $userId = $this->connUsers[$from->resourceId] ?? null;
        if (!$userId) return;

        $data = json_decode($msg, true);
        if (!$data || empty($data['type'])) return;

        $db = getDB();

        switch ($data['type']) {

            case 'send_message':
                $this->handleSendMessage($from, $userId, $data, $db);
                break;

            case 'typing':
                $this->handleTyping($from, $userId, $data, $db);
                break;

            case 'read_receipt':
                $this->handleReadReceipt($from, $userId, $data, $db);
                break;

            case 'presence':
                // Manual presence ping — refresh last_seen
                $db->prepare('UPDATE users SET last_seen = ? WHERE id = ?')
                   ->execute([round(microtime(true) * 1000), $userId]);
                break;

            default:
                $from->send(json_encode(['type' => 'error', 'message' => 'Unknown event type']));
        }
    }

    private function handleSendMessage(ConnectionInterface $from, int $senderId, array $data, \PDO $db): void {
        $convId    = (int)($data['conversation_id'] ?? 0);
        $tempId    = (int)($data['temp_id'] ?? 0);
        $content   = trim($data['content'] ?? '');
        $msgType   = in_array($data['message_type'] ?? 'text', ['text','image','audio','video','file'])
                      ? $data['message_type'] : 'text';
        $mediaUrl  = $data['media_url']  ?? null;
        $mediaSize = isset($data['media_size']) ? (int)$data['media_size'] : null;
        $replyToId = isset($data['reply_to_id']) ? (int)$data['reply_to_id'] : null;

        if (!$convId || (empty($content) && !$mediaUrl)) return;

        // Verify membership
        $stmt = $db->prepare('SELECT id FROM conversation_members WHERE conversation_id = ? AND user_id = ?');
        $stmt->execute([$convId, $senderId]);
        if (!$stmt->fetch()) return;

        $createdAt = round(microtime(true) * 1000);

        // Persist to DB
        $stmt = $db->prepare("
            INSERT INTO messages (conversation_id, sender_id, content, type, media_url, media_size, reply_to_id, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'sent', ?)
        ");
        $stmt->execute([$convId, $senderId, $content, $msgType, $mediaUrl, $mediaSize, $replyToId, $createdAt]);
        $msgId = (int)$db->lastInsertId();

        // Fetch sender info
        $stmt = $db->prepare('SELECT name, avatar_url FROM users WHERE id = ?');
        $stmt->execute([$senderId]);
        $sender = $stmt->fetch();

        // Reply preview
        $replyPreview = null;
        if ($replyToId) {
            $stmt = $db->prepare('SELECT content FROM messages WHERE id = ?');
            $stmt->execute([$replyToId]);
            $replyPreview = $stmt->fetchColumn() ?: null;
        }

        $message = [
            'type'    => 'new_message',
            'message' => [
                'id'              => $msgId,
                'conversation_id' => $convId,
                'sender_id'       => $senderId,
                'sender_name'     => $sender['name'],
                'sender_avatar'   => $sender['avatar_url'],
                'content'         => $content,
                'type'            => $msgType,
                'media_url'       => $mediaUrl,
                'media_size'      => $mediaSize,
                'reply_to_id'     => $replyToId,
                'reply_preview'   => $replyPreview,
                'status'          => 'sent',
                'is_deleted'      => false,
                'created_at'      => $createdAt,
                'reactions'       => [],
            ],
            'temp_id' => $tempId,  // echoed back so sender can swap optimistic bubble
        ];

        // Get all members of this conversation
        $stmt = $db->prepare('SELECT user_id FROM conversation_members WHERE conversation_id = ?');
        $stmt->execute([$convId]);
        $members = $stmt->fetchAll(\PDO::FETCH_COLUMN);

        foreach ($members as $memberId) {
            $memberId = (int)$memberId;
            $conn     = $this->userConnections[$memberId] ?? null;

            if ($conn) {
                $statusMsg = $message;
                if ($memberId !== $senderId) {
                    // Update status to delivered
                    $statusMsg['message']['status'] = 'delivered';
                }
                $conn->send(json_encode($statusMsg));
            }
        }

        // Send 'sent' confirmation back to sender
        $from->send(json_encode([
            'type'       => 'message_status',
            'message_id' => $msgId,
            'status'     => 'sent',
        ]));

        // Update delivered status in DB for online members
        $onlineMembers = array_filter($members, fn($id) => isset($this->userConnections[(int)$id]) && (int)$id !== $senderId);
        if (!empty($onlineMembers)) {
            $db->prepare("UPDATE messages SET status = 'delivered' WHERE id = ?")
               ->execute([$msgId]);
        }

        echo "[WS] Message $msgId sent in conv $convId by user $senderId\n";
    }

    private function handleTyping(ConnectionInterface $from, int $userId, array $data, \PDO $db): void {
        $convId   = (int)($data['conversation_id'] ?? 0);
        $isTyping = (bool)($data['is_typing'] ?? false);
        if (!$convId) return;

        // Get conversation members
        $stmt = $db->prepare('SELECT user_id FROM conversation_members WHERE conversation_id = ? AND user_id != ?');
        $stmt->execute([$convId, $userId]);
        $members = $stmt->fetchAll(\PDO::FETCH_COLUMN);

        $event = json_encode([
            'type'            => 'typing',
            'conversation_id' => $convId,
            'user_id'         => $userId,
            'is_typing'       => $isTyping,
        ]);

        foreach ($members as $memberId) {
            $conn = $this->userConnections[(int)$memberId] ?? null;
            $conn?->send($event);
        }
    }

    private function handleReadReceipt(ConnectionInterface $from, int $userId, array $data, \PDO $db): void {
        $convId        = (int)($data['conversation_id']  ?? 0);
        $lastMessageId = (int)($data['last_message_id']  ?? 0);
        if (!$convId || !$lastMessageId) return;

        // Mark all unread messages up to lastMessageId as read by this user
        $stmt = $db->prepare("
            SELECT id, sender_id FROM messages
            WHERE conversation_id = ? AND id <= ? AND sender_id != ? AND is_deleted = 0
        ");
        $stmt->execute([$convId, $lastMessageId, $userId]);
        $msgs = $stmt->fetchAll();

        foreach ($msgs as $msg) {
            $db->prepare("
                INSERT IGNORE INTO message_reads (message_id, user_id) VALUES (?, ?)
            ")->execute([$msg['id'], $userId]);

            // Update message status to 'read'
            $db->prepare("UPDATE messages SET status = 'read' WHERE id = ?")
               ->execute([$msg['id']]);

            // Notify original sender
            $senderConn = $this->userConnections[(int)$msg['sender_id']] ?? null;
            $senderConn?->send(json_encode([
                'type'       => 'message_status',
                'message_id' => (int)$msg['id'],
                'status'     => 'read',
            ]));
        }
    }

    public function onClose(ConnectionInterface $conn): void {
        $userId = $this->connUsers[$conn->resourceId] ?? null;

        if ($userId) {
            unset($this->userConnections[$userId]);
            unset($this->connUsers[$conn->resourceId]);

            $db        = getDB();
            $lastSeen  = round(microtime(true) * 1000);
            $db->prepare('UPDATE users SET is_online = 0, last_seen = ? WHERE id = ?')
               ->execute([$lastSeen, $userId]);

            $this->broadcastPresence($userId, false, $db, $lastSeen);
            echo "[WS] User $userId disconnected (conn #{$conn->resourceId})\n";
        }

        $this->clients->detach($conn);
    }

    public function onError(ConnectionInterface $conn, \Exception $e): void {
        echo "[WS ERROR] {$e->getMessage()}\n";
        $conn->close();
    }

    private function broadcastPresence(int $userId, bool $isOnline, \PDO $db, int $lastSeen = 0): void {
        if (!$lastSeen) $lastSeen = round(microtime(true) * 1000);

        // Get all users who share a conversation with this user
        $stmt = $db->prepare("
            SELECT DISTINCT cm2.user_id
            FROM conversation_members cm1
            JOIN conversation_members cm2 ON cm2.conversation_id = cm1.conversation_id
            WHERE cm1.user_id = ? AND cm2.user_id != ?
        ");
        $stmt->execute([$userId, $userId]);
        $partners = $stmt->fetchAll(\PDO::FETCH_COLUMN);

        $event = json_encode([
            'type'      => 'presence',
            'user_id'   => $userId,
            'is_online' => $isOnline,
            'last_seen' => $lastSeen,
        ]);

        foreach ($partners as $partnerId) {
            $conn = $this->userConnections[(int)$partnerId] ?? null;
            $conn?->send($event);
        }
    }
}

// ── Boot the server ────────────────────────────────────────────────────────────

$server = IoServer::factory(
    new HttpServer(
        new WsServer(
            new ChezaChatServer()
        )
    ),
    8080
);

echo "[Cheza Chat WS] Listening on ws://0.0.0.0:8080\n";
$server->run();
