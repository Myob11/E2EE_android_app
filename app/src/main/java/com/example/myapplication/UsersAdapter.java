package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.api.User;
import com.example.myapplication.util.ProfileUtils;
import java.util.List;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {

    private List<User> users;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onAddFriendClick(User user);
    }

    public UsersAdapter(List<User> users, OnUserClickListener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        holder.userName.setText(user.getUsername());
        
        ProfileUtils.loadProfilePicture(holder.itemView.getContext(), user.getUsername(), holder.userAvatar);
        
        holder.btnAddFriend.setOnClickListener(v -> listener.onAddFriendClick(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView userAvatar;
        TextView userName;
        Button btnAddFriend;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            btnAddFriend = itemView.findViewById(R.id.btnAddFriend);
        }
    }
}