<?php
// FR13 - my chats, newest first
// GET, needs token
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];

// All my chats with the other student, last message and unread count
// Chats with suspended/deactivated students are hidden
$st = $pdo->prepare(
    "SELECT c.conversation_id, c.conversation_type, c.created_at,
            u.user_id AS other_id, u.full_name AS other_name,
            (SELECT mm.message_text FROM MESSAGES mm WHERE mm.conversation_id = c.conversation_id ORDER BY mm.message_id DESC LIMIT 1) AS last_message,
            (SELECT mm.created_at   FROM MESSAGES mm WHERE mm.conversation_id = c.conversation_id ORDER BY mm.message_id DESC LIMIT 1) AS last_message_at,
            (SELECT COUNT(*) FROM MESSAGES mm WHERE mm.conversation_id = c.conversation_id AND mm.sender_id <> :me1 AND mm.is_read = 0) AS unread_count
     FROM CONVERSATION_MEMBERS cm
     JOIN CONVERSATIONS c         ON c.conversation_id  = cm.conversation_id
     JOIN CONVERSATION_MEMBERS c2 ON c2.conversation_id = c.conversation_id AND c2.user_id <> :me2
     JOIN USERS u                 ON u.user_id          = c2.user_id
     WHERE cm.user_id = :me3 AND c.conversation_type = 'direct' AND u.account_status = 'active'"
);
$st->execute([':me1' => $meId, ':me2' => $meId, ':me3' => $meId]);

$rows = [];
foreach ($st->fetchAll() as $r) {
    $rows[] = [
        'conversation_id' => (int)$r['conversation_id'],
        'type'            => $r['conversation_type'],
        'other_user_id'   => (int)$r['other_id'],
        'other_name'      => $r['other_name'],
        'last_message'    => $r['last_message'],
        'last_message_at' => $r['last_message_at'],
        'unread_count'    => (int)$r['unread_count'],
        'sort_time'       => $r['last_message_at'] ?? $r['created_at'],
    ];
}
// Newest first (empty chats use the time they were created)
usort($rows, function ($a, $b) {
    return strcmp((string)$b['sort_time'], (string)$a['sort_time']);
});
foreach ($rows as &$r) {
    unset($r['sort_time']);
}
unset($r);

ok('', ['conversations' => $rows]);
