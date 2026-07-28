package io.github.gohoski.numai.data;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.model.Chat;
import io.github.gohoski.numai.model.Message;

public class ChatManager {
    private static ChatManager instance;
    private final List<Chat> chats;
    private Chat currentChat;

    private ChatManager() {
        chats = new ArrayList<Chat>();
        startNewChat();
    }

    public static synchronized ChatManager getInstance() {
        if (instance == null) {
            instance = new ChatManager();
        }
        return instance;
    }

    public Chat getCurrentChat() {
        if (currentChat == null) {
            startNewChat();
        }
        return currentChat;
    }

    public void startNewChat() {
        currentChat = new Chat(UUID.randomUUID().toString(), "", System.currentTimeMillis());
    }

    public void setCurrentChat(Chat chat) {
        this.currentChat = chat;
    }

    public List<Chat> getSortedChats() {
        List<Chat> sorted = new ArrayList<Chat>(chats);
        Collections.sort(sorted, new Comparator<Chat>() {
            @Override
            public int compare(Chat c1, Chat c2) {
                if (c2.getUpdatedAt() > c1.getUpdatedAt()) return 1;
                if (c2.getUpdatedAt() < c1.getUpdatedAt()) return -1;
                return 0;
            }
        });
        return sorted;
    }

    public void onMessageAdded(Context context) {
        if (currentChat == null) return;

        if (currentChat.getTitle() == null || currentChat.getTitle().length() == 0) {
            for (Message msg : currentChat.getMessages()) {
                if (msg.isSent() && msg.getContent() != null && msg.getContent().trim().length() > 0) {
                    currentChat.setTitle(generateTitle(msg.getContent()));
                    break;
                }
            }
        }

        if (!chats.contains(currentChat) && currentChat.getMessages().size() > 0) {
            chats.add(currentChat);
        }

        currentChat.setUpdatedAt(System.currentTimeMillis());
        saveChats(context);
    }

    public void deleteChat(Context context, Chat chat) {
        chats.remove(chat);
        if (currentChat == chat) {
            startNewChat();
        }
        saveChats(context);
    }

    public void saveChats(Context context) {
        try {
            JSONArray array = new JSONArray();
            for (Chat c : chats) {
                array.add(c.toJSONObject());
            }
            FileOutputStream fos = context.openFileOutput("chats.json", Context.MODE_PRIVATE);
            fos.write(array.build().getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadChats(Context context) {
        chats.clear();
        try {
            FileInputStream fis = context.openFileInput("chats.json");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            fis.close();
            String jsonStr = baos.toString("UTF-8");
            if (jsonStr != null && jsonStr.trim().length() > 0) {
                JSONArray array = JSON.getArray(jsonStr);
                for (int i = 0; i < array.size(); i++) {
                    JSONObject obj = array.getObject(i);
                    chats.add(Chat.fromJSONObject(obj));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String generateTitle(String firstUserMessage) {
        if (firstUserMessage == null) return "New Chat";
        String clean = firstUserMessage.trim().replace('\n', ' ').replace('\r', ' ');
        if (clean.length() <= 35) {
            return clean;
        }
        return clean.substring(0, 35) + "\u2026";
    }
}
