package top.fengye.controller.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author: FengYe
 * @date: 2025/7/9 02:31
 * @description: WeatherService
 */
@Component
public class WeatherService {
    @Tool(name = "获取当前天气", description = "获取当前天气信息")
    public String getCurrentWeather(@ToolParam(description = "城市名称") String location) {
        return "It is sunny in " + location + " today.";
    }
}
