package roj.plugins.aiapi;

import roj.collect.SimpleList;

import java.util.List;

/**
 * @author Roj234
 * @since 2025/2/16 21:18
 */
class OAIChatCompletionRequest {
    public String model;
    public List<Message> messages = new SimpleList<>();
    public float temperature = 0.5f;

    public OAIChatCompletionRequest add(Role role, String content) {
        messages.add(new Message(role, content));
        return this;
    }

    public static class Message {
        public Role role;
        public String content;

        public Message() {}
        public Message(Role role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static enum Role {
        user, system, assistant;
    }
}
