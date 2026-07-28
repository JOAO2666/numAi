package io.github.gohoski.numai.data;

import java.util.List;

import io.github.gohoski.numai.model.Message;

public class MessageManager {
    private static MessageManager instance;

    private MessageManager() {
    }

    public static synchronized MessageManager getInstance() {
        if (instance == null) {
            instance = new MessageManager();
        }
        return instance;
    }

    public void addMessage(Message message) {
        ChatManager.getInstance().getCurrentChat().getMessages().add(message);
    }

    public List<Message> getMessages() {
        return ChatManager.getInstance().getCurrentChat().getMessages();
    }

    public void clearMessages() {
        ChatManager.getInstance().getCurrentChat().getMessages().clear();
    }
}
