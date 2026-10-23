<?php
// FR13 - send a message to a connected student
// POST { conversation_id, message_text }
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];
$data = read_json();



// VALIDATE

$conversationId = int_in($data, 'conversation_id');
$text           = str_in($data, 'message_text', 5000);

if ($conversationId <= 0) {
    fail('conversation_id is required');
}
if ($text === '') {
    fail('Message cannot be empty');
}
if (mb_strlen($text) > 1000) {
    fail('Message is too long (maximum 1000 characters)');
}



// CHECK I CAN SEND - in the chat, still connected, other account active

$conv = require_conversation_member($pdo, $conversationId, $meId);

if ($conv['conversation_type'] !== 'direct') {
    fail('Group messaging is not available yet', 400);       // FR14 - future work
}
if (!$conv['can_send']) {
    fail('You can only message students you are connected with', 403);
}



// SAVE + send it back with the server time

$pdo->prepare('INSERT INTO MESSAGES (conversation_id, sender_id, message_text) VALUES (?, ?, ?)')
    ->execute([$conversationId, $meId, $text]);
$messageId = (int)$pdo->lastInsertId();

$row = $pdo->prepare('SELECT message_id, sender_id, message_text, created_at FROM MESSAGES WHERE message_id = ?');
$row->execute([$messageId]);
$m = $row->fetch();

ok('Sent', ['message' => [
    'message_id'   => (int)$m['message_id'],
    'sender_id'    => (int)$m['sender_id'],
    'message_text' => $m['message_text'],
    'created_at'   => $m['created_at'],
    'is_mine'      => true,
]], 201);
