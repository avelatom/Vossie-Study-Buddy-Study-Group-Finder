<?php
// FR13 - open (or create) the chat with a connected student
// POST { user_id }
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];
$data = read_json();

$otherId = int_in($data, 'user_id');
if ($otherId <= 0 || $otherId === $meId) {
    fail('A valid user_id is required');
}
if (!are_connected($pdo, $meId, $otherId)) {
    fail('You can only message students you are connected with', 403);
}

$st = $pdo->prepare("SELECT user_id, full_name FROM USERS WHERE user_id = ? AND account_status = 'active'");
$st->execute([$otherId]);
$other = $st->fetch();
if (!$other) {
    fail('Student not found', 404);
}

// Reuse the existing chat or create one
$cid = get_or_create_direct_conversation($pdo, $meId, $otherId);
ok('', ['conversation_id' => $cid, 'other_user' => ['user_id' => (int)$other['user_id'], 'full_name' => $other['full_name']]]);
