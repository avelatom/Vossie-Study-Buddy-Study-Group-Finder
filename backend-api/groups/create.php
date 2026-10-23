<?php
// FR11 - create a study group
// POST { group_name, module_id, description?, max_members? }
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];
$data = read_json();



// VALIDATE

$name        = str_in($data, 'group_name', 100);
$module_id   = int_in($data, 'module_id');
$description = str_in($data, 'description', 500);
// Default group size is 5 when the app does not send one
$max         = isset($data['max_members']) && $data['max_members'] !== '' ? int_in($data, 'max_members') : 5;

if (mb_strlen($name) < 3 || mb_strlen($name) > 50) {
    fail('Group name must be between 3 and 50 characters');
}
if ($module_id <= 0) {
    fail('Please choose a module for the group');
}
if (mb_strlen($description) > 255) {
    fail('The group description must be 255 characters or fewer');
}
if ($max < GROUP_MIN_MEMBERS || $max > GROUP_MAX_MEMBERS) {
    fail('Maximum members must be between ' . GROUP_MIN_MEMBERS . ' and ' . GROUP_MAX_MEMBERS);
}

$m = $pdo->prepare('SELECT module_id FROM MODULES WHERE module_id = ?');
$m->execute([$module_id]);
if (!$m->fetchColumn()) {
    fail('Module not found', 404);
}

// Spam protection: a student can create at most 10 groups
$cnt = $pdo->prepare('SELECT COUNT(*) FROM STUDY_GROUPS WHERE creator_id = ?');
$cnt->execute([$meId]);
if ($cnt->fetchColumn() >= 10) {
    fail('You can only create up to 10 study groups.');
}



// CREATE - the group and its creator's membership are saved together

$pdo->beginTransaction();
try {
    $pdo->prepare('INSERT INTO STUDY_GROUPS (group_name, module_id, description, creator_id, max_members) VALUES (?, ?, ?, ?, ?)')
        ->execute([$name, $module_id, $description !== '' ? $description : null, $meId, $max]);
    $groupId = (int)$pdo->lastInsertId();

    // The creator is automatically the first member
    $pdo->prepare("INSERT INTO GROUP_MEMBERS (group_id, user_id, member_role) VALUES (?, ?, 'creator')")
        ->execute([$groupId, $meId]);
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    throw $e;
}

audit($pdo, $meId, 'group_created', 'STUDY_GROUP', $groupId, null);
// Send the new group back (same format as groups/list.php)
ok('Study group created!', ['group' => fetch_group($pdo, $groupId, $meId)], 201);
