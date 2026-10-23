<?php
// FR07 - peer search
// GET ?module_id= &type=offering|studying|requiring &q=
//   q matches module code, module name or student name
// Only public profiles show up (NFR05)
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];



// FILTERS FROM THE URL

$module_id = isset($_GET['module_id']) ? (int)$_GET['module_id'] : 0;
$type      = isset($_GET['type']) ? trim((string)$_GET['type']) : '';
$q         = isset($_GET['q']) ? trim((string)$_GET['q']) : '';
$q         = mb_substr($q, 0, 50);

if ($type !== '' && !in_array($type, SKILL_TYPES, true)) {
    fail('Invalid type filter');
}



// BUILD THE QUERY
// LEFT JOIN so students with no modules still show up
// DISTINCT so a student only shows once

$sql = "SELECT DISTINCT u.user_id, u.full_name
        FROM USERS u
        LEFT JOIN USER_MODULE_SKILLS s ON s.user_id = u.user_id
        LEFT JOIN MODULES m ON m.module_id = s.module_id
        WHERE u.user_id <> ?
          AND u.account_status = 'active'
          AND u.user_role = 'student'
          AND u.profile_visibility = 'public'";
$params = [$meId];

if ($module_id > 0) {
    $sql .= ' AND s.module_id = ?';
    $params[] = $module_id;
}
if ($type !== '') {
    $sql .= ' AND s.skill_type = ?';
    $params[] = $type;
}
if ($q !== '') {
    $like = '%' . like_escape($q) . '%';
    $sql .= ' AND (m.module_code LIKE ? OR m.module_name LIKE ? OR u.full_name LIKE ?)';
    array_push($params, $like, $like, $like);
}
$sql .= ' ORDER BY u.full_name LIMIT 50';



// RUN - build_peer_cards adds campus, rating, modules and connection status

$st = $pdo->prepare($sql);
$st->execute($params);
$ids = array_map('intval', array_column($st->fetchAll(), 'user_id'));

ok('', ['peers' => build_peer_cards($pdo, $meId, $ids)]);
