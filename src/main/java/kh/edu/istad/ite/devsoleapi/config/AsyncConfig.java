package kh.edu.istad.ite.devsoleapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }

    /**
     * Outbound SMTP, kept off both the request threads and the SSE pool: a
     * slow mail server should cost a few idle threads here, nothing else.
     *
     * <p>Named because {@code @Async} cannot choose between this and
     * {@link #applicationTaskExecutor()} on its own — mail senders ask for it
     * by name with {@code @Async("mailTaskExecutor")}.
     *
     * <p>Caller-runs on a full queue: the worst case is the thread that
     * triggered the email sending it itself, which beats dropping somebody's
     * invitation on the floor.
     */
    @Bean
    public ThreadPoolTaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.initialize();
        return executor;
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setDefaultTimeout(-1);
            }
        };
    }
}
