package com.v2v.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.v2v.app.R;
import com.v2v.app.adapter.ConnectionsAdapter;
import com.v2v.app.model.ConnectionItem;
import com.v2v.app.util.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// FR10 - Connection requests and my connections
public class ConnectionsFragment extends BaseFragment {

    private ConnectionsAdapter adapter;
    private RecyclerView list;
    private View empty, progress;

    public ConnectionsFragment() {
        super(R.layout.fragment_connections);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        list = view.findViewById(R.id.conn_list);
        empty = view.findViewById(R.id.conn_empty);
        progress = view.findViewById(R.id.conn_progress);

        adapter = new ConnectionsAdapter(new ConnectionsAdapter.Listener() {
            @Override
            public void onOpenProfile(ConnectionItem item) {
                openPeerProfile(item.userId);
            }

            @Override
            public void onAccept(ConnectionItem item) {
                respond(item, "accept");
            }

            @Override
            public void onDecline(ConnectionItem item) {
                respond(item, "decline");
            }

            @Override
            public void onMessage(ConnectionItem item) {
                openChatWith(item.userId, item.fullName);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private static List<ConnectionItem> parse(JSONObject json, String key) {
        List<ConnectionItem> out = new ArrayList<>();
        JSONArray arr = json.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null)
                    out.add(ConnectionItem.fromJson(o));
            }
        }
        return out;
    }

    // GET connections/list.php and split the result into the three sections.
    private void load() {
        show(progress, adapter.getItemCount() == 0);
        api().get("connections/list.php", null, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!alive())
                    return;
                show(progress, false);
                List<ConnectionItem> incoming = parse(json, "incoming");
                List<ConnectionItem> outgoing = parse(json, "outgoing");
                List<ConnectionItem> connected = parse(json, "connected");
                adapter.setData(incoming, outgoing, connected);

                boolean nothing = incoming.isEmpty() && outgoing.isEmpty() && connected.isEmpty();
                show(empty, nothing);
                show(list, !nothing);
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

    // POST connections/respond.php - accept or decline a request I received.
    private void respond(ConnectionItem item, String action) {
        api().post("connections/respond.php", body("connection_id", item.connectionId, "action", action),
                new ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        if (!alive())
                            return;
                        toast(json.optString("message"));
                        load();
                    }

                    @Override
                    public void onError(String message) {
                        if (!alive())
                            return;
                        toast(message);
                        load();
                    }
                });
    }
}
