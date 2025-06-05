package top.fengye.controller.model;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author: FengYe
 * @date: 2025/6/5 02:40
 * @description: BailianModel
 */
public class DashScopeModel implements ChatModel {
    @Override
    public ChatResponse call(Prompt prompt) {
        return null;
    }

    private GenerationParam convertDashScopeParam(Prompt prompt) {
        List<org.springframework.ai.chat.messages.Message> instructions = prompt.getInstructions();
        ChatOptions options = prompt.getOptions();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(prompt.getSystemMessage().getText())
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你是谁？")
                .build();
        GenerationParam generationParam = GenerationParam.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model(options.getModel())
                .topK(options.getTopK())
                .topP(options.getTopP())
                .maxTokens(options.getMaxTokens())
                .temperature(Objects.requireNonNull(options.getTemperature()).floatValue())
                .repetitionPenalty(Objects.requireNonNull(options.getFrequencyPenalty()).floatValue())
                .stopStrings(options.getStopSequences())
                .incrementalOutput(false)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        return generationParam;
    }

    public static void main(String[] args) {
//        try {
//            GenerationResult result = callWithMessage();
//            GenerationOutput output = result.getOutput();
//            System.out.println(output);
//            System.out.println(JsonUtils.toJson(result));
//        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
//            // 使用日志框架记录异常信息
//            System.err.println("An error occurred while calling the generation service: " + e.getMessage());
//        }
//        System.exit(0);
    }
}
