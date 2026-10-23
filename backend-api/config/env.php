<?php
function loadEnv(string $path): void
{
    // No .env -> defaults in database.php are used
    if (!file_exists($path)) {
        return;
    }

    $lines = file($path, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);

    foreach ($lines as $line) {
        $line = trim($line);

        // skip comments
        if ($line === '' || $line[0] === '#' || strpos($line, '=') === false) {
            continue;
        }

        // KEY=value, quotes removed
        [$key, $value] = explode('=', $line, 2);
        $key = trim($key);
        $value = trim(trim($value), "\"'");

        // real server environment variables take priority over the file
        if (getenv($key) === false) {
            putenv("$key=$value");
        }
    }
}