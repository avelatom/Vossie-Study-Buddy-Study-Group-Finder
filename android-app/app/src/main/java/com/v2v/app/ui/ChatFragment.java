package com.v2v.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.v2v.app.R;
import com.v2v.app.adapter.MessageAdapter;
import com.v2v.app.model.ChatMessage;
import com.v2v.app.util.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// FR13 - one to one chat with a connected student
// Checks for new messages every few seconds while the chat is open (polling)
public class ChatFragment extends BaseFragment {

    private static final long POLL_MS = 4000;

    private int conversationId;
    private RecyclerView list;
    private LinearLayoutManager layoutManager;
    private MessageAdapter adapter;
    private EditText input;
    private MaterialButton sendButton;
    private View inputRow, notice, empty, progress;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loading = false;
    private boolean refreshAgain = false;
    private boolean firstLoadDone = false;
    private boolean resumed = false;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            fetch();
        }
    };

    public ChatFragment() {
        super(R.layout.fragment_chat);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firstLoadDone = false;          // the view is new, so the list must be loaded again
        loading = false;
        refreshAgain = false;

        Bundle args = getArguments();
        conversationId = args == null ? 0 : args.getInt("conversation_id");
        String title = args == null ? null : args.getString("title");
        if (title != null) {
            main().setToolbarTitle(title);
        }

        list = view.findViewById(R.id.chat_list);
        input = view.findViewById(R.id.chat_input);
        sendButton = view.findViewById(R.id.chat_send);
        inputRow = view.findViewById(R.id.chat_input_row);
        notice = view.findViewById(R.id.chat_notice);
        empty = view.findViewById(R.id.chat_empty);
        progress = view.findViewById(R.id.chat_progress);

        adapter = new MessageAdapter();
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);

        sendButton.setOnClickListener(v -> send());
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        show(progress, !firstLoadDone);
        fetch();
    }

    @Override
    public void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(pollTask);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(pollTask);
        super.onDestroyView();
    }

    // Next check in POLL_MS, only while the chat is on screen
    private void scheduleNextPoll() {
        handler.removeCallbacks(pollTask);
        if (resumed) {
            handler.postDelayed(pollTask, POLL_MS);
        }
    }

    // First time loads the latest messages, after that only new ones
    private void fetch() {
        if (loading) {
            refreshAgain = true;
            return;
        }
        loading = true;

        Map<String, String> params = new HashMap<>();
        params.put("conversation_id", String.valueOf(conversationId));
        if (firstLoadDone && adapter.lastId() > 0) {
            params.put("after_id", String.valueOf(adapter.lastId()));
        }

        api().get("messages/list.php", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                loading = false;
                if (!alive()) return;
                show(progress, false);

                List<ChatMessage> msgs = new ArrayList<>();
                JSONArray arr = json.optJSONArray("messages");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) msgs.add(ChatMessage.fromJson(o));
                    }
                }

                if (!firstLoadDone) {
                    adapter.setItems(msgs);
                    firstLoadDone = true;
                    scrollToBottom();
                } else if (adapter.append(msgs) > 0) {
                    scrollToBottom();
                }

                show(empty, adapter.getItemCount() == 0);

                boolean canSend = json.optBoolean("can_send", false);
                show(inputRow, canSend);
                show(notice, !canSend);

                finishFetch();
            }

            @Override
            public void onError(String message) {
                loading = false;
                if (!alive()) return;
                show(progress, false);
                if (!firstLoadDone) {
                    toast(message);        // do not spam the user while polling in the background
                }
                finishFetch();
            }
        });
    }

    // Fetch again if one was asked for while loading, otherwise wait for the next poll
    private void finishFetch() {
        if (refreshAgain) {
            refreshAgain = false;
            fetch();
        } else {
            scheduleNextPoll();
        }
    }

    // Shows the newest message.
    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            list.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    // Send, then fetch so it shows in the right order
    private void send() {
        final String outgoing = text(input);
        if (outgoing.isEmpty()) {
            return;
        }
        sendButton.setEnabled(false);

        api().post("messages/send.php", body("conversation_id", conversationId, "message_text", outgoing), new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive()) return;
                sendButton.setEnabled(true);
                input.setText("");
                fetch();                   // reloads from the server so the order is always correct
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                sendButton.setEnabled(true);
                toast(message);
            }
        });
    }
}
