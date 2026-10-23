<?php
// FR04 / FR05 / FR06 - my modules and skills
// GET, needs token
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

$user   = require_auth($pdo);
// All 3 lists in one array (skill_type tells them apart)
$skills = skills_for_users($pdo, [$user['user_id']]);

ok('', ['skills' => $skills[$user['user_id']] ?? []]);
