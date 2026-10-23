package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.v2v.app.R;
import com.v2v.app.util.ApiCallback;

import org.json.JSONObject;

// FR16 - peer feedback
// Opened from a connected student's profile. 1-5 stars + optional comment
// Sending again updates the old feedback
public class FeedbackFragment extends BaseFragment {

    private int userId;
    private RatingBar ratingBar;
    private TextView ratingLabel;
    private TextInputEditText commentInput;
    private MaterialButton submitButton;
    private View progress;

    // Text shown under the stars
    private static final String[] RATING_WORDS = {
            "Tap a star to rate", "Not helpful", "Could be better", "Helpful", "Very helpful", "Excellent study partner"
    };

    public FeedbackFragment() {
        super(R.layout.fragment_feedback);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        userId = args.getInt("user_id");
        String name = args.getString("name", "this student");

        ratingBar = view.findViewById(R.id.feedback_rating);
        ratingLabel = view.findViewById(R.id.feedback_rating_label);
        commentInput = view.findViewById(R.id.feedback_comment);
        submitButton = view.findViewById(R.id.feedback_submit);
        progress = view.findViewById(R.id.feedback_progress);

        ((TextView) view.findViewById(R.id.feedback_title)).setText("How was studying with " + name + "?");

        // Show the old feedback if there is one
        int previous = args.getInt("my_rating", 0);
        if (previous > 0) {
            ratingBar.setRating(previous);
            commentInput.setText(args.getString("my_comment", ""));
            submitButton.setText("Update feedback");
        }
        updateLabel(previous);

        ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> updateLabel(Math.round(rating)));
        submitButton.setOnClickListener(v -> submit());
    }

    private void updateLabel(int stars) {
        ratingLabel.setText(RATING_WORDS[Math.max(0, Math.min(5, stars))]);
    }

    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        show(progress, busy);
    }

    private void submit() {
        hideKeyboard();
        int stars = Math.round(ratingBar.getRating());
        if (stars < 1) {
            toast("Please choose a star rating");
            return;
        }

        setBusy(true);
        api().post("feedback/feedback.php",
                body("reviewee_id", userId, "rating", stars, "comment", text(commentInput)),
                new ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        if (!alive())
                            return;
                        setBusy(false);
                        toast(json.optString("message"));
                        nav().popBackStack(); // back to the profile, which reloads the new average
                    }

                    @Override
                    public void onError(String message) {
                        if (!alive())
                            return;
                        setBusy(false);
                        toast(message);
                    }
                });
    }
}
