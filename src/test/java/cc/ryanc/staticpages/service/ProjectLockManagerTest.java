package cc.ryanc.staticpages.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Test concurrent operations with ProjectLockManager.
 */
class ProjectLockManagerTest {
    
    private ProjectLockManager lockManager;
    
    @BeforeEach
    void setUp() {
        lockManager = new ProjectLockManager();
    }
    
    @Test
    void testBasicLocking() {
        String projectName = "test-project";
        AtomicInteger counter = new AtomicInteger(0);
        
        Mono<Integer> operation = Mono.defer(() -> {
            int value = counter.incrementAndGet();
            return Mono.just(value).delayElement(Duration.ofMillis(50));
        });
        
        // Execute two operations concurrently with lock
        Mono<Integer> op1 = lockManager.withLock(projectName, operation);
        Mono<Integer> op2 = lockManager.withLock(projectName, operation);
        
        StepVerifier.create(Mono.zip(op1, op2))
            .assertNext(tuple -> {
                // Both operations should complete
                assertNotNull(tuple.getT1());
                assertNotNull(tuple.getT2());
                // Values should be sequential (lock prevented race condition)
                assertTrue(Math.abs(tuple.getT1() - tuple.getT2()) == 1);
            })
            .verifyComplete();
    }
    
    @Test
    void testConcurrentProjectLocks() throws InterruptedException {
        String project1 = "project-1";
        String project2 = "project-2";
        
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger project1Counter = new AtomicInteger(0);
        AtomicInteger project2Counter = new AtomicInteger(0);
        
        // Operations on different projects should not block each other
        Mono<Void> op1 = lockManager.withLock(project1, Mono.defer(() -> {
            project1Counter.incrementAndGet();
            return Mono.delay(Duration.ofMillis(100)).then();
        })).doFinally(signal -> latch.countDown());
        
        Mono<Void> op2 = lockManager.withLock(project2, Mono.defer(() -> {
            project2Counter.incrementAndGet();
            return Mono.delay(Duration.ofMillis(100)).then();
        })).doFinally(signal -> latch.countDown());
        
        // Start both operations
        op1.subscribe();
        op2.subscribe();
        
        // Both should complete within reasonable time
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        assertEquals(1, project1Counter.get());
        assertEquals(1, project2Counter.get());
    }
    
    @Test
    void testLockReentrancy() {
        String projectName = "test-project";
        
        // Nested lock acquisition should work (ReentrantLock)
        Mono<String> operation = lockManager.withLock(projectName,
            Mono.just("inner")
        );
        
        Mono<String> outerOperation = lockManager.withLock(projectName,
            operation.map(s -> "outer-" + s)
        );
        
        StepVerifier.create(outerOperation)
            .expectNext("outer-inner")
            .verifyComplete();
    }
    
    @Test
    void testLockReleasedOnError() {
        String projectName = "test-project";
        AtomicInteger attempts = new AtomicInteger(0);
        
        // First operation fails
        Mono<String> failingOp = lockManager.withLock(projectName,
            Mono.defer(() -> {
                attempts.incrementAndGet();
                return Mono.error(new RuntimeException("Test error"));
            })
        );
        
        // Second operation should succeed (lock was released after first error)
        Mono<String> successOp = lockManager.withLock(projectName,
            Mono.defer(() -> {
                attempts.incrementAndGet();
                return Mono.just("success");
            })
        );
        
        StepVerifier.create(failingOp)
            .expectError(RuntimeException.class)
            .verify();
        
        StepVerifier.create(successOp)
            .expectNext("success")
            .verifyComplete();
        
        assertEquals(2, attempts.get());
    }
    
    @Test
    void testGetActiveLockCount() {
        assertEquals(0, lockManager.getActiveLockCount());
        
        lockManager.getLock("project-1");
        assertEquals(1, lockManager.getActiveLockCount());
        
        lockManager.getLock("project-2");
        assertEquals(2, lockManager.getActiveLockCount());
        
        // Getting same lock again doesn't increase count
        lockManager.getLock("project-1");
        assertEquals(2, lockManager.getActiveLockCount());
    }
    
    @Test
    void testRemoveLock() {
        String projectName = "test-project";
        
        lockManager.getLock(projectName);
        assertEquals(1, lockManager.getActiveLockCount());
        
        lockManager.removeLock(projectName);
        assertEquals(0, lockManager.getActiveLockCount());
        
        // Removing non-existent lock should not error
        lockManager.removeLock("non-existent");
        assertEquals(0, lockManager.getActiveLockCount());
    }
}
