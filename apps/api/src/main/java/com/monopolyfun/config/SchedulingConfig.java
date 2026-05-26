package com.monopolyfun.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "monopolyfun.scheduler.enabled", havingValue = "true")
public class SchedulingConfig {
    // 中文注释：定时任务只在显式启用时注册，避免本地风险排查启动 API 时推进真实业务状态。
}
