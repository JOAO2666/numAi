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
    private static final String INDEX_FILE = "chats.json";

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

    public synchronized Chat getCurrentChat() {
        if (currentChat == null) {
            startNewChat();
        }
        return currentChat;
    }

    public synchronized Chat getChatById(String chatId) {
        if (chatId == null) return null;
        for (int i = 0; i < chats.size(); i++) {
            if (chatId.equals(chats.get(i).getId())) return chats.get(i);
        }
        return null;
    }

    public synchronized void startNewChat() {
        currentChat = new Chat(UUID.randomUUID().toString(), "", System.currentTimeMillis());
    }

    public synchronized void setCurrentChat(Context context, Chat chat) {
        this.currentChat = chat;
        if (chat != null && chat.getId() != null && chat.getMessages().size() == 0) {
            loadMessages(context, chat);
        }
    }

    public synchronized void ensureMessagesLoaded(Context context, Chat chat) {
        if (chat != null && chat.getId() != null && chat.getMessages().size() == 0) {
            loadMessages(context, chat);
        }
    }

    public synchronized List<Chat> getSortedChats() {
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
        onMessageAdded(context, currentChat);
    }

    public synchronized void onMessageAdded(Context context, Chat targetChat) {
        if (targetChat == null) return;

        if (targetChat.getTitle() == null || targetChat.getTitle().length() == 0) {
            for (Message msg : targetChat.getMessages()) {
                if (msg.isSent() && msg.getContent() != null && msg.getContent().trim().length() > 0) {
                    targetChat.setTitle(generateTitle(msg.getContent()));
                    break;
                }
            }
        }

        if (!chats.contains(targetChat) && targetChat.getMessages().size() > 0) {
            chats.add(targetChat);
        }

        targetChat.setUpdatedAt(System.currentTimeMillis());
        saveChat(context, targetChat);
        saveChats(context);
    }

    public synchronized void deleteChat(Context context, Chat chat) {
        chats.remove(chat);
        if (currentChat == chat) {
            startNewChat();
        }

        // Delete associated image files from internal storage
        if (chat.getMessages() != null) {
            for (Message msg : chat.getMessages()) {
                List<String> images = msg.getInputImages();
                if (images != null) {
                    for (String fileName : images) {
                        if (!fileName.startsWith("data:image")) {
                            context.deleteFile(fileName);
                        }
                    }
                }
                String generatedImage = msg.getOutputImage();
                if (generatedImage != null && !generatedImage.startsWith("data:image")) {
                    context.deleteFile(generatedImage);
                }
            }
        }

        if (chat.getId() != null) {
            context.deleteFile(chatFileName(chat));
        }
        saveChats(context);
    }

    public void cleanupOrphanedImages(Context context) {
        final Context ctx = context;
        new Thread(new Runnable() {
            @Override
            public void run() {
                java.util.Set<String> referencedFiles = new java.util.HashSet<String>();

                // Gather all image filenames across all chats
                for (Chat chat : chats) {
                    for (Message msg : chat.getMessages()) {
                        if (msg.getInputImages() != null) {
                            for (String img : msg.getInputImages()) {
                                if (!img.startsWith("data:image")) {
                                    referencedFiles.add(img);
                                }
                            }
                        }
                        String generatedImage = msg.getOutputImage();
                        if (generatedImage != null && !generatedImage.startsWith("data:image")) {
                            referencedFiles.add(generatedImage);
                        }
                    }
                }

                // List all files in internal directory and delete unreferenced images
                String[] files = ctx.fileList();
                if (files != null) {
                    for (String file : files) {
                        if ((file.startsWith("img_") || file.startsWith("gemini_img_")) &&
                                (file.endsWith(".jpg") || file.endsWith(".png"))) {
                            if (!referencedFiles.contains(file)) {
                                ctx.deleteFile(file);
                            }
                        }
                    }
                }
            }
        }).start();
    }

    public synchronized void saveChat(Context context, Chat chat) {
        if (chat == null || chat.getId() == null) return;
        String finalFileName = chatFileName(chat);
        try {
            writeAtomically(context, finalFileName,
                    chat.toJSONObject().build().getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void saveChats(Context context) {
        try {
            JSONArray array = new JSONArray();
            for (Chat c : chats) {
                array.add(c.toIndexJSONObject());
            }
            writeAtomically(context, INDEX_FILE, array.build().getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void loadChats(Context context) {
        String previousCurrentId = currentChat == null ? null : currentChat.getId();
        chats.clear();
        try {
            FileInputStream fis = openStoredFile(context, INDEX_FILE);
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
                boolean migrated = false;
                for (int i = 0; i < array.size(); i++) {
                    JSONObject obj = array.getObject(i);
                    Chat chat = Chat.fromIndexJSONObject(obj);
                    JSONArray msgArr = obj.getNullableArray("messages");
                    if (msgArr != null && msgArr.size() > 0) {
                        chat = Chat.fromJSONObject(obj);
                        saveChat(context, chat);
                        migrated = true;
                    }
                    chats.add(chat);
                }
                if (migrated) {
                    saveChats(context);
                }
            }
        } catch (Exception ignored) {
        }

        if (previousCurrentId != null) {
            Chat restored = getChatById(previousCurrentId);
            if (restored != null) currentChat = restored;
        }
        if (currentChat == null) startNewChat();
    }

    private void loadMessages(Context context, Chat chat) {
        try {
            FileInputStream fis = openStoredFile(context, chatFileName(chat));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            fis.close();
            String jsonStr = baos.toString("UTF-8");
            if (jsonStr != null && jsonStr.trim().length() > 0) {
                Chat loaded = Chat.fromJSONObject(JSON.getObject(jsonStr));
                chat.getMessages().clear();
                chat.getMessages().addAll(loaded.getMessages());
            }
        } catch (Exception ignored) {
        }
    }

    private static String chatFileName(Chat chat) {
        return "chat_" + chat.getId() + ".json";
    }

    private static FileInputStream openStoredFile(Context context,
            String fileName) throws Exception {
        java.io.File target = context.getFileStreamPath(fileName);
        java.io.File backup = context.getFileStreamPath(fileName + ".bak");
        if (!target.exists() && backup.exists()) backup.renameTo(target);
        if (target.exists() && backup.exists()) backup.delete();
        return context.openFileInput(fileName);
    }

    /** Replaces a file without leaving a partially written chat after a crash. */
    private static void writeAtomically(Context context, String fileName,
            byte[] data) throws Exception {
        String tempName = fileName + ".tmp";
        String backupName = fileName + ".bak";
        FileOutputStream output = null;
        try {
            output = context.openFileOutput(tempName, Context.MODE_PRIVATE);
            output.write(data);
            output.flush();
        } finally {
            if (output != null) {
                try { output.close(); } catch (Exception ignored) {}
            }
        }

        java.io.File temp = context.getFileStreamPath(tempName);
        java.io.File target = context.getFileStreamPath(fileName);
        java.io.File backup = context.getFileStreamPath(backupName);
        if (backup.exists()) backup.delete();

        boolean hadTarget = target.exists();
        if (hadTarget && !target.renameTo(backup)) {
            temp.delete();
            throw new java.io.IOException("Could not protect existing " + fileName);
        }
        if (!temp.renameTo(target)) {
            if (hadTarget) backup.renameTo(target);
            temp.delete();
            throw new java.io.IOException("Could not replace " + fileName);
        }
        if (backup.exists()) backup.delete();
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
