package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.v2v.app.R;
import com.v2v.app.adapter.ConversationAdapter;
import com.v2v.app.model.ConversationItem;
import com.v2v.app.util.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// FR13 - List of conversations
public class MessagesFragment extends BaseFragment {

    private ConversationAdapter adapter;
    private RecyclerView list;
    private View empty, progress;

    public MessagesFragment() {
        super(R.layout.fragment_messages);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        list = view.findViewById(R.id.msg_list);
        empty = view.findViewById(R.id.msg_empty);
        progress = view.findViewById(R.id.msg_progress);

        adapter = new ConversationAdapter(item -> {
            Bundle args = new Bundle();
            args.putInt("conversation_id", item.conversationId);
            args.putString("title", item.otherName);
            nav().navigate(R.id.chatFragment, args);
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    // GET messages/conversations.php - my chats, newest activity first.
    private void load() {
        show(progress, adapter.getItemCount() == 0);
        api().get("messages/conversations.php", null, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);

                List<ConversationItem> items = new ArrayList<>();
                JSONArray arr = json.optJSONArray("conversations");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null)
                            items.add(ConversationItem.fromJson(o));
                    }
                }
                adapter.setItems(items);
                show(empty, items.isEmpty());
                show(list, !items.isEmpty());
            }

            @Override
            public void onError(String message) {
                if (!alive())
                    return;
                show(progress, false);
                toast(message);
            }
        });
    }
}
