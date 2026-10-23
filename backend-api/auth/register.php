<?php
// FR01 - registration
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

$data = read_json();


// READ THE INPUT
// Student numbers stored upper case, emails lower case

$student_number = strtoupper(str_in($data, 'student_number', 20));
$full_name      = str_in($data, 'full_name', 100);
$email          = strtolower(str_in($data, 'email', 100));
$password       = (string)($data['password'] ?? '');         
$campus         = str_in($data, 'campus_location', 20);
$phone_number   = preg_replace('/[\s\-]/', '', str_in($data, 'phone_number', 30));



// VALIDATION (NFR10) - app checks these too but the server has to as well
if ($student_number === '' || $full_name === '' || $email === '' || $password === '' || $campus === '' || $phone_number === '') {
    fail('All fields are required');
}
if (!preg_match('/^[A-Z0-9]{5,20}$/', $student_number)) {
    fail('Please enter a valid student number (letters and numbers only)');
}
if (mb_strlen($full_name) < 2 || mb_strlen($full_name) > 50) {
    fail('Full name must be between 2 and 50 characters');
}
// Only Eduvos student emails may register
if (!filter_var($email, FILTER_VALIDATE_EMAIL) || !str_ends_with($email, '@vossie.net')) {
    fail('Please use your @vossie.net email address');
}
if (strlen($email) > 50) {
    fail('Email address is too long');
}
// Must match the USERS.campus_location ENUM
if (!in_array($campus, ['Tygervalley', 'Mowbray'], true)) {
    fail('Invalid campus location');
}
if (!preg_match('/^\+?[0-9]{9,14}$/', $phone_number)) {
    fail('Please enter a valid phone number (9 to 14 digits)');
}
if (strlen($password) < PASSWORD_MIN_LENGTH) {
    fail('Password must be at least ' . PASSWORD_MIN_LENGTH . ' characters long');
}
if (strlen($password) > PASSWORD_MAX_LENGTH) {
    fail('Password must be at most ' . PASSWORD_MAX_LENGTH . ' characters long');
}



// DUPLICATE CHECKS - one account per student number / email
$chk = $pdo->prepare('SELECT COUNT(*) FROM USERS WHERE student_number = ?');
$chk->execute([$student_number]);
if ($chk->fetchColumn() > 0) {
    fail('Student number already exists', 409);
}
$chk = $pdo->prepare('SELECT COUNT(*) FROM USERS WHERE email = ?');
$chk->execute([$email]);
if ($chk->fetchColumn() > 0) {
    fail('Email already exists', 409);
}



// CREATE THE ACCOUNT
$mode   = (string)env_value('VERIFICATION_MODE', 'response');
$auto   = ($mode === 'auto');
$hashed = password_hash($password, PASSWORD_BCRYPT);
$code   = (string)random_int(100000, 999999);

// User + verification code saved together
try {
    $pdo->beginTransaction();

    $ins = $pdo->prepare(
        'INSERT INTO USERS (student_number, full_name, email, password_hash, campus_location, phone_number, account_status)
         VALUES (?, ?, ?, ?, ?, ?, ?)'
    );
    $ins->execute([
        $student_number, $full_name, $email, $hashed, $campus, $phone_number,
        $auto ? 'active' : 'pending_verification',
    ]);
    $user_id = (int)$pdo->lastInsertId();

    // Code is valid for 1 hour
    $ver = $pdo->prepare(
        'INSERT INTO ACCOUNT_VERIFICATIONS (user_id, verification_type, verification_status, verification_code, expires_at, verified_at)
         VALUES (?, \'email\', ?, ?, DATE_ADD(NOW(), INTERVAL 1 HOUR), ?)'
    );
    $ver->execute([
        $user_id,
        $auto ? 'verified' : 'not verified',
        $code,
        $auto ? date('Y-m-d H:i:s') : null,
    ]);

    $pdo->commit();
} catch (PDOException $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    if ($e->getCode() === '23000') {
        fail('Student number or email already exists', 409);
    }
    throw $e;
}



// RESPOND
audit($pdo, $user_id, 'account_created', 'USER', $user_id, 'Registration (mode: ' . $mode . ')');

$extra = ['user_id' => $user_id, 'email' => $email, 'verification_mode' => $mode];

if ($mode === 'response') {
    $extra['verification_code'] = $code;            // We weren't able to complete to email verification yet. 
} elseif ($mode === 'mail') {
    $extra['mail_sent'] = @mail(
        $email,
        'Your V2V verification code',
        "Your V2V verification code is $code. It expires in 1 hour.",
        'From: no-reply@v2v.app'
    );
}

ok(
    $auto ? 'Registration successful! You can log in now.' : 'Registration successful! Please verify your email.',
    $extra,
    201
);
