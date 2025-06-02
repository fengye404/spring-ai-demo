import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.fengye.Main;

/**
 * @author: FengYe
 * @date: 2025/6/2 18:47
 * @description: Test
 */
@SpringBootTest(classes = Main.class)
public class Tests {
    @Autowired
    private OllamaChatModel chatModel;

    @Test
    void ollamaChat() {
        ChatResponse response = chatModel.call(
                new Prompt(
                        "你是什么模型",
                        OllamaOptions.builder()
                                .temperature(0.4)
                                .build()
                ));
        System.out.println(response);
    }
}
