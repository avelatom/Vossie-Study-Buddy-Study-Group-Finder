<?php
// FR16 - peer feedback
require_once __DIR__ . '/../config/bootstrap.php';
start_api('POST');

$me   = require_auth($pdo);
$meId = $me['user_id'];
$data = read_json();


// VALIDATE THE INPUT

$reviewee_id = int_in($data, 'reviewee_id');
$rating      = $data['rating'] ?? null;
$comment     = str_in($data, 'comment', 1000);

if ($reviewee_id <= 0) {
    fail('Please choose the student you are giving feedback to');
}
if ($reviewee_id === $meId) {
    fail('You cannot give feedback to yourself');
}
// REVIEWS.rating_score is a whole number from 1 to 5 (check_rating_range in schema.sql)
if (!is_numeric($rating) || (float)$rating != (int)$rating || (int)$rating < 1 || (int)$rating > 5) {
    fail('Please choose a rating from 1 to 5 stars');
}
$rating = (int)$rating;
if (mb_strlen($comment) > 500) {
    fail('Feedback must be 500 characters or fewer');
}


// CHECK WHO IS BEING REVIEWED

// Must be an active student account
$st = $pdo->prepare("SELECT user_id FROM USERS WHERE user_id = ? AND account_status = 'active' AND user_role = 'student'");
$st->execute([$reviewee_id]);
if (!$st->fetchColumn()) {
    fail('Student not found', 404);
}

// Only connected students can rate each other (stops random/fake ratings)
if (!are_connected($pdo, $meId, $reviewee_id)) {
    fail('You can only give feedback to students you are connected with', 403);
}


// SAVE THE REVIEW (insert the first time, update after that)

$pdo->beginTransaction();
try {
    $find = $pdo->prepare('SELECT review_id FROM REVIEWS WHERE reviewer_id = ? AND reviewee_id = ? AND session_id IS NULL LIMIT 1');
    $find->execute([$meId, $reviewee_id]);
    $existing = $find->fetchColumn();

    if ($existing) {
        $pdo->prepare('UPDATE REVIEWS SET rating_score = ?, feedback_text = ?, created_at = NOW() WHERE review_id = ?')
            ->execute([$rating, $comment !== '' ? $comment : null, $existing]);
    } else {
        $pdo->prepare('INSERT INTO REVIEWS (session_id, reviewer_id, reviewee_id, rating_score, feedback_text) VALUES (NULL, ?, ?, ?, ?)')
            ->execute([$meId, $reviewee_id, $rating, $comment !== '' ? $comment : null]);
    }

    // Recalculate their average rating
    $pdo->prepare('UPDATE USERS SET aggregate_rating = (SELECT ROUND(AVG(rating_score), 2) FROM REVIEWS WHERE reviewee_id = ?) WHERE user_id = ?')
        ->execute([$reviewee_id, $reviewee_id]);

    $pdo->commit();
} catch (Throwable $e) {
    $pdo->rollBack();
    throw $e;                                     // bootstrap.php turns this into a clean 500 JSON response
}


// RESPOND

// New average + number of reviews for the app
$sum = $pdo->prepare('SELECT ROUND(AVG(rating_score), 2) AS avg_rating, COUNT(*) AS n FROM REVIEWS WHERE reviewee_id = ?');
$sum->execute([$reviewee_id]);
$s = $sum->fetch();

// NFR14 - log it. No notification, NOTIFICATIONS doesn't have a feedback type in the ERD
audit($pdo, $meId, $existing ? 'feedback_updated' : 'feedback_given', 'USER', $reviewee_id, null);

ok($existing ? 'Your feedback was updated' : 'Thank you for your feedback!', [
    'review'           => ['reviewee_id' => $reviewee_id, 'rating' => $rating, 'comment' => $comment !== '' ? $comment : null],
    'aggregate_rating' => $s['avg_rating'] !== null ? (float)$s['avg_rating'] : null,
    'review_count'     => (int)$s['n'],
], $existing ? 200 : 201);
