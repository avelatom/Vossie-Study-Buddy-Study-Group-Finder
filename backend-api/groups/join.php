<?php
// FR12 - join a study group
// POST { group_id }
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// Logged in student (from the token)
$me      = require_auth($pdo);
$meId    = $me['user_id'];
$data    = read_json();
$groupId = int_in($data, 'group_id');
if ($groupId <= 0) {
    fail('group_id is required');
}

$pdo->beginTransaction();
try {
    // lock the group row so two students cannot take the last free place at the same moment
    $lock = $pdo->prepare('SELECT group_id, max_members, creator_id FROM STUDY_GROUPS WHERE group_id = ? FOR UPDATE');
    $lock->execute([$groupId]);
    $g = $lock->fetch();
    if (!$g) {
        $pdo->rollBack();
        fail('Study group not found', 404);
    }

    // How many members the group has right now
    $c = $pdo->prepare('SELECT COUNT(*) FROM GROUP_MEMBERS WHERE group_id = ?');
    $c->execute([$groupId]);
    $count = (int)$c->fetchColumn();

    $already = $pdo->prepare('SELECT 1 FROM GROUP_MEMBERS WHERE group_id = ? AND user_id = ?');
    $already->execute([$groupId, $meId]);
    if ($already->fetchColumn()) {
        $pdo->rollBack();
        fail('You are already a member of this group', 409);
    }
    if ($count >= (int)$g['max_members']) {
        $pdo->rollBack();
        fail('This group is full', 409);
    }

    // Join as an ordinary member
    $pdo->prepare("INSERT INTO GROUP_MEMBERS (group_id, user_id, member_role) VALUES (?, ?, 'member')")
        ->execute([$groupId, $meId]);
    $pdo->commit();
} catch (PDOException $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    throw $e;
}

// Let the creator know (stored for FR17 notifications)
notify($pdo, (int)$g['creator_id'], 'group activity', $me['full_name'] . ' joined your study group.');
audit($pdo, $meId, 'group_joined', 'STUDY_GROUP', $groupId, null);
ok('You joined the group!', ['group' => fetch_group($pdo, $groupId, $meId)]);
