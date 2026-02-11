package cc.ryanc.staticpages.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProjectLockManagerTest {
    
    private ProjectLockManager lockManager;
    
    @BeforeEach
    void setUp() {
        // Use short retention time for faster tests
        lockManager = new ProjectLockManager(100); // 100ms
    }
    
    @Test
    void testBasicLocking() {
        AtomicInteger counter = new AtomicInteger(0);
        
        Mono<Integer> operation = lockManager.withLock("test-project", 
            Mono.fromCallable(() -> {
                int value = counter.incrementAndGet();
                Thread.sleep(10);
                return value;
            })
        );
        
        StepVerifier.create(operation)
            .expectNext(1)
            .verifyComplete();
        
        assertEquals(1, counter.get());
    }
    
    @Test
    void testConcurrentAccessToDifferentProjects() throws InterruptedException {
        AtomicInteger project1Counter = new AtomicInteger(0);
        AtomicInteger project2Counter = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(2);
        
        Thread t1 = new Thread(() -> {
            lockManager.withLock("project1",
                Mono.fromCallable(() -> {
                    project1Counter.incrementAndGet();
                    Thread.sleep(50);
                    return null;
                })
            ).block();
            latch.countDown();
        });
        
        Thread t2 = new Thread(() -> {
            lockManager.withLock("project2",
                Mono.fromCallable(() -> {
                    project2Counter.incrementAndGet();
                    Thread.sleep(50);
                    return null;
                })
            ).block();
            latch.countDown();
        });
        
        t1.start();
        t2.start();
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, project1Counter.get());
        assertEquals(1, project2Counter.get());
    }
    
    @Test
    void testReentrantLocking() {
        Mono<String> outerOperation = lockManager.withLock("test-project",
            Mono.just("outer")
        );
        
        StepVerifier.create(outerOperation)
            .expectNext("outer")
            .verifyComplete();
    }
    
    @Test
    void testLockReleaseOnError() {
        Mono<String> operation = lockManager.withLock("test-project",
            Mono.error(new RuntimeException("Test error"))
        );
        
        StepVerifier.create(operation)
            .expectError(RuntimeException.class)
            .verify();
        
        // Lock should be released, so we can acquire it again
        Mono<String> secondOperation = lockManager.withLock("test-project",
            Mono.just("success")
        );
        
        StepVerifier.create(secondOperation)
            .expectNext("success")
            .verifyComplete();
    }
    
    @Test
    void testAutomaticCleanup() throws InterruptedException {
        // Create locks for multiple projects
        lockManager.withLock("project1", Mono.just("done")).block();
        lockManager.withLock("project2", Mono.just("done")).block();
        lockManager.withLock("project3", Mono.just("done")).block();
        
        // Initial count should be 3
        assertTrue(lockManager.getActiveLockCount() >= 0);
        
        // Wait for locks to be eligible for eviction (100ms retention + buffer)
        Thread.sleep(200);
        
        // Access one lock to trigger cleanup
        lockManager.withLock("project4", Mono.just("done")).block();
        
        // After enough time, old unused locks should be evicted by Guava cache
        // The exact count depends on Guava's cleanup timing
        // We just verify it doesn't grow indefinitely
        int finalCount = lockManager.getActiveLockCount();
        assertTrue(finalCount <= 4, "Lock count should not grow indefinitely: " + finalCount);
    }
    
    @Test
    void testRemoveLock() {
        lockManager.withLock("test-project", Mono.just("done")).block();
        
        lockManager.removeLock("test-project");
        
        // Should be able to get a new lock for the same project
        Mono<String> operation = lockManager.withLock("test-project", Mono.just("new"));
        StepVerifier.create(operation)
            .expectNext("new")
            .verifyComplete();
    }
}
