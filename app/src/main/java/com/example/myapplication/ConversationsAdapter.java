package com.example.myapplication;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

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
        holder.lastMessage.setText(conversation.getLastMessage());
        holder.time.setText(conversation.getTime());
        
        TypedValue typedValue = new TypedValue();
        if (conversation.isUnread()) {
            holder.name.setTypeface(null, Typeface.BOLD);
            holder.lastMessage.setTypeface(null, Typeface.BOLD);
            context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
            holder.lastMessage.setTextColor(typedValue.data);
        } else {
            holder.name.setTypeface(null, Typeface.NORMAL);
            holder.lastMessage.setTypeface(null, Typeface.NORMAL);
            context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true);
            holder.lastMessage.setTextColor(typedValue.data);
        }
        
        Glide.with(context)
                .load(conversation.getImageUrl())
                .circleCrop()
                .placeholder(R.mipmap.ic_launcher_round)
                .into(holder.profileImage);
        
        holder.itemView.setOnClickListener(v -> {
            conversation.setUnread(false);
            notifyItemChanged(position);
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
}