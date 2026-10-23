<?php
// FR01 - email verification (unfinished ;-;, This section was completed in this way for testing purposes)
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

$data  = read_json();
$email = strtolower(str_in($data, 'email', 100));
$code  = str_in($data, 'code', 10);

if ($email === '' || $code === '') {
    fail('Email and verification code are required');
}



// FIND THE ACCOUNT

$st = $pdo->prepare('SELECT user_id, account_status FROM USERS WHERE email = ?');
$st->execute([$email]);
$user = $st->fetch();

if (!$user) {
    fail('Incorrect verification code', 400);          // same message as a wrong code - do not reveal which emails exist
}
$uid = (int)$user['user_id'];

if ($user['account_status'] === 'active') {
    ok('Your account is already verified. You can log in.');
}
if ($user['account_status'] !== 'pending_verification') {
    fail('This account cannot be verified. Please contact support.', 403);
}
// Brute-force protection
if (recent_events($pdo, 'verify_failed', $uid, 60) >= 10) {
    fail('Too many incorrect attempts. Please try again later.', 429);
}



// CHECK THE CODE - only the newest one counts

$vs = $pdo->prepare(
    "SELECT verification_id, verification_code, (expires_at > NOW()) AS still_valid
     FROM ACCOUNT_VERIFICATIONS
     WHERE user_id = ? AND verification_type = 'email' AND verification_status = 'not verified'
     ORDER BY verification_id DESC LIMIT 1"
);
$vs->execute([$uid]);
$ver = $vs->fetch();

// hash_equals so the code can't be guessed from timing
if (!$ver || !hash_equals((string)$ver['verification_code'], $code)) {
    audit($pdo, $uid, 'verify_failed', 'USER', $uid, 'Incorrect verification code');
    fail('Incorrect verification code', 400);
}
if (!(int)$ver['still_valid']) {
    fail('This code has expired. Please request a new one.', 400);
}



// ACTIVATE - mark code as used + account as active

$pdo->beginTransaction();
$pdo->prepare("UPDATE ACCOUNT_VERIFICATIONS SET verification_status = 'verified', verified_at = NOW() WHERE verification_id = ?")
    ->execute([$ver['verification_id']]);
$pdo->prepare("UPDATE USERS SET account_status = 'active' WHERE user_id = ? AND account_status = 'pending_verification'")
    ->execute([$uid]);
$pdo->commit();

audit($pdo, $uid, 'account_verified', 'USER', $uid, null);
ok('Email verified! You can now log in.');
