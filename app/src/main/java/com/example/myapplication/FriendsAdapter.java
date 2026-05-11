package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.api.User;
import com.example.myapplication.util.ProfileUtils;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

    private List<User> friends;
    private OnFriendClickListener listener;

    public interface OnFriendClickListener {
        void onFriendClick(User friend);
    }

    public FriendsAdapter(List<User> friends, OnFriendClickListener listener) {
        this.friends = friends;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User friend = friends.get(position);
        holder.name.setText(friend.getUsername());
        holder.lastMessage.setText("Tap to start chatting");
        holder.time.setVisibility(View.GONE);
        
        ProfileUtils.loadProfilePicture(holder.itemView.getContext(), friend.getUsername(), holder.profileImage);
        
        holder.itemView.setOnClickListener(v -> listener.onFriendClick(friend));
    }

    @Override
    public int getItemCount() {
        return friends.size();
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
