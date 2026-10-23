<?php
// FR08 - peer matching, best match first
// GET, needs token
// Scoring:
//   +10 they offer help with a module I need (+1 intermediate, +2 advanced)
//   +5  they study a module I need
//   +4  they need help with a module I offer
//   +1  same campus (just a tie-breaker)
// If it works both ways it's flagged as a "skill swap"
require_once __DIR__ . '/../config/bootstrap.php';
start_api('GET');

// Logged in student (from the token)
$me   = require_auth($pdo);
$meId = $me['user_id'];

// ---- my own requirements / offers ----
$mine = $pdo->prepare("SELECT module_id, skill_type FROM USER_MODULE_SKILLS WHERE user_id = ? AND skill_type IN ('requiring','offering')");
$mine->execute([$meId]);
$myRequired = [];
$myOffered  = [];
foreach ($mine->fetchAll() as $r) {
    if ($r['skill_type'] === 'requiring') {
        $myRequired[] = (int)$r['module_id'];
    } else {
        $myOffered[] = (int)$r['module_id'];
    }
}

if (!$myRequired && !$myOffered) {
    ok('Add the modules you need help with to see suggested peers.', ['peers' => [], 'needs_requirements' => true]);
}

// Shared FROM/WHERE - other active students with public profiles only (NFR05)
$base = " FROM USER_MODULE_SKILLS s
          JOIN USERS u   ON u.user_id   = s.user_id
          JOIN MODULES m ON m.module_id = s.module_id
          WHERE u.user_id <> ? AND u.account_status = 'active' AND u.user_role = 'student'
            AND u.profile_visibility = 'public' ";

$score   = [];     // user_id => points
$reasons = [];     // user_id => list of text reasons
$swapA   = [];     // user_id => true when they can help me
$swapB   = [];     // user_id => true when I can help them

// Adds a "why matched" line once per student (no duplicates)
$addReason = function (int $uid, string $text) use (&$reasons) {
    if (!isset($reasons[$uid]) || !in_array($text, $reasons[$uid], true)) {
        $reasons[$uid][] = $text;
    }
};

// ---- A: students who OFFER or STUDY a module I NEED ----
if ($myRequired) {
    $in = implode(',', array_fill(0, count($myRequired), '?'));
    $st = $pdo->prepare(
        "SELECT s.user_id, s.skill_type, s.proficiency_level, m.module_code" . $base .
        " AND s.module_id IN ($in) AND s.skill_type IN ('offering','studying')"
    );
    $st->execute(array_merge([$meId], $myRequired));
    foreach ($st->fetchAll() as $r) {
        $uid = (int)$r['user_id'];
        if ($r['skill_type'] === 'offering') {
            $pts = 10;
            if ($r['proficiency_level'] === 'advanced') {
                $pts += 2;
            } elseif ($r['proficiency_level'] === 'intermediate') {
                $pts += 1;
            }
            $swapA[$uid] = true;
            $addReason($uid, 'Can help with ' . $r['module_code']);
        } else {
            $pts = 5;
            $addReason($uid, 'Studies ' . $r['module_code']);
        }
        $score[$uid] = ($score[$uid] ?? 0) + $pts;
    }
}

// ---- B: students who NEED a module I OFFER ----
if ($myOffered) {
    $in = implode(',', array_fill(0, count($myOffered), '?'));
    $st = $pdo->prepare(
        "SELECT s.user_id, m.module_code" . $base .
        " AND s.module_id IN ($in) AND s.skill_type = 'requiring'"
    );
    $st->execute(array_merge([$meId], $myOffered));
    foreach ($st->fetchAll() as $r) {
        $uid = (int)$r['user_id'];
        $swapB[$uid] = true;
        $score[$uid] = ($score[$uid] ?? 0) + 4;
        $addReason($uid, 'Needs help with ' . $r['module_code'] . ' (you can help)');
    }
}

if (!$score) {
    ok('No matches yet. Try adding more modules, or use Search.', ['peers' => [], 'needs_requirements' => !$myRequired]);
}

// ---- campus tie-breaker ----
$ids = array_keys($score);
$in  = implode(',', array_fill(0, count($ids), '?'));
$cs  = $pdo->prepare("SELECT user_id FROM USERS WHERE campus_location = ? AND user_id IN ($in)");
$cs->execute(array_merge([$me['campus_location']], $ids));
foreach ($cs->fetchAll(PDO::FETCH_COLUMN) as $uid) {
    $score[(int)$uid] += 1;
}

// ---- top 30 by score, turned into peer cards ----
arsort($score);                                   // best score first
$ids = array_slice(array_keys($score), 0, 30);

$extra = [];
foreach ($ids as $uid) {
    $extra[$uid] = [
        'score'      => (int)$score[$uid],
        'reasons'    => $reasons[$uid] ?? [],
        'skill_swap' => !empty($swapA[$uid]) && !empty($swapB[$uid]),
    ];
}
$cards = build_peer_cards($pdo, $meId, $ids, $extra);

// Sort again just to be safe (score, then name)
usort($cards, function ($a, $b) {
    return [$b['score'], $a['full_name']] <=> [$a['score'], $b['full_name']];
});

ok('', ['peers' => $cards, 'needs_requirements' => !$myRequired]);
