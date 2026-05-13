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

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ViewHolder> {

    private List<Conversation> conversations;
    private List<Conversation> filteredList;
    private OnConversationClickListener listener;
    private OnConversationLongClickListener longClickListener;

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(Conversation conversation);
    }

    public ConversationsAdapter(
            List<Conversation> conversations,
            OnConversationClickListener listener,
            OnConversationLongClickListener longClickListener
    ) {
        this.conversations = conversations;
        this.filteredList = new ArrayList<>(conversations);
        this.listener = listener;
        this.longClickListener = longClickListener;
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

        if (secretB64 != null && lastMsg != null && !lastMsg.isEmpty() && 
            !lastMsg.equals("No messages yet") && !lastMsg.equals("Tap to chat")) {
            try {
                byte[] secret = Base64.decode(secretB64, Base64.NO_WRAP);
                lastMsg = SignalManager.decrypt(lastMsg, secret);
            } catch (Exception e) {
                // If decryption fails, it might be a new session or corrupted state
                lastMsg = "[Encrypted Message]";
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

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onConversationLongClick(conversation);
                return true;
            }
            return false;
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
}
