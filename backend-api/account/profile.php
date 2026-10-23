<?php
// FR03 / FR04-FR06 - my profile with my modules split into the 3 lists
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// require_auth() already loads the logged-in student's USERS row and checks the account is active
$me = require_auth($pdo);
$id = $me['user_id'];

// Only the student's own details - this endpoint never returns another student's data
$profile = [
    'user_id'            => $id,
    'student_number'     => $me['student_number'],
    'full_name'          => $me['full_name'],
    'email'              => $me['email'],
    'phone_number'       => $me['phone_number'],
    'campus_location'    => $me['campus_location'],
    'bio'                => $me['bio'],
    'profile_photo_url'  => $me['profile_photo_url'],
    'profile_visibility' => $me['profile_visibility'],
];

// All modules/skills for this student (module code + name already joined in)
$skills = skills_for_users($pdo, [$id])[$id] ?? [];

// Separate them into the three lists
$studying  = [];   // FR04 modules I am studying
$offering  = [];   // FR05 modules I can help with
$requiring = [];   // FR06 modules I need help with

foreach ($skills as $skill) {
    if ($skill['skill_type'] === 'studying') {
        $studying[] = $skill;
    } elseif ($skill['skill_type'] === 'offering') {
        $offering[] = $skill;
    } elseif ($skill['skill_type'] === 'requiring') {
        $requiring[] = $skill;
    }
}

ok('Profile retrieved successfully', [
    'profile'          => $profile,
    'modules_studying' => $studying,
    'skills_offered'   => $offering,
    'skills_required'  => $requiring,
]);
