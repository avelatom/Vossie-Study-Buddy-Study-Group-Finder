<?php
// FR04 / FR05 / FR06 - add a module to my profile
// POST { module_id, skill_type, proficiency_level? }
//   studying = I'm doing this module, offering = I can help, requiring = I need help
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

$user = require_auth($pdo);
$uid  = $user['user_id'];
$data = read_json();


// VALIDATE

$module_id = int_in($data, 'module_id');
$type      = str_in($data, 'skill_type', 20);
$level     = str_in($data, 'proficiency_level', 20);

if ($module_id <= 0) {
    fail('Please choose a module');
}
if (!in_array($type, SKILL_TYPES, true)) {
    fail('Invalid skill type');
}
if ($level !== '' && !in_array($level, PROFICIENCY_LEVELS, true)) {
    fail('Invalid proficiency level');
}
if ($type === 'offering' && $level === '') {
    fail('Please choose how well you know this module');
}
if ($type !== 'offering') {
    $level = '';                                       // proficiency only makes sense for modules you can help with
}



// CHECKS AGAINST THE DATABASE

$m = $pdo->prepare('SELECT module_id, module_code, module_name FROM MODULES WHERE module_id = ?');
$m->execute([$module_id]);
$module = $m->fetch();
if (!$module) {
    fail('Module not found', 404);
}

// Limit of 40 entries across all three lists
$cnt = $pdo->prepare('SELECT COUNT(*) FROM USER_MODULE_SKILLS WHERE user_id = ?');
$cnt->execute([$uid]);
if ($cnt->fetchColumn() >= 40) {
    fail('You have reached the maximum number of modules. Remove one first.');
}

// Which lists is this module already in?
$existing = $pdo->prepare('SELECT skill_type FROM USER_MODULE_SKILLS WHERE user_id = ? AND module_id = ?');
$existing->execute([$uid, $module_id]);
$types = $existing->fetchAll(PDO::FETCH_COLUMN);

if (in_array($type, $types, true)) {
    fail('This module is already in that list', 409);
}
// A student cannot both offer help with a module AND need help with it
if (($type === 'offering' && in_array('requiring', $types, true)) ||
    ($type === 'requiring' && in_array('offering', $types, true))) {
    fail('You cannot offer help with and need help with the same module', 409);
}



// SAVE - unique key also stops duplicates

try {
    $pdo->prepare('INSERT INTO USER_MODULE_SKILLS (user_id, module_id, skill_type, proficiency_level) VALUES (?, ?, ?, ?)')
        ->execute([$uid, $module_id, $type, $level !== '' ? $level : null]);
} catch (PDOException $e) {
    if ($e->getCode() === '23000') {
        fail('This module is already in that list', 409);
    }
    throw $e;
}
$skill_id = (int)$pdo->lastInsertId();

// Send the new row back
ok('Saved', ['skill' => [
    'skill_id'          => $skill_id,
    'module_id'         => (int)$module['module_id'],
    'module_code'       => $module['module_code'],
    'module_name'       => $module['module_name'],
    'skill_type'        => $type,
    'proficiency_level' => $level !== '' ? $level : null,
]], 201);
