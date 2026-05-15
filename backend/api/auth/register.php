<?php
// backend/api/auth/register.php
require_once __DIR__ . '/../../config/helpers.php';
setCorsHeaders();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') jsonError('Method not allowed', 405);

$body = getBody();
required($body, ['name', 'email', 'password']);

$name     = sanitize($body['name']);
$email    = strtolower(trim($body['email']));
$phone    = sanitize($body['phone'] ?? '');
$password = $body['password'];

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) jsonError('Invalid email address');
if (strlen($password) < 6) jsonError('Password must be at least 6 characters');
if (strlen($name) < 2)     jsonError('Name must be at least 2 characters');

$db = getDB();

// Check duplicate
$stmt = $db->prepare('SELECT id FROM users WHERE email = ?');
$stmt->execute([$email]);
if ($stmt->fetch()) jsonError('Email is already registered');

$hash = password_hash($password, PASSWORD_BCRYPT, ['cost' => 12]);

$stmt = $db->prepare('
    INSERT INTO users (name, email, phone, password)
    VALUES (?, ?, ?, ?)
');
$stmt->execute([$name, $email, $phone, $hash]);
$userId = (int)$db->lastInsertId();

$token = generateJWT($userId, $email);

$user = [
    'id'         => $userId,
    'name'       => $name,
    'email'      => $email,
    'phone'      => $phone,
    'avatar_url' => null,
    'status'     => 'Hey there! I am using Cheza Chat',
    'is_online'  => true,
    'last_seen'  => time() * 1000,
];

jsonSuccess(['token' => $token, 'user' => $user], 'Registration successful', 201);
