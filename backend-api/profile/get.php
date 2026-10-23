<?php
// FR03 / FR09 - view a profile
// GET               -> my own profile
// GET ?user_id=     -> someone else's, filtered by their privacy setting (NFR05)
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// No ?user_id= means my own profile
$me       = require_auth($pdo);
$meId     = $me['user_id'];
$targetId = isset($_GET['user_id']) ? (int)$_GET['user_id'] : $meId;

// MY OWN PROFILE
if ($targetId === $meId) {
    $skills = skills_for_users($pdo, [$meId]);
    ok('', ['profile' => [
        'user_id'            => $meId,
        'student_number'     => $me['student_number'],
        'full_name'          => $me['full_name'],
        'email'              => $me['email'],
        'phone_number'       => $me['phone_number'],
        'campus_location'    => $me['campus_location'],
        'aggregate_rating'   => $me['aggregate_rating'] !== null ? (float)$me['aggregate_rating'] : null,
        'bio'                => $me['bio'],
        'profile_visibility' => $me['profile_visibility'],
        'user_role'          => $me['user_role'],
        'connections'        => connection_count($pdo, $meId),
        'skills'             => $skills[$meId] ?? [],
    ]]);
}

// SOMEONE ELSE'S PROFILE
$st = $pdo->prepare(
    "SELECT user_id, full_name, campus_location, aggregate_rating, bio, profile_visibility
     FROM USERS WHERE user_id = ? AND account_status = 'active' AND user_role = 'student'"
);
$st->execute([$targetId]);
$u = $st->fetch();
if (!$u) {
    fail('Student not found', 404);
}

$conn = find_connection($pdo, $meId, $targetId);
$rel  = relation_of($conn, $meId);
$connected = ($rel['relation'] === 'connected');

// What may this viewer see?
//   public            -> everyone sees the full peer profile
//   connections_only  -> only connected students see the full peer profile
//   private           -> nobody else sees more than name and campus
$full = ($u['profile_visibility'] === 'public')
     || ($u['profile_visibility'] === 'connections_only' && $connected);

$profile = [
    'user_id'            => (int)$u['user_id'],
    'full_name'          => $u['full_name'],
    'campus_location'    => $u['campus_location'],
    'is_full'            => $full,
    'visibility'         => $u['profile_visibility'],
    'relation'           => $rel['relation'],
    'connection_id'      => $rel['connection_id'],
    'aggregate_rating'   => null,
    'bio'                => null,
    'skills'             => [],
];
if ($full) {
    $skills = skills_for_users($pdo, [$targetId]);
    $profile['aggregate_rating'] = $u['aggregate_rating'] !== null ? (float)$u['aggregate_rating'] : null;
    $profile['bio']              = $u['bio'];
    $profile['skills']           = $skills[$targetId] ?? [];
}
// FR16 - the rating I already gave them, for the feedback screen
$profile['my_rating'] = null;
if ($connected) {
    $mr = $pdo->prepare('SELECT rating_score, feedback_text FROM REVIEWS WHERE reviewer_id = ? AND reviewee_id = ? AND session_id IS NULL LIMIT 1');
    $mr->execute([$meId, $targetId]);
    if ($row = $mr->fetch()) {
        $profile['my_rating']  = (int)$row['rating_score'];
        $profile['my_comment'] = $row['feedback_text'];
    }
}

// email, phone number and student number are NEVER sent for other students

ok('', ['profile' => $profile]);
