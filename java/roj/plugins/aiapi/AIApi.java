package roj.plugins.aiapi;

import java.io.File;

/**
 * @author Roj234
 * @since 2025/2/16 0016 21:26
 */
public class AIApi {
    public static void main(String[] args) throws Exception {
        var task = ChatTask.fromFile(new File("plugins/AIApi/templates/translate_en.json"));
        String result = task.eval("Initial random depletion percentage: {MiningParameters.initial_depletion * 100}%");
        System.out.println(result);
        System.out.println("Token Consumed:"+task.totalInputTokens);
        System.out.println("Token Generated:"+task.totalOutputTokens);
    }
}
