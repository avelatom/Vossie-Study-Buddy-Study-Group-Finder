<?php
// DB connection - creates $pdo for every endpoint
// Settings come from .env, defaults are for a normal XAMPP install
require_once __DIR__ . '/env.php';
loadEnv(__DIR__ . '/../.env');


// CONNECTION SETTINGS

$host     = getenv('DB_HOST') ?: '127.0.0.1';
$port     = getenv('DB_PORT') ?: '3306';
$dbname   = getenv('DB_NAME') ?: 'v2v_db';
$username = getenv('DB_USER') ?: 'root';
$password = getenv('DB_PASS') ?: '';
$debug    = getenv('APP_DEBUG') === 'true';



// CONNECT
// utf8mb4 = full Unicode (names with accents, emoji in messages)

try {
    $dsn = "mysql:host=$host;port=$port;dbname=$dbname;charset=utf8mb4";
    $pdo = new PDO($dsn, $username, $password, [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION, 
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
} catch (PDOException $e) {
    error_log('DB connection failed: ' . $e->getMessage());
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode([
        'success' => false,
        'message' => $debug ? $e->getMessage() : 'Database connection failed',
    ]);
    exit;
}
