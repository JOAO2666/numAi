package io.github.gohoski.numai.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.ChatProcessingService;
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
            holder.mathMarkdown = (MathMarkdownView) convertView.findViewById(R.id.message_math);
            holder.generatedImage = (android.widget.ImageView) convertView.findViewById(R.id.generated_image);
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
        boolean isGenerating = message.getChatId() != null &&
                message.getGenerationId() != null &&
                ChatProcessingService.isGenerationActive(
                        message.getChatId(), message.getGenerationId());

        // Thinking Box
        if (thinkingRaw != null && thinkingRaw.length() != 0) {
            holder.thinkingLayout.setVisibility(View.VISIBLE);
            holder.thinkingProcess.setMovementMethod(LinkMovementMethod.getInstance());
            holder.thinkingProcess.setText(
                    message.getParsedThinkContent(context, isGenerating));
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
        String outputImage = message.getOutputImage();
        if (outputImage != null && outputImage.length() > 0) {
            Bitmap generatedBitmap = decodeGeneratedImage(outputImage, 1024);
            if (generatedBitmap != null) {
                holder.generatedImage.setImageBitmap(generatedBitmap);
                holder.generatedImage.setVisibility(View.VISIBLE);
                holder.generatedImage.setTag(outputImage);
                holder.generatedImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Object tag = view.getTag();
                        if (tag instanceof String) showGeneratedImage((String) tag);
                    }
                });
            } else {
                holder.generatedImage.setImageBitmap(null);
                holder.generatedImage.setVisibility(View.GONE);
            }
        } else {
            holder.generatedImage.setImageBitmap(null);
            holder.generatedImage.setVisibility(View.GONE);
            holder.generatedImage.setTag(null);
            holder.generatedImage.setOnClickListener(null);
        }

        if (displayRaw != null && displayRaw.length() != 0) {
            holder.response.setVisibility(View.VISIBLE);
            // Loading a WebView and MathJax for every streamed token is very
            // expensive on old devices. Render the lightweight native preview
            // while streaming, then typeset the completed response once.
            if (!isGenerating && MathMarkdownView.canRender(displayRaw)) {
                holder.messageText.setVisibility(View.GONE);
                holder.mathMarkdown.setVisibility(View.VISIBLE);
                final MathMarkdownView mathView = holder.mathMarkdown;
                final String mathRaw = displayRaw;
                final Message boundMessage = message;
                final ReceivedViewHolder boundHolder = holder;
                mathView.setRenderErrorListener(new MathMarkdownView.RenderErrorListener() {
                    @Override
                    public void onRenderError() {
                        if (mathRaw.equals(mathView.getMarkdown())) {
                            mathView.setVisibility(View.GONE);
                            boundHolder.messageText.setVisibility(View.VISIBLE);
                            boundHolder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
                            boundHolder.messageText.setText(
                                    boundMessage.getParsedDisplayContent(context, false));
                        }
                    }
                });
                holder.mathMarkdown.setMarkdown(displayRaw);
            } else {
                holder.mathMarkdown.setRenderErrorListener(null);
                holder.mathMarkdown.setVisibility(View.GONE);
                holder.messageText.setVisibility(View.VISIBLE);
                holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
                holder.messageText.setText(
                        message.getParsedDisplayContent(context, isGenerating));
            }
        } else {
            holder.mathMarkdown.setRenderErrorListener(null);
            holder.response.setVisibility(holder.generatedImage.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
            holder.mathMarkdown.setVisibility(View.GONE);
            holder.messageText.setVisibility(View.VISIBLE);
            holder.messageText.setText("");
        }

        return convertView;
    }

    private static class SentViewHolder {
        TextView messageText;
    }

    private static class ReceivedViewHolder {
        TextView messageText;
        MathMarkdownView mathMarkdown;
        android.widget.ImageView generatedImage;
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

    private Bitmap decodeGeneratedImage(String fileName, int maxDimension) {
        FileInputStream input = null;
        try {
            input = context.openFileInput(fileName);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            input.close();
            input = null;

            int sample = 1;
            while ((options.outWidth / sample) > maxDimension ||
                    (options.outHeight / sample) > maxDimension) {
                sample *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            input = context.openFileInput(fileName);
            return BitmapFactory.decodeStream(input, null, options);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (input != null) {
                try { input.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void showGeneratedImage(String fileName) {
        Bitmap image = decodeGeneratedImage(fileName, 1600);
        if (image == null) return;
        android.widget.ImageView view = new android.widget.ImageView(context);
        view.setImageBitmap(image);
        view.setAdjustViewBounds(true);
        new AlertDialog.Builder(context)
                .setTitle(R.string.gemini_generated_image)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
