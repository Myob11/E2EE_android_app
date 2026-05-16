package com.example.myapplication;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Base64;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.util.Prefs;
import com.example.myapplication.util.ProfileUtils;
import com.example.myapplication.util.SignalManager;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import java.util.regex.Pattern;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ViewHolder> {

    private List<Conversation> conversations;
    private List<Conversation> filteredList;
    private OnConversationClickListener listener;

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    public ConversationsAdapter(List<Conversation> conversations, OnConversationClickListener listener) {
        this.conversations = conversations;
        this.filteredList = new ArrayList<>(conversations);
        this.listener = listener;
    }

    public void updateData(List<Conversation> newList) {
        this.conversations = newList;
        this.filteredList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(conversations);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (Conversation conv : conversations) {
                if (conv.getContactName().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(conv);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = filteredList.get(position);
        Context context = holder.itemView.getContext();
        
        holder.name.setText(conversation.getContactName());
        
        // --- E2EE Decryption Logic ---
        String lastMsg = conversation.getLastMessage();
        String targetUserId = conversation.getTargetUserId();
        String secretB64 = (targetUserId != null) ? Prefs.getSharedSecret(targetUserId) : null;

        if (secretB64 != null && lastMsg != null && !lastMsg.isEmpty()
                && !lastMsg.equals("No messages yet")
                && !lastMsg.equals("Tap to chat")
                && looksLikeCiphertext(lastMsg)) {
            try {
                byte[] secret = Base64.decode(secretB64, Base64.NO_WRAP);
                lastMsg = SignalManager.decrypt(lastMsg, secret);
            } catch (Exception e) {
                // Expected for mixed legacy/plaintext/non-matching-session entries.
                // Keep UI stable and avoid alarming stacktraces.
                Log.w(TAG, "Preview decrypt skipped for conversation contact="
                        + conversation.getContactName()
                        + ", targetUserId=" + conversation.getTargetUserId()
                        + " (possibly legacy/plaintext/mismatched key): "
                        + e.getClass().getSimpleName());
            }
        }
        holder.lastMessage.setText(lastMsg);
        // -----------------------------

        holder.time.setText(conversation.getTime());
        
        holder.name.setTypeface(null, Typeface.NORMAL);
        holder.lastMessage.setTypeface(null, Typeface.NORMAL);
        
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true);
        holder.lastMessage.setTextColor(typedValue.data);
        
        // Use ProfileUtils to handle URL fetching, memory caching, and disk caching
        ProfileUtils.loadProfilePicture(context, conversation.getContactName(), holder.profileImage);
        
        holder.itemView.setOnClickListener(v -> {
            listener.onConversationClick(conversation);
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView name, lastMessage, time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.imageViewProfile);
            name = itemView.findViewById(R.id.textViewName);
            lastMessage = itemView.findViewById(R.id.textViewLastMessage);
            time = itemView.findViewById(R.id.textViewTime);
        }
    }
    private static final String TAG = "ConversationsAdapter";
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/=_-]+$");

    private boolean looksLikeCiphertext(String value) {
        if (value == null) return false;
        String s = value.trim();
        // Skip obvious plaintext placeholders / short text
        if (s.length() < 40) return false;
        return BASE64_PATTERN.matcher(s).matches();
    }
}
