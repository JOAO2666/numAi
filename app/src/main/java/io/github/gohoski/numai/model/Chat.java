package io.github.gohoski.numai.model;

import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class Chat {
    private String id;
    private String title;
    private long updatedAt;
    private List<Message> messages;

    public Chat(String id, String title, long updatedAt) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
        this.messages = new ArrayList<Message>();
    }

    public Chat(String id, String title, long updatedAt, List<Message> messages) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
        this.messages = messages != null ? messages : new ArrayList<Message>();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public List<Message> getMessages() { return messages; }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("updatedAt", updatedAt);
        JSONArray msgArr = new JSONArray();
        for (int i = 0; i < messages.size(); i++) {
            msgArr.add(messages.get(i).toJSONObject());
        }
        json.put("messages", msgArr);
        return json;
    }

    public static Chat fromJSONObject(JSONObject json) {
        String id = json.getNullableString("id");
        String title = json.getNullableString("title");
        long updatedAt = json.getLong("updatedAt", System.currentTimeMillis());
        List<Message> msgs = new ArrayList<Message>();
        if (json.has("messages")) {
            JSONArray msgArr = json.getNullableArray("messages");
            if (msgArr != null) {
                for (int i = 0; i < msgArr.size(); i++) {
                    msgs.add(Message.fromJSONObject(msgArr.getObject(i)));
                }
            }
        }
        return new Chat(id, title, updatedAt, msgs);
    }
}
