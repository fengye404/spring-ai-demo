package top.fengye.controller.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.fengye.controller.model.DashScopeModel;
import top.fengye.controller.tool.WeatherService;

import java.util.List;

/**
 * @author: FengYe
 * @date: 2025/7/9 03:05
 * @description: ChatConfig
 */
@Configuration
public class ChatConfig {
    @Bean
    public ChatClient.Builder chatClientBuilder() {
        DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
        options.setModel("qwen-max");
        return ChatClient.builder(new DashScopeModel()).defaultAdvisors(new SimpleLoggerAdvisor()).defaultOptions(options);
    }
}
