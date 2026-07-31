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
import java.util.regex.Pattern;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
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
            holder.searchLayout = (LinearLayout) convertView.findViewById(R.id.searchLayout);
            holder.searchResultsCount = (TextView) convertView.findViewById(R.id.searchResultsCount);
            holder.searchQueries = (TextView) convertView.findViewById(R.id.searchQueries);
            holder.response = convertView.findViewById(R.id.response);
            convertView.setTag(holder);
        } else {
            holder = (ReceivedViewHolder) convertView.getTag();
        }

        String content = message.getContent();

        String thinkingContent = extractThinkingContent(content);
        String displayContent = extractContentWithoutThinking(content);

        // 1. Thinking Box
        if (thinkingContent.length() != 0) {
            holder.thinkingLayout.setVisibility(View.VISIBLE);
            holder.thinkingProcess.setMovementMethod(LinkMovementMethod.getInstance());
            holder.thinkingProcess.setText(MarkdownParser.parse(thinkingContent));
        } else {
            holder.thinkingLayout.setVisibility(View.GONE);
        }

        // 2. Search Box
        JSONArray toolCalls = message.getToolCalls();
        List<String> queries = new ArrayList<String>();
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.size(); i++) {
                try {
                    JSONObject tc = toolCalls.getObject(i);
                    JSONObject fn = tc.getObject("function");
                    if (fn != null && "web_search".equals(fn.getString("name"))) {
                        String argsStr = fn.getString("arguments");
                        JSONObject args = JSON.getObject(argsStr);
                        if (args.has("query")) {
                            queries.add(args.getString("query"));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (!queries.isEmpty()) {
            holder.searchLayout.setVisibility(View.VISIBLE);
            StringBuilder qText = new StringBuilder();
            for (int i = 0; i < queries.size(); i++) {
                if (i > 0) qText.append("\n");
                qText.append("• ").append(queries.get(i));
            }
            holder.searchQueries.setText(qText.toString());

            int resultCount = message.getSearchResultCount();
            if (resultCount >= 0) {
                holder.searchResultsCount.setText(resultCount == 1 ? "1 result" : resultCount + " results");
            } else {
                holder.searchResultsCount.setText("0 results");
            }
        } else {
            holder.searchLayout.setVisibility(View.GONE);
        }

        // 3. Response Box
        holder.llm.setText(message.getLlm());
        if (displayContent.length() != 0) {
            holder.response.setVisibility(View.VISIBLE);
            holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
            holder.messageText.setText(MarkdownParser.parse(displayContent));
        } else {
            holder.response.setVisibility(View.GONE);
        }

        convertView.requestLayout();

        return convertView;
    }

    private String extractThinkingContent(String content) {
        if (content == null) return "";
        int thinkStart = content.indexOf("<think>");
        if (thinkStart != -1) {
            int thinkEnd = content.indexOf("</think>", thinkStart);
            if (thinkEnd != -1) {
                return content.substring(thinkStart + 7, thinkEnd).trim();
            } else {
                return content.substring(thinkStart + 7).trim();
            }
        }
        return "";
    }

    private static final Pattern PATTERN_THOUGHT_CHANNEL_1 = Pattern.compile("(?i)^\\s*thought\\s*<\\|?channel\\|?>");
    private static final Pattern PATTERN_THOUGHT_CHANNEL_2 = Pattern.compile("(?i)^\\s*<\\|?channel\\|?>");
    private static final Pattern PATTERN_THOUGHT_CHANNEL_3 = Pattern.compile("(?i)^\\s*<\\|?channel>thought\\s*");
    private static final Pattern PATTERN_THOUGHT_CHANNEL_4 = Pattern.compile("(?i)^\\s*thought\\s*\n");
    private String extractContentWithoutThinking(String content) {
        if (content == null) return "";
        String text = content;
        int thinkStart = text.indexOf("<think>");
        if (thinkStart != -1) {
            int thinkEnd = text.indexOf("</think>", thinkStart);
            if (thinkEnd != -1) {
                text = text.substring(0, thinkStart) + text.substring(thinkEnd + 8);
            } else {
                text = text.substring(0, thinkStart);
            }
        }

        text = PATTERN_THOUGHT_CHANNEL_1.matcher(text).replaceAll("");
        text = PATTERN_THOUGHT_CHANNEL_2.matcher(text).replaceAll("");
        text = PATTERN_THOUGHT_CHANNEL_3.matcher(text).replaceAll("");
        text = PATTERN_THOUGHT_CHANNEL_4.matcher(text).replaceAll("");
        return text.trim();
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
        View response;
    }
}