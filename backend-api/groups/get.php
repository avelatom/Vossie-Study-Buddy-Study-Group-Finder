<?php
// FR12 - study group details
// GET ?group_id=
// Member list only shown to members
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// Logged in student (from the token)
$me      = require_auth($pdo);
$meId    = $me['user_id'];
$groupId = isset($_GET['group_id']) ? (int)$_GET['group_id'] : 0;
if ($groupId <= 0) {
    fail('group_id is required');
}

// Group details + member count + whether I am a member (helpers.php)
$group = fetch_group($pdo, $groupId, $meId);
if (!$group) {
    fail('Study group not found', 404);
}

// MEMBER LIST - members only, creator first
$members = [];
if ($group['is_member']) {
    $st = $pdo->prepare(
        "SELECT u.user_id, u.full_name, u.campus_location, gm.member_role, gm.joined_at
         FROM GROUP_MEMBERS gm JOIN USERS u ON u.user_id = gm.user_id
         WHERE gm.group_id = ? AND u.account_status = 'active'
         ORDER BY (gm.member_role = 'creator') DESC, gm.joined_at ASC"
    );
    $st->execute([$groupId]);
    foreach ($st->fetchAll() as $r) {
        $members[] = [
            'user_id'         => (int)$r['user_id'],
            'full_name'       => $r['full_name'],
            'campus_location' => $r['campus_location'],
            'member_role'     => $r['member_role'],
            'joined_at'       => $r['joined_at'],
        ];
    }
}
ok('', ['group' => $group, 'members' => $members]);
