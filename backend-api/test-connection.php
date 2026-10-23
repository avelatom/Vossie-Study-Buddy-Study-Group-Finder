<?php

header('Content-Type: application/json');
require __DIR__ . '/config/database.php';


$tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);

$response = [
    'success'     => true,
    'message'     => 'Database connected successfully',
    'table_count' => count($tables),
];

if ($debug) {
    $response['database'] = $dbname;
    $response['tables']   = $tables;
}
echo json_encode($response);
