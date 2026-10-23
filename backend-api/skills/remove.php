<?php
// Remove one of my modules
// POST { skill_id }
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

$user     = require_auth($pdo);
$data     = read_json();
$skill_id = int_in($data, 'skill_id');

if ($skill_id <= 0) {
    fail('skill_id is required');
}

// user_id check so you can't delete someone else's
$del = $pdo->prepare('DELETE FROM USER_MODULE_SKILLS WHERE skill_id = ? AND user_id = ?');
$del->execute([$skill_id, $user['user_id']]);

if ($del->rowCount() === 0) {
    fail('Entry not found', 404);
}
ok('Removed');
