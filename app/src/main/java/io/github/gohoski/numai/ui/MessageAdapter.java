package io.github.gohoski.numai.ui;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import io.github.gohoski.numai.R;
import io.github.gohoski.numai.model.Message;

public class MessageAdapter extends ArrayAdapter<Message> {
    private static final int VIEW_TYPE_SENT = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;
    private Context context;

    public MessageAdapter(Context context, List<Message> messages) {
        super(context, R.layout.message_sent, messages);
        Log.d("MessageAdapter", "Created adapter with " + messages.size() + " messages");
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isSent() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getCount() {
        return super.getCount();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message message = getItem(position);
        int viewType = getItemViewType(position);

        if (viewType == VIEW_TYPE_SENT) {
            return getSentView(position, convertView, parent, message);
        } else {
            return getReceivedView(position, convertView, parent, message);
        }
    }

    private View getSentView(int position, View convertView, ViewGroup parent, Message message) {
        SentViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.message_sent, parent, false);

            holder = new SentViewHolder();
            holder.messageText = (TextView) convertView.findViewById(R.id.message_text);
            convertView.setTag(holder);
        } else {
            holder = (SentViewHolder) convertView.getTag();
        }

        List<String> images = message.getInputImages();
        holder.messageText.setText(message.getContent() + (images == null || images.isEmpty() ? "" : String.format("\n " + context.getString(R.string.img_count), String.valueOf(images.size()))));

        convertView.requestLayout();

        return convertView;
    }

    private View getReceivedView(int position, View convertView, ViewGroup parent, Message message) {
        ReceivedViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.message_received, parent, false);

            holder = new ReceivedViewHolder();
            holder.messageText = (TextView) convertView.findViewById(R.id.message_text);
            holder.llm = (TextView) convertView.findViewById(R.id.llm);
            holder.thinkingLayout = (LinearLayout) convertView.findViewById(R.id.thinkingLayout);
            holder.thinkingProcess = (TextView) convertView.findViewById(R.id.thinkingProcess);
            convertView.setTag(holder);
        } else {
            holder = (ReceivedViewHolder) convertView.getTag();
        }

        String content = message.getContent();

        String thinkingContent = extractThinkingContent(content);
        String displayContent = extractContentWithoutThinking(content);

        holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
        holder.messageText.setText(MarkdownParser.parse(displayContent));
        holder.llm.setText(message.getLlm());

        if (thinkingContent.length() != 0) {
            holder.thinkingLayout.setVisibility(View.VISIBLE);
            holder.thinkingProcess.setMovementMethod(LinkMovementMethod.getInstance());
            holder.thinkingProcess.setText(MarkdownParser.parse(thinkingContent));
        }

        convertView.requestLayout();

        return convertView;
    }

    private String extractThinkingContent(String content) {
        if (content == null) return "";
        int thinkStart = content.indexOf("<think>");
        int thinkEnd = content.indexOf("</think>");
        if (thinkStart != -1 && thinkEnd != -1 && thinkEnd > thinkStart) {
            return content.substring(thinkStart + 7, thinkEnd);
        }
        return "";
    }

    private String extractContentWithoutThinking(String content) {
        if (content == null) return "";
        int thinkStart = content.indexOf("<think>");
        int thinkEnd = content.indexOf("</think>");
        if (thinkStart != -1 && thinkEnd != -1 && thinkEnd > thinkStart) {
            return content.substring(0, thinkStart) + content.substring(thinkEnd + 8);
        }
        return content;
    }

    private static class SentViewHolder {
        TextView messageText;
    }

    private static class ReceivedViewHolder {
        TextView messageText;
        TextView llm;
        LinearLayout thinkingLayout;
        TextView thinkingProcess;
    }
}
