<?php
// FR10 - my connections and requests
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];

// All my pending/accepted requests with the other student's details
// IF() picks the side that isn't me. Declined ones are left out
$st = $pdo->prepare(
    "SELECT c.connection_id, c.connection_sender_id, c.connection_receiver_id, c.connection_status, c.created_at, c.responded_at,
            u.user_id AS other_id, u.full_name AS other_name, u.campus_location AS other_campus
     FROM CONNECTION_REQUESTS c
     JOIN USERS u ON u.user_id = IF(c.connection_sender_id = ?, c.connection_receiver_id, c.connection_sender_id)
     WHERE (c.connection_sender_id = ? OR c.connection_receiver_id = ?)
       AND c.connection_status IN ('pending', 'accepted')
       AND u.account_status = 'active'
     ORDER BY c.created_at DESC"
);
$st->execute([$meId, $meId, $meId]);

// Split into the 3 sections on the Connections tab
$connected = [];   // accepted - either side
$incoming  = [];   // pending, sent TO me     -> Accept / Decline
$outgoing  = [];   // pending, sent BY me     -> waiting

foreach ($st->fetchAll() as $r) {
    $item = [
        'connection_id'   => (int)$r['connection_id'],
        'user_id'         => (int)$r['other_id'],
        'full_name'       => $r['other_name'],
        'campus_location' => $r['other_campus'],
        'created_at'      => $r['created_at'],
    ];
    if ($r['connection_status'] === 'accepted') {
        $connected[] = $item;
    } elseif ((int)$r['connection_receiver_id'] === $meId) {
        $incoming[] = $item;
    } else {
        $outgoing[] = $item;
    }
}

ok('', ['connected' => $connected, 'incoming' => $incoming, 'outgoing' => $outgoing]);
