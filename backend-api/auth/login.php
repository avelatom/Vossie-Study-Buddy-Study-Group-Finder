<?php
// FR02 - login
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

$data = read_json();

// Use whichever field was sent
$login = str_in($data, 'login', 100);
if ($login === '') {
    $login = str_in($data, 'email', 100);
}
if ($login === '') {
    $login = str_in($data, 'student_number', 100);
}
$password = (string)($data['password'] ?? '');

if ($login === '' || $password === '') {
    fail('Please enter your email or student number, and your password');
}


// FIND THE ACCOUNT
// Has an @ -> email (stored lower case), otherwise student number (stored upper case)

$byEmail = str_contains($login, '@');
$column  = $byEmail ? 'email' : 'student_number';
$value   = $byEmail ? strtolower($login) : strtoupper($login);

$stmt = $pdo->prepare(
    "SELECT user_id, student_number, full_name, email, password_hash, campus_location, phone_number, account_status,
            aggregate_rating, user_role, profile_photo_url, bio, profile_visibility
     FROM USERS WHERE $column = ?"
);
$stmt->execute([$value]);
$user = $stmt->fetch();

// Same message for wrong email and wrong password so you can't tell which accounts exist
if (!$user) {
    fail('Incorrect email/student number or password', 401);
}
$uid = (int)$user['user_id'];


// CHECK THE PASSWORD

// Brute force protection
if (recent_events($pdo, 'login_failed', $uid, 15) >= 10) {
    fail('Too many failed attempts. Please try again in 15 minutes.', 429);
}

if (!password_verify($password, $user['password_hash'])) {
    audit($pdo, $uid, 'login_failed', 'USER', $uid, 'Wrong password');
    fail('Incorrect email/student number or password', 401);
}


// CHECK THE ACCOUNT STATUS
// Only checked after the password is right so we don't leak other people's account status

switch ($user['account_status']) {
    case 'pending_verification':
        // App sees needs_verification and opens the verify screen
        fail('Please verify your email before logging in.', 403, ['needs_verification' => true, 'email' => $user['email']]);
        break;
    case 'suspended':
        audit($pdo, $uid, 'login_blocked', 'USER', $uid, 'Suspended account');
        fail('Your account has been suspended. Please contact support.', 403);
        break;
    case 'deactivated':
        audit($pdo, $uid, 'login_blocked', 'USER', $uid, 'Deactivated account');
        fail('Your account has been deactivated. Please contact support.', 403);
        break;
}


// SUCCESS - create the login token (JWT)

unset($user['password_hash']);
$user['user_id'] = $uid;
$user['aggregate_rating'] = $user['aggregate_rating'] !== null ? (float)$user['aggregate_rating'] : null;

$jwt = jwt_create($uid, $user['user_role']);
audit($pdo, $uid, 'login_success', 'USER', $uid, null);

ok('Login successful', [
    'token'      => $jwt['token'],
    'expires_at' => $jwt['exp'],
    'user'       => $user,
]);
