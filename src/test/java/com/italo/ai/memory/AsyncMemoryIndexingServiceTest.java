package com.italo.ai.memory;

import com.italo.ai.memory.indexing.*;
import com.italo.ai.memory.model.MemoryType;
import com.italo.ai.memory.service.MemoryService.MemoryView;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncMemoryIndexingServiceTest {
    @Test void returnsBeforeIndexingCompletesOnAnotherThread() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>();
        var indexer = mock(MemoryIndexer.class);
        var memory = new MemoryView(UUID.randomUUID(), "alice", Instant.now(), MemoryType.FACT, "usuário", "vive em", "Recife", null);
        doAnswer(invocation -> {
            worker.set(Thread.currentThread());
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("indexação não liberada");
            return null;
        }).when(indexer).index(List.of(memory));
        try (var context = context(indexer)) {
            var future = context.getBean(AsyncMemoryIndexingService.class).index(List.of(memory));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(worker.get()).isNotSameAs(Thread.currentThread());
                assertThat(future).isNotDone();
            } finally {
                release.countDown();
            }
            future.get(5, TimeUnit.SECONDS);
            verify(indexer).index(List.of(memory));
        }
    }

    @Test void failedIndexIsLoggedWithoutRetryOrFailedFuture() throws Exception {
        var indexer = mock(MemoryIndexer.class);
        var memory = new MemoryView(UUID.randomUUID(), "alice", Instant.now(), MemoryType.FACT, "usuário", "vive em", "Recife", null);
        doThrow(new IllegalStateException("offline")).when(indexer).index(List.of(memory));
        try (var context = context(indexer)) {
            context.getBean(AsyncMemoryIndexingService.class).index(List.of(memory)).get(5, TimeUnit.SECONDS);
            verify(indexer, times(1)).index(List.of(memory));
        }
    }

    private AnnotationConfigApplicationContext context(MemoryIndexer indexer) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(MemoryIndexer.class, () -> indexer);
        context.register(AsyncConfig.class, AsyncMemoryIndexingService.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableAsync
    static class AsyncConfig {
        @Bean ThreadPoolTaskExecutor taskExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setThreadNamePrefix("memory-test-");
            return executor;
        }
    }
}
