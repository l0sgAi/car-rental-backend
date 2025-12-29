package com.losgai.sys.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("aiWorkerExecutor")
    public Executor aiWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4); // TODO 调整线程池设置
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ai-worker-");
        // 默认拒绝策略，队列满了直接抛异常
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * JDK 21 新特性：为每个任务创建一个新的虚拟线程
     * 不再需要 corePoolSize, maxPoolSize 等参数
     */
    @Bean("vtExecutor")
    public ExecutorService vtExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
}
