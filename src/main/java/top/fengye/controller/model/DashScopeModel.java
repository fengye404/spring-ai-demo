package top.fengye.controller.model;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.*;
import com.alibaba.dashscope.utils.JsonUtils;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import top.fengye.controller.tool.WeatherService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: FengYe
 * @date: 2025/6/5 02:40
 * @description: BailianModel
 */
@Slf4j
public class DashScopeModel implements ChatModel {

    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse chatResponse = this.internalCall(prompt);
        // 工具执行后，返回的结果再发给模型
        if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), chatResponse)) {
            ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);
            return toolExecutionResult.returnDirect() ? ChatResponse.builder().from(chatResponse).generations(ToolExecutionResult.buildGenerations(toolExecutionResult)).build()
                    : this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()));
        } else {
            return chatResponse;
        }
    }

    private ChatResponse internalCall(Prompt prompt) {
        GenerationParam generationParam = convertDashScopeParamBuilder(prompt)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(false).build();
        com.alibaba.dashscope.aigc.generation.Generation gen = new com.alibaba.dashscope.aigc.generation.Generation();
        GenerationResult res = null;
        try {
            res = gen.call(generationParam);
        } catch (Exception e) {
            log.error("DashScopeModel call error", e);
            return null;
        }
        ChatResponse chatResponse = convertDashScopeResponse(res);
        return chatResponse;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Flux<ChatResponse> chatResponseFlux = internalStream(prompt);
        return chatResponseFlux.flatMap(response -> {
            // todo
            System.out.println(response.getResult().getOutput().getToolCalls());

//            ChatResponse toolCall = ChatResponse.builder().from(response).generations(
//                    List.of(
//                            new Generation(new AssistantMessage("", new HashMap<>(), response.getResult().getOutput().getToolCalls()),
//                                    ChatGenerationMetadata.builder().finishReason("toolCall").build()
//                            )
//                    )
//            ).build();
            System.out.println(response.hasToolCalls());
            if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
                return Flux.defer(() -> {
                    var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
                    if (toolExecutionResult.returnDirect()) {
                        // Return tool execution result directly to the client.
                        return Flux.just(ChatResponse.builder().from(response)
                                .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                .build());
                    } else {
                        // Send the tool execution result back to the model.
                        return this.stream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()));
                    }
                }).subscribeOn(Schedulers.boundedElastic());
            } else {
                return Flux.just(response);
            }
        });
    }

    private Flux<ChatResponse> internalStream(Prompt prompt) {
        GenerationParam generationParam = convertDashScopeParamBuilder(prompt)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true).build();
        com.alibaba.dashscope.aigc.generation.Generation gen = new com.alibaba.dashscope.aigc.generation.Generation();
        try {
            Flux<GenerationResult> flux = Flux.from(gen.streamCall(generationParam));
            return flux.map(this::convertDashScopeResponse);
        } catch (Exception e) {
            log.error("DashScopeModel call error", e);
            return null;
        }
    }

    private ChatResponse convertDashScopeResponse(GenerationResult res) {
        GenerationOutput.Choice choice = res.getOutput().getChoices().getFirst();
        if (null == choice) {
            throw new IllegalArgumentException("output is null");
        }
        // 1.构造AssistantMessage
        List<ToolCallBase> dsTool = choice.getMessage().getToolCalls();
        AssistantMessage assistantMessage = null;
        if (CollectionUtils.isEmpty(dsTool)) {
            assistantMessage = new AssistantMessage(choice.getMessage().getContent());
        } else {
            List<AssistantMessage.ToolCall> list = dsTool.stream().map(t -> {
                ToolCallFunction toolCallFunction = (ToolCallFunction) t;
                return new AssistantMessage.ToolCall(toolCallFunction.getId(), toolCallFunction.getFunction().getName(),
                        toolCallFunction.getFunction().getName(), toolCallFunction.getFunction().getArguments());
            }).toList();

            // 按需添加
            HashMap<String, Object> properties = new HashMap<>();
            assistantMessage = new AssistantMessage(choice.getMessage().getContent(), properties, list);
        }

        // 2.构造Generation
        ChatGenerationMetadata chatGenerationMetadata = ChatGenerationMetadata.builder().finishReason(choice.getFinishReason()).build();
        Generation generation = new Generation(assistantMessage, chatGenerationMetadata);

        // 3.构造ChatResponse
        GenerationUsage dsUsage = res.getUsage();
        DefaultUsage usage = new DefaultUsage(dsUsage.getInputTokens(), dsUsage.getOutputTokens(), dsUsage.getTotalTokens());
        ChatResponseMetadata chatResponseMetadata = ChatResponseMetadata.builder()
                .usage(usage)
                .id(res.getRequestId()).build();
        return new ChatResponse(List.of(generation), chatResponseMetadata);
    }

    private GenerationParam.GenerationParamBuilder<?, ?> convertDashScopeParamBuilder(Prompt prompt) {
        List<org.springframework.ai.chat.messages.Message> messages = prompt.getInstructions();
        ChatOptions options = prompt.getOptions();
        if (null == options) {
            throw new IllegalArgumentException("options is null");
        }

        // 1. 处理大模型 options
        GenerationParam.GenerationParamBuilder<?, ?> paramBuilder = GenerationParam.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model(options.getModel())
                .topK(options.getTopK())
                .topP(options.getTopP())
                .maxTokens(options.getMaxTokens())
                .incrementalOutput(false)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE);

        if (Objects.nonNull(options.getTemperature())) {
            paramBuilder.temperature(options.getTemperature().floatValue());
        }
        if (Objects.nonNull(options.getFrequencyPenalty())) {
            paramBuilder.repetitionPenalty(options.getFrequencyPenalty().floatValue());
        }
        if (Objects.nonNull(options.getStopSequences())) {
            paramBuilder.stopStrings(options.getStopSequences());
        }

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
                    AssistantMessage assistantMessage = (AssistantMessage) message;
                    List<ToolCallBase> tooCalls = new ArrayList<>();
                    if (assistantMessage.hasToolCalls()) {
                        AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().getFirst();
                        ToolCallFunction toolCallFunction = new ToolCallFunction();
                        toolCallFunction.setId(toolCall.id());
                        ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
                        callFunction.setName(toolCall.name());
                        callFunction.setArguments(toolCall.arguments());
                        toolCallFunction.setFunction(callFunction);
                        tooCalls.add(toolCallFunction);
                    }
                    return List.of(Message.builder()
                            .role(Role.ASSISTANT.getValue())
                            .content(message.getText())
                            .toolCalls(tooCalls)
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

        // 3.处理大模型 functionCall
        if (options instanceof ToolCallingChatOptions toolCallingChatOptions) {
            List<ToolBase> dashscopeFunctions = new ArrayList<>();
            List<ToolCallback> toolCallbacks = toolCallingChatOptions.getToolCallbacks();
            toolCallbacks.forEach(toolCallback -> {
                ToolDefinition toolDefinition = toolCallback.getToolDefinition();
                dashscopeFunctions.add(ToolFunction.builder().function(FunctionDefinition.builder()
                        .name(toolDefinition.name())
                        .description(toolDefinition.description())
                        .parameters(JsonUtils.parseString(toolDefinition.inputSchema()).getAsJsonObject())
                        .build()
                ).build());
            });
            paramBuilder.tools(dashscopeFunctions);
        }


        return paramBuilder;
    }


    public static void main(String[] args) {
        DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
        options.setModel("qwen-max");
        ChatClient chatClient = ChatClient.builder(new DashScopeModel())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(options)
                .defaultTools(new WeatherService()).build();

//        System.out.println(chatClient.prompt()
//                .user("杭州天气怎么样")
//                .call().content());

        chatClient.prompt().user("杭州天气怎么样").stream().content().subscribe(System.out::println);
    }
}
