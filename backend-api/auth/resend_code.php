<?php
// FR01 - send a new verification code
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

$data  = read_json();
$email = strtolower(str_in($data, 'email', 100));
if ($email === '') {
    fail('Email is required');
}

// Same answer whether the email exists or not
$generic = 'If this account is waiting for verification, a new code has been issued.';

$st = $pdo->prepare('SELECT user_id, account_status FROM USERS WHERE email = ?');
$st->execute([$email]);
$user = $st->fetch();

if (!$user || $user['account_status'] !== 'pending_verification') {
    ok($generic);                                       // don't reveal whether the email exists
}
$uid = (int)$user['user_id'];



// RATE LIMIT - max one new code per minute
$rc = $pdo->prepare(
    "SELECT COUNT(*) FROM ACCOUNT_VERIFICATIONS
     WHERE user_id = ? AND verification_type = 'email' AND created_at > (NOW() - INTERVAL 1 MINUTE)"
);
$rc->execute([$uid]);
if ($rc->fetchColumn() > 0) {
    fail('Please wait a minute before requesting another code.', 429);
}



// NEW CODE (verify.php only looks at the newest one)

$mode = (string)env_value('VERIFICATION_MODE', 'response');
$code = (string)random_int(100000, 999999);

$pdo->prepare(
    "INSERT INTO ACCOUNT_VERIFICATIONS (user_id, verification_type, verification_status, verification_code, expires_at)
     VALUES (?, 'email', 'not verified', ?, DATE_ADD(NOW(), INTERVAL 1 HOUR))"
)->execute([$uid, $code]);

// Send it the same way as register.php
$extra = ['verification_mode' => $mode];
if ($mode === 'response') {
    $extra['verification_code'] = $code;                //Again, not able to get to complete email verification yet so this is what it is 
} elseif ($mode === 'mail') {
    @mail($email, 'Your V2V verification code', "Your V2V verification code is $code. It expires in 1 hour.", 'From: no-reply@v2v.app');
}

audit($pdo, $uid, 'verification_code_resent', 'USER', $uid, null);
ok($generic, $extra);
