<?php
// US07 - leave a study group
// POST { group_id }
// If the creator leaves the oldest member takes over, if the last member leaves the group is deleted
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
    // Lock the group row while membership changes (same idea as join.php)
    $lock = $pdo->prepare('SELECT group_id, creator_id FROM STUDY_GROUPS WHERE group_id = ? FOR UPDATE');
    $lock->execute([$groupId]);
    $g = $lock->fetch();

    // Remove me - if nothing was deleted I was not a member
    $del = $pdo->prepare('DELETE FROM GROUP_MEMBERS WHERE group_id = ? AND user_id = ?');
    $del->execute([$groupId, $meId]);
    if (!$g || $del->rowCount() === 0) {
        $pdo->rollBack();
        fail('You are not a member of this group', 404);
    }

    $left = $pdo->prepare('SELECT COUNT(*) FROM GROUP_MEMBERS WHERE group_id = ?');
    $left->execute([$groupId]);
    $remaining = (int)$left->fetchColumn();

    $message = 'You left the group.';
    if ($remaining === 0) {
        // Nobody left -> delete the empty group
        $pdo->prepare('DELETE FROM STUDY_GROUPS WHERE group_id = ?')->execute([$groupId]);
        $message = 'You left the group. It was removed because it has no members left.';
    } elseif ((int)$g['creator_id'] === $meId) {
        // The creator left -> hand the group to the member who joined first
        $next = $pdo->prepare('SELECT user_id FROM GROUP_MEMBERS WHERE group_id = ? ORDER BY joined_at ASC, user_id ASC LIMIT 1');
        $next->execute([$groupId]);
        $newOwner = (int)$next->fetchColumn();

        $pdo->prepare('UPDATE STUDY_GROUPS SET creator_id = ? WHERE group_id = ?')->execute([$newOwner, $groupId]);
        $pdo->prepare("UPDATE GROUP_MEMBERS SET member_role = 'creator' WHERE group_id = ? AND user_id = ?")->execute([$groupId, $newOwner]);
    }
    $pdo->commit();
} catch (PDOException $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    throw $e;
}

audit($pdo, $meId, 'group_left', 'STUDY_GROUP', $groupId, null);
ok($message);
