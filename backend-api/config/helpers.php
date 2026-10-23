<?php


date_default_timezone_set('Africa/Johannesburg');
ini_set('display_errors', '0');

const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 72;   // bcrypt only uses the first 72 bytes
const SKILL_TYPES         = ['studying', 'offering', 'requiring'];
const PROFICIENCY_LEVELS  = ['beginner', 'intermediate', 'advanced'];
const VISIBILITY_OPTIONS  = ['public', 'connections_only', 'private'];

// ENVIRONMENT / OUTPUT HELPERS
function env_value(string $key, $default = null)
{
    $v = getenv($key);
    return ($v === false || $v === '') ? $default : $v;
}

function is_debug(): bool
{
    return env_value('APP_DEBUG') === 'true';
}

// Send a JSON response and stop.
function json_out(array $payload, int $status = 200): void
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function ok(string $message = '', array $extra = [], int $status = 200): void
{
    json_out(array_merge(['success' => true, 'message' => $message], $extra), $status);
}

function fail(string $message, int $status = 400, array $extra = []): void
{
    json_out(array_merge(['success' => false, 'message' => $message], $extra), $status);
}

// Call first in every endpoint: sets headers, handles CORS pre-flight, enforces the HTTP method.
function start_api(string $method): void
{
    header('Content-Type: application/json; charset=utf-8');
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Auth-Token');

    if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') {
        http_response_code(204);
        exit;
    }
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== $method) {
        fail('Method not allowed', 405);
    }
}

// Read and decode the JSON request body (empty body = empty array).
function read_json(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') {
        return [];
    }
    $data = json_decode($raw, true);
    if (!is_array($data)) {
        fail('Invalid JSON body', 400);
    }
    return $data;
}

// Trimmed string from an array (works even if the client sent a number).
function str_in(array $data, string $key, int $maxLen = 255): string
{
    $v = $data[$key] ?? '';
    if (!is_scalar($v)) {
        return '';
    }
    $v = trim((string)$v);
    return mb_substr($v, 0, $maxLen);
}

function int_in(array $data, string $key): int
{
    $v = $data[$key] ?? 0;
    return is_numeric($v) ? (int)$v : 0;
}

// Escape % and _ so user text is matched literally inside LIKE.
function like_escape(string $s): string
{
    return str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], $s);
}


// JWT (LOGIN TOKENS)
// Own small HS256 implementation so we don't need Composer
function b64url_encode(string $data): string
{
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

function b64url_decode(string $data)
{
    $pad = strlen($data) % 4;
    if ($pad) {
        $data .= str_repeat('=', 4 - $pad);
    }
    return base64_decode(strtr($data, '-_', '+/'), true);
}

function jwt_secret(): string
{
    $secret = env_value('JWT_SECRET');
    if ($secret) {
        return (string)$secret;
    }
    if (is_debug()) {
        return 'dev-only-insecure-secret-change-me';  
    }
    fail('Server is not configured correctly (JWT_SECRET is missing).', 500);
    return '';
}

function jwt_create(int $userId, string $role): array
{
    $ttlHours = (int)env_value('JWT_TTL_HOURS', 168);
    $now = time();
    $claims = [
        'iss'  => 'v2v',
        'sub'  => $userId,
        'role' => $role,
        'jti'  => bin2hex(random_bytes(16)),
        'iat'  => $now,
        'exp'  => $now + max(1, $ttlHours) * 3600,
    ];
    $header  = b64url_encode(json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
    $payload = b64url_encode(json_encode($claims));
    $sig     = b64url_encode(hash_hmac('sha256', "$header.$payload", jwt_secret(), true));

    return ['token' => "$header.$payload.$sig", 'jti' => $claims['jti'], 'exp' => $claims['exp']];
}

// Returns the claims if the token is genuine and not expired, otherwise null.
function jwt_verify(string $token): ?array
{
    $parts = explode('.', $token);
    if (count($parts) !== 3) {
        return null;
    }
    [$h, $p, $s] = $parts;

    $header = json_decode((string)b64url_decode($h), true);
    if (!is_array($header) || ($header['alg'] ?? '') !== 'HS256') {
        return null;
    }
    $expected = b64url_encode(hash_hmac('sha256', "$h.$p", jwt_secret(), true));
    if (!hash_equals($expected, $s)) {
        return null;
    }
    $claims = json_decode((string)b64url_decode($p), true);
    if (!is_array($claims) || !isset($claims['sub'], $claims['exp'], $claims['jti'])) {
        return null;
    }
    if ((int)$claims['exp'] < time()) {
        return null;
    }
    return $claims;
}

function bearer_token(): ?string
{
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? null;

    if (!$h && function_exists('getallheaders')) {
        foreach (getallheaders() as $name => $value) {
            if (strtolower($name) === 'authorization') {
                $h = $value;
                break;
            }
        }
    }
    if ($h && preg_match('/^Bearer\s+(.+)$/i', trim($h), $m)) {
        return trim($m[1]);
    }
    $x = $_SERVER['HTTP_X_AUTH_TOKEN'] ?? null;
    return $x ? trim($x) : null;
}

// Every protected endpoint calls this. Returns the logged in user
function require_auth(PDO $pdo): array
{
    $token = bearer_token();
    if (!$token) {
        fail('Authentication required', 401);
    }
    $claims = jwt_verify($token);
    if (!$claims) {
        fail('Your session has expired. Please log in again.', 401);
    }

    $rev = $pdo->prepare('SELECT 1 FROM REVOKED_TOKENS WHERE jti = ?');
    $rev->execute([$claims['jti']]);
    if ($rev->fetchColumn()) {
        fail('You have been logged out. Please log in again.', 401);
    }

    $st = $pdo->prepare(
        'SELECT user_id, student_number, full_name, email, phone_number, campus_location, aggregate_rating,
                user_role, account_status, bio, profile_photo_url, profile_visibility
         FROM USERS WHERE user_id = ?'
    );
    $st->execute([(int)$claims['sub']]);
    $user = $st->fetch();

    if (!$user) {
        fail('Account not found. Please log in again.', 401);
    }
    if ($user['account_status'] !== 'active') {
        fail('Your account is not active. Please contact support.', 403, ['account_inactive' => true]);
    }
    $user['user_id']   = (int)$user['user_id'];
    $user['jti']       = $claims['jti'];
    $user['token_exp'] = (int)$claims['exp'];
    return $user;
}

// LOGGING / NOTIFICATIONS
// NFR14 - record important events. Never put passwords or tokens in $description.
function audit(PDO $pdo, ?int $userId, string $event, string $entityType = 'USER', ?int $entityId = null, ?string $description = null): void
{
    try {
        $st = $pdo->prepare(
            'INSERT INTO AUDIT_EVENTS (user_id, event_type, entity_type, entity_id, event_description) VALUES (?, ?, ?, ?, ?)'
        );
        $st->execute([$userId, $event, $entityType, $entityId, $description]);
    } catch (Throwable $e) {
        error_log('V2V audit failed: ' . $e->getMessage());
    }
}

// Counts recent audit events (used for brute force protection)
function recent_events(PDO $pdo, string $event, int $userId, int $minutes): int
{
    $minutes = max(1, $minutes);
    $st = $pdo->prepare(
        "SELECT COUNT(*) FROM AUDIT_EVENTS
         WHERE event_type = ? AND user_id = ? AND created_at > (NOW() - INTERVAL $minutes MINUTE)"
    );
    $st->execute([$event, $userId]);
    return (int)$st->fetchColumn();
}

// Saves notification for FR17. $type has to be one of the NOTIFICATIONS enum values
function notify(PDO $pdo, int $userId, string $type, string $message): void
{
    try {
        $st = $pdo->prepare('INSERT INTO NOTIFICATIONS (user_id, notification_title, notification_message) VALUES (?, ?, ?)');
        $st->execute([$userId, $type, $message]);
    } catch (Throwable $e) {
        error_log('V2V notify failed: ' . $e->getMessage());
    }
}

// CONNECTIONS
// The single connection roww between two users (either direction), or null.
function find_connection(PDO $pdo, int $a, int $b): ?array
{
    $st = $pdo->prepare(
        'SELECT * FROM CONNECTION_REQUESTS
         WHERE (connection_sender_id = ? AND connection_receiver_id = ?)
            OR (connection_sender_id = ? AND connection_receiver_id = ?)
         ORDER BY connection_id DESC LIMIT 1'
    );
    $st->execute([$a, $b, $b, $a]);
    $row = $st->fetch();
    return $row ?: null;
}

function are_connected(PDO $pdo, int $a, int $b): bool
{
    $c = find_connection($pdo, $a, $b);
    return $c !== null && $c['connection_status'] === 'accepted';
}

// Describes relationship from $me's point of view: none | pending_sent | pending_received | connected
function relation_of(?array $conn, int $me): array
{
    if ($conn === null || $conn['connection_status'] === 'declined') {
        return ['relation' => 'none', 'connection_id' => null];
    }
    $id = (int)$conn['connection_id'];
    if ($conn['connection_status'] === 'accepted') {
        return ['relation' => 'connected', 'connection_id' => $id];
    }
    return [
        'relation'      => ((int)$conn['connection_sender_id'] === $me) ? 'pending_sent' : 'pending_received',
        'connection_id' => $id,
    ];
}

// CONVERSATIONS
// Finds (or creates) the private conversation between two users and returns its id.
function get_or_create_direct_conversation(PDO $pdo, int $a, int $b): int
{
    $st = $pdo->prepare(
        "SELECT c.conversation_id
         FROM CONVERSATIONS c
         JOIN CONVERSATION_MEMBERS m1 ON m1.conversation_id = c.conversation_id AND m1.user_id = ?
         JOIN CONVERSATION_MEMBERS m2 ON m2.conversation_id = c.conversation_id AND m2.user_id = ?
         WHERE c.conversation_type = 'direct'
         LIMIT 1"
    );
    $st->execute([$a, $b]);
    $existing = $st->fetchColumn();
    if ($existing) {
        return (int)$existing;
    }

    $startedHere = !$pdo->inTransaction();
    if ($startedHere) {
        $pdo->beginTransaction();
    }
    try {
        $pdo->exec("INSERT INTO CONVERSATIONS (conversation_type) VALUES ('direct')");
        $cid = (int)$pdo->lastInsertId();
        $mem = $pdo->prepare('INSERT INTO CONVERSATION_MEMBERS (conversation_id, user_id) VALUES (?, ?), (?, ?)');
        $mem->execute([$cid, $a, $cid, $b]);
        if ($startedHere) {
            $pdo->commit();
        }
        return $cid;
    } catch (Throwable $e) {
        if ($startedHere && $pdo->inTransaction()) {
            $pdo->rollBack();
        }
        throw $e;
    }
}

// SKILLS / PEER CARDS
function skill_row_out(array $r): array
{
    return [
        'skill_id'          => (int)$r['skill_id'],
        'module_id'         => (int)$r['module_id'],
        'module_code'       => $r['module_code'],
        'module_name'       => $r['module_name'],
        'skill_type'        => $r['skill_type'],
        'proficiency_level' => $r['proficiency_level'],
    ];
}

function skills_for_users(PDO $pdo, array $userIds): array
{
    $out = [];
    if (!$userIds) {
        return $out;
    }
    $in = implode(',', array_fill(0, count($userIds), '?'));
    $st = $pdo->prepare(
        "SELECT s.skill_id, s.user_id, s.module_id, s.skill_type, s.proficiency_level, m.module_code, m.module_name
         FROM USER_MODULE_SKILLS s
         JOIN MODULES m ON m.module_id = s.module_id
         WHERE s.user_id IN ($in)
         ORDER BY FIELD(s.skill_type, 'offering', 'requiring', 'studying'), m.module_code"
    );
    $st->execute(array_values($userIds));
    foreach ($st->fetchAll() as $r) {
        $out[(int)$r['user_id']][] = skill_row_out($r);
    }
    return $out;
}

// Builds the peer cards for search and matches
// Only public, active students (NFR05)
function build_peer_cards(PDO $pdo, int $me, array $userIds, array $extraByUser = []): array
{
    if (!$userIds) {
        return [];
    }
    $userIds = array_values(array_unique(array_map('intval', $userIds)));
    $in = implode(',', array_fill(0, count($userIds), '?'));

    $st = $pdo->prepare(
        "SELECT user_id, full_name, campus_location, aggregate_rating FROM USERS WHERE user_id IN ($in)"
    );
    $st->execute($userIds);
    $users = [];
    foreach ($st->fetchAll() as $u) {
        $users[(int)$u['user_id']] = $u;
    }

    // relationships between myself and each of these users
    $rel = [];
    $cs = $pdo->prepare(
        "SELECT * FROM CONNECTION_REQUESTS
         WHERE (connection_sender_id = ? AND connection_receiver_id IN ($in))
            OR (connection_receiver_id = ? AND connection_sender_id IN ($in))
         ORDER BY connection_id ASC"
    );
    $cs->execute(array_merge([$me], $userIds, [$me], $userIds));
    foreach ($cs->fetchAll() as $c) {
        $other = ((int)$c['connection_sender_id'] === $me) ? (int)$c['connection_receiver_id'] : (int)$c['connection_sender_id'];
        $rel[$other] = $c;   // later rows overwrite earlier ones, so the newest wins
    }

    $skills = skills_for_users($pdo, $userIds);

    $cards = [];
    foreach ($userIds as $id) {
        if (!isset($users[$id])) {
            continue;
        }
        $u = $users[$id];
        $r = relation_of($rel[$id] ?? null, $me);
        $card = [
            'user_id'         => $id,
            'full_name'       => $u['full_name'],
            'campus_location' => $u['campus_location'],
            'aggregate_rating' => $u['aggregate_rating'] !== null ? (float)$u['aggregate_rating'] : null,
            'relation'        => $r['relation'],
            'connection_id'   => $r['connection_id'],
            'skills'          => $skills[$id] ?? [],
        ];
        $cards[] = array_merge($card, $extraByUser[$id] ?? []);
    }
    return $cards;
}

// Counts accepted connections for a user.
function connection_count(PDO $pdo, int $userId): int
{
    $st = $pdo->prepare(
        "SELECT COUNT(*) FROM CONNECTION_REQUESTS
         WHERE connection_status = 'accepted' AND (connection_sender_id = ? OR connection_receiver_id = ?)"
    );
    $st->execute([$userId, $userId]);
    return (int)$st->fetchColumn();
}

// MESSAGING ACCESS CONTROL
// Checks I'm in the conversation. For direct chats also returns the other student
// and whether messaging is allowed (still connected, account active)
// Non members just get not found
function require_conversation_member(PDO $pdo, int $conversationId, int $userId): array
{
    $st = $pdo->prepare(
        'SELECT c.conversation_id, c.conversation_type, c.group_id
         FROM CONVERSATIONS c
         JOIN CONVERSATION_MEMBERS cm ON cm.conversation_id = c.conversation_id
         WHERE c.conversation_id = ? AND cm.user_id = ?'
    );
    $st->execute([$conversationId, $userId]);
    $conv = $st->fetch();
    if (!$conv) {
        fail('Conversation not found', 404);
    }

    $conv['conversation_id'] = (int)$conv['conversation_id'];
    $conv['other_user']      = null;
    $conv['can_send']        = false;

    if ($conv['conversation_type'] === 'direct') {
        $o = $pdo->prepare(
            'SELECT u.user_id, u.full_name, u.account_status
             FROM CONVERSATION_MEMBERS cm JOIN USERS u ON u.user_id = cm.user_id
             WHERE cm.conversation_id = ? AND cm.user_id <> ? LIMIT 1'
        );
        $o->execute([$conversationId, $userId]);
        $other = $o->fetch();
        if ($other) {
            $conv['other_user'] = ['user_id' => (int)$other['user_id'], 'full_name' => $other['full_name']];
            $conv['can_send']   = ($other['account_status'] === 'active') && are_connected($pdo, $userId, (int)$other['user_id']);
        }
    }
    return $conv;
}

// STUDY GROUPS
const GROUP_MIN_MEMBERS = 2;
const GROUP_MAX_MEMBERS = 30;

function group_row_out(array $r): array
{
    $count = (int)$r['member_count'];
    $max   = (int)$r['max_members'];
    return [
        'group_id'     => (int)$r['group_id'],
        'group_name'   => $r['group_name'],
        'description'  => $r['description'],
        'module_id'    => (int)$r['module_id'],
        'module_code'  => $r['module_code'],
        'module_name'  => $r['module_name'],
        'creator_id'   => (int)$r['creator_id'],
        'creator_name' => $r['creator_name'],
        'member_count' => $count,
        'max_members'  => $max,
        'is_full'      => $count >= $max,
        'is_member'    => ((int)$r['is_member']) > 0,
    ];
}

// SQL used by list/get to describe a group from the point of view of user :me
function group_select_sql(): string
{
    return "SELECT g.group_id, g.group_name, g.description, g.max_members, g.created_at,
                   m.module_id, m.module_code, m.module_name,
                   u.user_id AS creator_id, u.full_name AS creator_name,
                   (SELECT COUNT(*) FROM GROUP_MEMBERS gm  WHERE gm.group_id  = g.group_id) AS member_count,
                   (SELECT COUNT(*) FROM GROUP_MEMBERS gm2 WHERE gm2.group_id = g.group_id AND gm2.user_id = :me) AS is_member
            FROM STUDY_GROUPS g
            JOIN MODULES m ON m.module_id = g.module_id
            JOIN USERS u   ON u.user_id   = g.creator_id ";
}

function fetch_group(PDO $pdo, int $groupId, int $me): ?array
{
    $st = $pdo->prepare(group_select_sql() . ' WHERE g.group_id = :gid');
    $st->execute([':me' => $me, ':gid' => $groupId]);
    $row = $st->fetch();
    return $row ? group_row_out($row) : null;
}
