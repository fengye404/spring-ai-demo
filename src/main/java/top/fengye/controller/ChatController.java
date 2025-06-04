package top.fengye.controller;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;


/**
 * @author: FengYe
 * @date: 2025/6/2 02:27
 * @description: BailianAgentRagController
 */
@RestController
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(value = "input") String input) {
        return this.chatClient.prompt()
                .user(input)
                .call()
                .content();
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam(value = "input") String input) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

//        // 异步处理流式响应
//        CompletableFuture.runAsync(() -> {
//
//        });


        long start = System.currentTimeMillis();
        try {
            // 使用ChatClient的stream方法获取流式响应
            System.out.println(System.currentTimeMillis()-start);
            Flux<String> contentStream = this.chatClient.prompt()
                    .user(input)
                    .stream()
                    .content();

            System.out.println(System.currentTimeMillis()-start);

            // 处理流式数据
            contentStream.subscribe(
                    content -> {
                        try {
                            // 发送每个内容块
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(content));
                        } catch (IOException e) {
                            logger.error("Error sending SSE data", e);
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        logger.error("Error in stream processing", error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("处理请求时发生错误: " + error.getMessage()));
                        } catch (IOException e) {
                            logger.error("Error sending error event", e);
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        try {
                            // 发送结束事件
                            emitter.send(SseEmitter.event()
                                    .name("end")
                                    .data(""));
                            emitter.complete();
                        } catch (IOException e) {
                            logger.error("Error completing SSE", e);
                            emitter.completeWithError(e);
                        }
                    }
            );

            System.out.println(System.currentTimeMillis()-start);

        } catch (Exception e) {
            logger.error("Error processing chat stream", e);
            emitter.completeWithError(e);
        }


        System.out.println(System.currentTimeMillis()-start);
        return emitter;
    }
}