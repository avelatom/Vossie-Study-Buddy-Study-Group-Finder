<?php
// FR10 - send a connection request
// POST { receiver_id }
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];
$data = read_json();



// VALIDATE

$receiverId = int_in($data, 'receiver_id');
if ($receiverId <= 0) {
    fail('receiver_id is required');
}
if ($receiverId === $meId) {
    fail('You cannot connect with yourself');
}

// The other student must exist, be active and be a student (not an administrator)
$st = $pdo->prepare("SELECT user_id, full_name FROM USERS WHERE user_id = ? AND account_status = 'active' AND user_role = 'student'");
$st->execute([$receiverId]);
$receiver = $st->fetch();
if (!$receiver) {
    fail('Student not found', 404);
}



// CREATE OR RE-USE THE REQUEST
// Only ever one CONNECTION_REQUESTS row per pair of students

$existing = find_connection($pdo, $meId, $receiverId);

if ($existing === null) {
    // First contact - new pending request
    $pdo->prepare("INSERT INTO CONNECTION_REQUESTS (connection_sender_id, connection_receiver_id, connection_status) VALUES (?, ?, 'pending')")
        ->execute([$meId, $receiverId]);
    $connectionId = (int)$pdo->lastInsertId();

} elseif ($existing['connection_status'] === 'accepted') {
    fail('You are already connected with this student', 409);

} elseif ($existing['connection_status'] === 'pending') {
    if ((int)$existing['connection_sender_id'] === $meId) {
        fail('You have already sent a request to this student', 409);
    }
    fail('This student has already sent you a request. Accept it in the Connections tab.', 409);

} else {
    // previously declined - reuse the same row so there is only ever one row per pair of students
    $connectionId = (int)$existing['connection_id'];
    $pdo->prepare(
        "UPDATE CONNECTION_REQUESTS
         SET connection_sender_id = ?, connection_receiver_id = ?, connection_status = 'pending',
             created_at = NOW(), responded_at = NULL
         WHERE connection_id = ?"
    )->execute([$meId, $receiverId, $connectionId]);
}



// Tell the other student (stored for FR17 notifications)
notify($pdo, $receiverId, 'request', $me['full_name'] . ' sent you a connection request.');
ok('Connection request sent!', ['connection_id' => $connectionId, 'relation' => 'pending_sent'], 201);
