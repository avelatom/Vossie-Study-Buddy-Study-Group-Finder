<?php
// FR11 / FR12 - list + search study groups
// GET ?scope=all|mine &module_id= &q=
//   all = every group (default), mine = only groups I'm in
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];



// FILTERS FROM THE URL

$scope     = (isset($_GET['scope']) && $_GET['scope'] === 'mine') ? 'mine' : 'all';
$module_id = isset($_GET['module_id']) ? (int)$_GET['module_id'] : 0;
$q         = isset($_GET['q']) ? mb_substr(trim((string)$_GET['q']), 0, 50) : '';



// BUILD THE QUERY - each filter adds an AND

$sql    = group_select_sql() . ' WHERE 1 = 1';
$params = [':me' => $meId];

if ($scope === 'mine') {
    $sql .= ' AND EXISTS (SELECT 1 FROM GROUP_MEMBERS x WHERE x.group_id = g.group_id AND x.user_id = :me_scope)';
    $params[':me_scope'] = $meId;
}
if ($module_id > 0) {
    $sql .= ' AND g.module_id = :module_id';
    $params[':module_id'] = $module_id;
}
if ($q !== '') {
    // Free text matches the group name, description, module code or module name
    $like = '%' . like_escape($q) . '%';
    $sql .= ' AND (g.group_name LIKE :q1 OR g.description LIKE :q2 OR m.module_code LIKE :q3 OR m.module_name LIKE :q4)';
    $params[':q1'] = $like;
    $params[':q2'] = $like;
    $params[':q3'] = $like;
    $params[':q4'] = $like;
}
$sql .= ' ORDER BY g.created_at DESC, g.group_id DESC LIMIT 50';



// RUN AND FORMAT

$st = $pdo->prepare($sql);
$st->execute($params);

$groups = [];
foreach ($st->fetchAll() as $r) {
    $groups[] = group_row_out($r);
}
ok('', ['groups' => $groups]);
