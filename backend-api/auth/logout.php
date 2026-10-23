<?php
// FR18 - log out
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');


$user = require_auth($pdo);

// 1. revoke the token
$pdo->prepare('INSERT IGNORE INTO REVOKED_TOKENS (jti, expires_at) VALUES (?, FROM_UNIXTIME(?))')
    ->execute([$user['jti'], $user['token_exp']]);
$pdo->exec('DELETE FROM REVOKED_TOKENS WHERE expires_at < NOW()');

// 2. stop push notifications to this user's devices
$pdo->prepare('UPDATE DEVICE_TOKENS SET is_active = FALSE WHERE user_id = ?')->execute([$user['user_id']]);

audit($pdo, $user['user_id'], 'logout', 'USER', $user['user_id'], null);
ok('Logged out successfully');
