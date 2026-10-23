<?php
// FR04 - list of all modules
// GET, needs token
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');
require_auth($pdo);                                   // only logged-in students can read the module list

$rows = $pdo->query('SELECT module_id, module_code, module_name, faculty FROM MODULES ORDER BY module_code')->fetchAll();
// PDO gives numbers back as strings
foreach ($rows as &$r) {
    $r['module_id'] = (int)$r['module_id'];
}
unset($r);

ok('', ['modules' => $rows]);
