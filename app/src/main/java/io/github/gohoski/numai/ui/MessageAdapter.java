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

import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.R;
import io.github.gohoski.numai.model.Message;
import io.github.gohoski.numai.model.Role;

public class MessageAdapter extends ArrayAdapter<Message> {
    private static final int VIEW_TYPE_SENT = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;
    private Context context;
    private List<Message> rawMessages;

    public MessageAdapter(Context context, List<Message> messages) {
        super(context, R.layout.message_sent, filterMessages(messages));
        Log.d("MessageAdapter", "Created adapter with " + messages.size() + " messages");
        this.context = context;
        this.rawMessages = messages;
    }

    private static List<Message> filterMessages(List<Message> messages) {
        List<Message> filtered = new ArrayList<Message>();
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (msg.getRoleEnum() != Role.TOOL) {
                    filtered.add(msg);
                }
            }
        }
        return filtered;
    }

    @Override
    public void notifyDataSetChanged() {
        setNotifyOnChange(false);
        clear();
        List<Message> filtered = filterMessages(rawMessages);
        for (int i = 0; i < filtered.size(); i++) {
            add(filtered.get(i));
        }
        setNotifyOnChange(true);
        super.notifyDataSetChanged();
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
            holder.searchLayout = (LinearLayout) convertView.findViewById(R.id.searchLayout);
            holder.searchResultsCount = (TextView) convertView.findViewById(R.id.searchResultsCount);
            holder.searchQueries = (TextView) convertView.findViewById(R.id.searchQueries);
            holder.readingLayout = (LinearLayout) convertView.findViewById(R.id.readingLayout);
            holder.readingUrls = (TextView) convertView.findViewById(R.id.readingUrls);
            holder.response = convertView.findViewById(R.id.response);
            convertView.setTag(holder);
        } else {
            holder = (ReceivedViewHolder) convertView.getTag();
        }

        String thinkingRaw = message.getThinkingRaw();
        String displayRaw = message.getDisplayRaw();

        // Thinking Box
        if (thinkingRaw != null && thinkingRaw.length() != 0) {
            holder.thinkingLayout.setVisibility(View.VISIBLE);
            holder.thinkingProcess.setMovementMethod(LinkMovementMethod.getInstance());
            holder.thinkingProcess.setText(message.getParsedThinkContent(context, false));
        } else {
            holder.thinkingLayout.setVisibility(View.GONE);
        }

        // Search Box
        JSONArray toolCalls = message.getToolCalls();
        List<String> toolActions = new ArrayList<String>();
        List<String> readingActions = new ArrayList<String>();
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.size(); i++) {
                try {
                    JSONObject tc = toolCalls.getObject(i);
                    JSONObject fn = tc.getObject("function");
                    if (fn != null) {
                        String name = fn.getString("name");
                        String argsStr = fn.getString("arguments");
                        JSONObject args = JSON.getObject(argsStr);
                        if ("web_search".equals(name)) {
                            try {
                                toolActions.add(args.getString("query"));
                            } catch (JSONException e) { e.printStackTrace(); }
                        } else if ("web_fetch".equals(name)) {
                            try {
                                readingActions.add(args.getString("url"));
                            } catch (JSONException e) { e.printStackTrace(); }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (!toolActions.isEmpty()) {
            holder.searchLayout.setVisibility(View.VISIBLE);
            StringBuilder actionText = new StringBuilder();
            for (int i = 0; i < toolActions.size(); i++) {
                if (i > 0) actionText.append("\n");
                actionText.append("• ").append(toolActions.get(i));
            }
            holder.searchQueries.setText(actionText.toString());

            int resultCount = message.getSearchResultCount();
            if (resultCount > 0) {
                holder.searchResultsCount.setVisibility(View.VISIBLE);
                holder.searchResultsCount.setText(context.getResources().getQuantityString(R.plurals.results, resultCount, resultCount));
            } else {
                holder.searchResultsCount.setVisibility(View.GONE);
            }
        } else {
            holder.searchLayout.setVisibility(View.GONE);
        }

        // Reading sources Box
        if (!readingActions.isEmpty()) {
            holder.readingLayout.setVisibility(View.VISIBLE);
            StringBuilder readingText = new StringBuilder();
            for (int i = 0; i < readingActions.size(); i++) {
                if (i > 0) readingText.append("\n");
                readingText.append("• ").append(readingActions.get(i));
            }
            holder.readingUrls.setText(readingText.toString());
        } else {
            holder.readingLayout.setVisibility(View.GONE);
        }

        // Response Box
        holder.llm.setText(message.getLlm());
        if (displayRaw != null && displayRaw.length() != 0) {
            holder.response.setVisibility(View.VISIBLE);
            holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
            holder.messageText.setText(message.getParsedDisplayContent(context, false));
        } else {
            holder.response.setVisibility(View.GONE);
        }

        return convertView;
    }

    private static class SentViewHolder {
        TextView messageText;
    }

    private static class ReceivedViewHolder {
        TextView messageText;
        TextView llm;
        LinearLayout thinkingLayout;
        TextView thinkingProcess;
        LinearLayout searchLayout;
        TextView searchResultsCount;
        TextView searchQueries;
        LinearLayout readingLayout;
        TextView readingUrls;
        View response;
    }
}