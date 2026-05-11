package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.util.ProfileUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private List<Message> messages;
    private String otherUsername;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private int lastSentMessagePosition = -1;

    public MessagesAdapter(List<Message> messages) {
        this.messages = messages;
        updateLastSentPosition();
    }

    public void setOtherUsername(String username) {
        this.otherUsername = username;
        notifyDataSetChanged();
    }

    public void updateMessages(List<Message> newMessages) {
        this.messages = newMessages;
        updateLastSentPosition();
        notifyDataSetChanged();
    }

    private void updateLastSentPosition() {
        lastSentMessagePosition = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isSentByMe()) {
                lastSentMessagePosition = i;
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSentByMe() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        String timeStr = timeFormat.format(new Date(message.getTimestamp()));
        
        if (holder instanceof SentViewHolder) {
            SentViewHolder sentHolder = (SentViewHolder) holder;
            sentHolder.messageText.setText(message.getContent());
            sentHolder.timeText.setText(timeStr);
            
            if (position == lastSentMessagePosition) {
                sentHolder.statusText.setVisibility(View.VISIBLE);
                if (message.isRead()) {
                    sentHolder.statusText.setText("Read");
                    sentHolder.statusText.setTextColor(sentHolder.itemView.getContext().getResources().getColor(R.color.primary));
                } else {
                    sentHolder.statusText.setText("Sent");
                    sentHolder.statusText.setTextColor(sentHolder.itemView.getContext().getResources().getColor(android.R.color.darker_gray));
                }
            } else {
                sentHolder.statusText.setVisibility(View.GONE);
            }
        } else {
            ReceivedViewHolder receivedHolder = (ReceivedViewHolder) holder;
            receivedHolder.messageText.setText(message.getContent());
            receivedHolder.timeText.setText(timeStr);

            ProfileUtils.loadProfilePicture(receivedHolder.itemView.getContext(), otherUsername, receivedHolder.avatarImage);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText, statusText;
        SentViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textViewMessageSent);
            timeText = itemView.findViewById(R.id.textViewTimeSent);
            statusText = itemView.findViewById(R.id.textViewStatus);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;
        ImageView avatarImage;
        ReceivedViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textViewMessageReceived);
            timeText = itemView.findViewById(R.id.textViewTimeReceived);
            avatarImage = itemView.findViewById(R.id.imageViewAvatar);
        }
    }
}
