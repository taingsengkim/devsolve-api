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

    /**
     * The search index sync, kept off the scheduler thread.
     *
     * <p>{@code @Scheduled} methods across the whole application share one
     * thread. A sync pass reads pages out of PostgreSQL and pushes them to
     * Meilisearch, and a rebuild does that for every row of five tables — long
     * enough to hold up every other timer in the application if it ran there.
     *
     * <p>One thread, because {@code SearchIndexSynchronizer} already refuses to
     * start a pass while one is running: a second would have nothing to do.
     * Queue of one for the same reason. Rejections are discarded rather than
     * run on the caller — the caller is the scheduler thread this exists to
     * protect, and a dropped pass costs nothing, since the next tick repeats it.
     */
    @Bean
    public ThreadPoolTaskExecutor searchIndexTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("search-index-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.DiscardPolicy()
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
