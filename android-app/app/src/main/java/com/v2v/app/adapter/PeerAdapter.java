package com.v2v.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.v2v.app.R;
import com.v2v.app.model.PeerCard;
import com.v2v.app.util.Avatar;
import com.v2v.app.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Student cards for search results and matches
public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.VH> {

    // Card tapped -> open their profile
    public interface Listener {
        void onPeerClick(PeerCard peer);
    }

    private final List<PeerCard> items = new ArrayList<>();
    private final Listener listener;

    public PeerAdapter(Listener listener) {
        this.listener = listener;
    }

    // Replace the whole list
    public void setItems(List<PeerCard> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_peer, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final PeerCard p = items.get(position);

        // avatar, name, campus and rating
        Avatar.bind(h.avatar, p.fullName, p.userId);
        h.name.setText(p.fullName);
        String campus = p.campus;
        if (p.rating >= 0) { // -1 = no reviews yet
            campus = campus + "   ★ " + String.format(Locale.US, "%.1f", p.rating);
        }
        h.campus.setText(campus);

        // relationship chip (Connected / Request sent / Wants to connect)
        Ui.relationChip(h.relation, p.relation);

        // FR08: both students can help each other 
        h.swap.setVisibility(p.skillSwap ? View.VISIBLE : View.GONE);

        // FR08: "why matched" reasons, one per line with a tick
        if (!p.reasons.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String r : p.reasons) {
                if (sb.length() > 0)
                    sb.append("\n");
                sb.append("✓  ").append(r);
            }
            h.reasons.setText(sb.toString());
            h.reasons.setVisibility(View.VISIBLE);
        } else {
            h.reasons.setVisibility(View.GONE);
        }

        // modules this student can help with / needs help with (module codes only)
        String offers = p.summary("offering");
        String needs = p.summary("requiring");
        h.offers.setVisibility(offers.isEmpty() ? View.GONE : View.VISIBLE);
        h.offers.setText(Ui.labelled(h.offers, "Can help with", offers));
        h.needs.setVisibility(needs.isEmpty() ? View.GONE : View.VISIBLE);
        h.needs.setText(Ui.labelled(h.needs, "Needs help with", needs));

        h.itemView.setOnClickListener(view -> listener.onPeerClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Holds the views for one card
    static class VH extends RecyclerView.ViewHolder {
        final TextView avatar, name, campus, swap, reasons, offers, needs, relation;

        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.peer_item_avatar);
            name = v.findViewById(R.id.peer_item_name);
            campus = v.findViewById(R.id.peer_item_campus);
            swap = v.findViewById(R.id.peer_item_swap);
            reasons = v.findViewById(R.id.peer_item_reasons);
            offers = v.findViewById(R.id.peer_item_offers);
            needs = v.findViewById(R.id.peer_item_needs);
            relation = v.findViewById(R.id.peer_item_relation);
        }
    }
}
