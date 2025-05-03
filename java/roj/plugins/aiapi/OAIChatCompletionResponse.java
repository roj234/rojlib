package roj.plugins.aiapi;

import java.util.List;

/**
 * @author Roj234
 * @since 2025/2/16 21:18
 */
class OAIChatCompletionResponse {
    public String id;
    public long created;
    public String model;
    public List<Choice> choices;
    public Usage usage;

    public static class Choice {
        public int index;
        public OAIChatCompletionRequest.Message message;
        public String finish_reason;
    }

    public static class Usage {
        int prompt_tokens, completion_tokens, total_tokens;
    }
}
