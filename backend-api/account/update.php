<?php
// FR03 / FR04-FR06 / FR18 - update account info
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

// Logged in student - no token / expired token stops here with a 401
$user    = require_auth($pdo);
$user_id = $user['user_id'];

$data   = read_json();
$action = str_in($data, 'action', 30);


// VALIDATORS
// Used by more than one action. Returns the cleaned value or stops with a 400

function valid_name(string $name): string
{
    if ($name === '') {
        fail('Name cannot be empty');
    }
    if (mb_strlen($name) < 2 || mb_strlen($name) > 50) {
        fail('Name must be between 2 and 50 characters');
    }
    return $name;
}

function valid_phone(string $phone): string
{
    $phone = preg_replace('/[\s\-]/', '', $phone);           // allow "082 123 4567" and "082-123-4567"
    if ($phone === '') {
        fail('Phone number cannot be empty');
    }
    if (!preg_match('/^\+?[0-9]{9,14}$/', $phone)) {        // optional + then 9 to 14 digits
        fail('Please enter a valid phone number (9 to 14 digits)');
    }
    return $phone;
}

function valid_bio(string $bio): ?string
{
    if (mb_strlen($bio) > 500) {
        fail('Bio must be 500 characters or fewer');
    }
    return $bio !== '' ? $bio : null;                         // an empty bio is stored as NULL
}

function valid_visibility(string $v): string
{
    if (!in_array($v, VISIBILITY_OPTIONS, true)) {           // public / connections_only / private
        fail('Invalid visibility option');
    }
    return $v;
}


// SINGLE-FIELD UPDATES (FR03)

if ($action === 'update_name') {
    $name = valid_name(str_in($data, 'full_name', 100));
    $pdo->prepare('UPDATE USERS SET full_name = ? WHERE user_id = ?')->execute([$name, $user_id]);
    ok('Name updated successfully!');
} elseif ($action === 'update_phone') {
    $phone = valid_phone(str_in($data, 'phone_number', 30));
    $pdo->prepare('UPDATE USERS SET phone_number = ? WHERE user_id = ?')->execute([$phone, $user_id]);
    ok('Phone number updated successfully!');
} elseif ($action === 'update_bio') {
    $bio = valid_bio(str_in($data, 'bio', 1000));
    $pdo->prepare('UPDATE USERS SET bio = ? WHERE user_id = ?')->execute([$bio, $user_id]);
    ok('Bio updated successfully!');
} elseif ($action === 'update_visibility') {
    $vis = valid_visibility(str_in($data, 'profile_visibility', 20));
    $pdo->prepare('UPDATE USERS SET profile_visibility = ? WHERE user_id = ?')->execute([$vis, $user_id]);
    ok('Profile visibility updated successfully!');


    // WHOLE PROFILE UPDATE (FR03) - profile screen saves everything at once

} elseif ($action === 'update_profile') {
    // Validate everything first so nothing gets half saved
    $name  = valid_name(str_in($data, 'full_name', 100));
    $phone = valid_phone(str_in($data, 'phone_number', 30));
    $bio   = valid_bio(str_in($data, 'bio', 1000));
    $vis   = valid_visibility(str_in($data, 'profile_visibility', 20));
    $pdo->prepare('UPDATE USERS SET full_name = ?, phone_number = ?, bio = ?, profile_visibility = ? WHERE user_id = ?')
        ->execute([$name, $phone, $bio, $vis, $user_id]);
    // Send the saved values back so the app doesn't have to reload
    ok('Profile updated successfully!', [
        'user' => ['full_name' => $name, 'phone_number' => $phone, 'bio' => $bio, 'profile_visibility' => $vis],
    ]);


    // PASSWORD CHANGE (FR18)

} elseif ($action === 'update_password') {
    // Don't trim passwords, spaces are allowed
    $current  = (string)($data['current_password'] ?? '');
    $new_pass = (string)($data['new_password'] ?? '');

    if ($current === '' || $new_pass === '') {
        fail('Both current and new password are required');
    }
    if (strlen($new_pass) < PASSWORD_MIN_LENGTH) {
        fail('New password must be at least ' . PASSWORD_MIN_LENGTH . ' characters long');
    }
    if (strlen($new_pass) > PASSWORD_MAX_LENGTH) {           // bcrypt ignores anything after 72 bytes
        fail('New password must be at most ' . PASSWORD_MAX_LENGTH . ' characters long');
    }

    // Check the current password first
    $pc = $pdo->prepare('SELECT password_hash FROM USERS WHERE user_id = ?');
    $pc->execute([$user_id]);
    $row = $pc->fetch();

    if (!$row || !password_verify($current, $row['password_hash'])) {
        fail('Current password is incorrect');
    }
    $pdo->prepare('UPDATE USERS SET password_hash = ? WHERE user_id = ?')
        ->execute([password_hash($new_pass, PASSWORD_BCRYPT), $user_id]);

    audit($pdo, $user_id, 'password_changed', 'USER', $user_id, null);   // NFR14 - the password itself is never logged
    ok('Password updated successfully!');
} elseif (in_array($action, ['save_modules', 'save_offered', 'save_required'], true)) {

    $types = [
        'save_modules'  => 'studying',    // FR04 modules I am studying
        'save_offered'  => 'offering',    // FR05 modules I can help with
        'save_required' => 'requiring',   // FR06 modules I need help with
    ];
    $skill_type = $types[$action];

    // validate the list of ids
    $module_ids = $data['module_ids'] ?? [];
    if (!is_array($module_ids)) {
        fail('module_ids must be a list');
    }
    foreach ($module_ids as $module_id) {
        if (!is_numeric($module_id) || (int)$module_id < 1) {
            fail('Invalid module ID');
        }
    }
    $module_ids = array_values(array_unique(array_map('intval', $module_ids)));
    if (count($module_ids) > 40) {                            // same limit as skills/add.php
        fail('You can choose at most 40 modules');
    }

    // Proficiency only applies to modules you can help with (same as skills/add.php)
    $level = null;
    if ($skill_type === 'offering') {
        $level = str_in($data, 'proficiency_level', 20) ?: 'intermediate';
        if (!in_array($level, PROFICIENCY_LEVELS, true)) {
            fail('Invalid proficiency level');
        }
    }

    // every id must be a real module
    if ($module_ids) {
        $in = implode(',', array_fill(0, count($module_ids), '?'));
        $chk = $pdo->prepare("SELECT COUNT(*) FROM MODULES WHERE module_id IN ($in)");
        $chk->execute($module_ids);
        if ((int)$chk->fetchColumn() !== count($module_ids)) {
            fail('One or more modules were not found', 404);
        }
    }

    // a student cannot offer help with and need help with the same module 
    $opposite = ['offering' => 'requiring', 'requiring' => 'offering'][$skill_type] ?? null;
    if ($opposite && $module_ids) {
        $in = implode(',', array_fill(0, count($module_ids), '?'));
        $clash = $pdo->prepare("SELECT COUNT(*) FROM USER_MODULE_SKILLS WHERE user_id = ? AND skill_type = ? AND module_id IN ($in)");
        $clash->execute(array_merge([$user_id, $opposite], $module_ids));
        if ((int)$clash->fetchColumn() > 0) {
            fail('You cannot offer help with and need help with the same module', 409);
        }
    }

    // one transaction so the list never ends up half saved
    $pdo->beginTransaction();
    try {
        // What is in the list right now
        $cur = $pdo->prepare('SELECT module_id FROM USER_MODULE_SKILLS WHERE user_id = ? AND skill_type = ?');
        $cur->execute([$user_id, $skill_type]);
        $current = array_map('intval', $cur->fetchAll(PDO::FETCH_COLUMN));

        // Remove the modules that were un-ticked
        $remove = array_diff($current, $module_ids);
        if ($remove) {
            $in = implode(',', array_fill(0, count($remove), '?'));
            $pdo->prepare("DELETE FROM USER_MODULE_SKILLS WHERE user_id = ? AND skill_type = ? AND module_id IN ($in)")
                ->execute(array_merge([$user_id, $skill_type], array_values($remove)));
        }

        // Add the modules that are new
        $ins = $pdo->prepare('INSERT INTO USER_MODULE_SKILLS (user_id, module_id, skill_type, proficiency_level) VALUES (?, ?, ?, ?)');
        foreach (array_diff($module_ids, $current) as $module_id) {
            $ins->execute([$user_id, $module_id, $skill_type, $level]);
        }

        $pdo->commit();
    } catch (Throwable $e) {
        $pdo->rollBack();
        throw $e;
    }

    // Send the updated lists back (same format as skills/list.php)
    $skills = skills_for_users($pdo, [$user_id]);
    ok('Modules and skills saved successfully', ['skills' => $skills[$user_id] ?? []]);
} else {
    fail('Invalid action');
}
