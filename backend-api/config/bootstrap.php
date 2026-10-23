<?php
// Included at the top of every endpoint
// Loads .env, connects to the DB ($pdo) and loads helpers.php
require_once __DIR__ . '/helpers.php';
require_once __DIR__ . '/database.php';   // also loads .env and creates $pdo

// Any uncaught error -> JSON error instead of an HTML page
set_exception_handler(function (Throwable $e) {
    error_log('V2V error: ' . $e->getMessage() . ' in ' . $e->getFile() . ':' . $e->getLine());
    if (!headers_sent()) {
        header('Content-Type: application/json; charset=utf-8');
        http_response_code(500);
    }
    echo json_encode([
        'success' => false,
        'message' => is_debug() ? $e->getMessage() : 'Something went wrong on the server. Please try again.',
    ]);
});
