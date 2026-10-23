<?php
// FR13 - messages in a chat
// GET ?conversation_id= [&after_id=]
//   no after_id = latest 50, with after_id = only newer ones (used for polling)
// The other student's messages get marked as read
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];

$conversationId = isset($_GET['conversation_id']) ? (int)$_GET['conversation_id'] : 0;
$afterId        = isset($_GET['after_id']) ? (int)$_GET['after_id'] : 0;
if ($conversationId <= 0) {
    fail('conversation_id is required');
}

// Stops here with 404 if I am not in this conversation (helpers.php)
$conv = require_conversation_member($pdo, $conversationId, $meId);


// LOAD MESSAGES

if ($afterId > 0) {
    $st = $pdo->prepare(
        'SELECT message_id, sender_id, message_text, created_at FROM MESSAGES
         WHERE conversation_id = ? AND message_id > ? ORDER BY message_id ASC LIMIT 100'
    );
    $st->execute([$conversationId, $afterId]);
    $rows = $st->fetchAll();
} else {
    $st = $pdo->prepare(
        'SELECT message_id, sender_id, message_text, created_at FROM MESSAGES
         WHERE conversation_id = ? ORDER BY message_id DESC LIMIT 50'
    );
    $st->execute([$conversationId]);
    $rows = array_reverse($st->fetchAll());
}

// is_mine tells the app which side of the chat to draw the bubble on
$messages = [];
foreach ($rows as $r) {
    $messages[] = [
        'message_id'   => (int)$r['message_id'],
        'sender_id'    => (int)$r['sender_id'],
        'message_text' => $r['message_text'],
        'created_at'   => $r['created_at'],
        'is_mine'      => ((int)$r['sender_id'] === $meId),
    ];
}

// Mark their messages as read
$pdo->prepare('UPDATE MESSAGES SET is_read = 1 WHERE conversation_id = ? AND sender_id <> ? AND is_read = 0')
    ->execute([$conversationId, $meId]);

ok('', [
    'conversation_id' => $conversationId,
    'other_user'      => $conv['other_user'],
    'can_send'        => $conv['can_send'],
    'messages'        => $messages,
]);
