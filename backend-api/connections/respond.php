<?php
// FR10 - accept or decline a request I received
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];
$data = read_json();

$connectionId = int_in($data, 'connection_id');
$action       = str_in($data, 'action', 10);

if ($connectionId <= 0 || !in_array($action, ['accept', 'decline'], true)) {
    fail('connection_id and a valid action (accept or decline) are required');
}



// FIND THE REQUEST

$st = $pdo->prepare('SELECT * FROM CONNECTION_REQUESTS WHERE connection_id = ?');
$st->execute([$connectionId]);
$conn = $st->fetch();

// only the RECEIVER may answer, and only while the request is still pending
if (!$conn || (int)$conn['connection_receiver_id'] !== $meId) {
    fail('Request not found', 404);
}
if ($conn['connection_status'] !== 'pending') {
    fail('This request has already been answered', 409);
}



// SAVE THE ANSWER (+ open a chat if accepted)

$senderId  = (int)$conn['connection_sender_id'];
$newStatus = ($action === 'accept') ? 'accepted' : 'declined';

$pdo->beginTransaction();
try {
    $pdo->prepare('UPDATE CONNECTION_REQUESTS SET connection_status = ?, responded_at = NOW() WHERE connection_id = ?')
        ->execute([$newStatus, $connectionId]);

    $conversationId = null;
    if ($newStatus === 'accepted') {
        // FR13 - accepted connections can message each other straight away
        $conversationId = get_or_create_direct_conversation($pdo, $senderId, $meId);
    }
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    throw $e;
}

if ($newStatus === 'accepted') {
    notify($pdo, $senderId, 'request', $me['full_name'] . ' accepted your connection request.');
}

ok(
    $newStatus === 'accepted' ? 'Connection accepted!' : 'Request declined.',
    ['connection_status' => $newStatus, 'conversation_id' => $conversationId]
);
