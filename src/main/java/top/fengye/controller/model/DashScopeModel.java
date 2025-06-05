package top.fengye.controller.model;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        List<org.springframework.ai.chat.messages.Message> messages = prompt.getInstructions();
        ChatOptions options = prompt.getOptions();

        // 1. 处理大模型 options
        GenerationParam.GenerationParamBuilder<?, ?> paramBuilder = GenerationParam.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model(options.getModel())
                .topK(options.getTopK())
                .topP(options.getTopP())
                .maxTokens(options.getMaxTokens())
                .temperature(Objects.requireNonNull(options.getTemperature()).floatValue())
                .repetitionPenalty(Objects.requireNonNull(options.getFrequencyPenalty()).floatValue())
                .stopStrings(options.getStopSequences())
                .incrementalOutput(false)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE);

        // 2. 处理大模型 message
        paramBuilder.messages(messages.stream().map(message -> {
            switch (message.getMessageType()) {
                case USER:
                    return List.of(Message.builder()
                            .role(Role.USER.getValue())
                            .content(message.getText())
                            .build());
                case SYSTEM:
                    return List.of(Message.builder()
                            .role(Role.SYSTEM.getValue())
                            .content(message.getText())
                            .build());
                case ASSISTANT:
                    return List.of(Message.builder()
                            .role(Role.ASSISTANT.getValue())
                            .content(message.getText())
                            .build());
                case TOOL:
                    ToolResponseMessage toolResponseMessage = (ToolResponseMessage) message;
                    return toolResponseMessage.getResponses().stream().map(toolResponse -> Message.builder()
                            .role(Role.TOOL.getValue())
                            .toolCallId(toolResponse.id())
                            .name(toolResponse.name())
                            .content(toolResponse.responseData())
                            .build()).toList();
                default:
                    throw new IllegalArgumentException("Invalid messageType: " + message.getMessageType());
            }
        }).flatMap(List::stream).collect(Collectors.toList()));

        return paramBuilder.build();
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
