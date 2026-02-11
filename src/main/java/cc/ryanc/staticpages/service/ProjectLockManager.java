package cc.ryanc.staticpages.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Manages locks for project operations to prevent race conditions during
 * concurrent version activations, uploads, and other file operations.
 * 
 * Uses Guava LoadingCache with automatic time-based eviction (industry best practice).
 * Locks are automatically removed after {@code lockRetentionMillis} of inactivity.
 * 
 * This is the recommended approach over scheduled cleanup or weak references:
 * - No background threads needed
 * - Automatic cleanup during normal operations
 * - Thread-safe with minimal overhead
 * - Battle-tested in production by Google, Netflix, etc.
 * 
 * @author HowieHz
 */
@Slf4j
@Component
public class ProjectLockManager {
    
    @Value("${static-pages.lock.retention-time:3600000}") // Default: 1 hour
    private long lockRetentionMillis;
    
    private final LoadingCache<String, Lock> projectLocks;
    
    /**
     * Constructor that initializes the LoadingCache with automatic eviction.
     */
    public ProjectLockManager(
            @Value("${static-pages.lock.retention-time:3600000}") long lockRetentionMillis) {
        this.lockRetentionMillis = lockRetentionMillis;
        
        this.projectLocks = CacheBuilder.newBuilder()
                .expireAfterAccess(lockRetentionMillis, TimeUnit.MILLISECONDS)
                .build(new CacheLoader<String, Lock>() {
                    @Override
                    public Lock load(String key) {
                        log.debug("Creating new lock for project: {}", key);
                        return new ReentrantLock();
                    }
                });
        
        log.info("ProjectLockManager initialized with {}ms retention time", lockRetentionMillis);
    }
    
    /**
     * Get or create a lock for the given project name.
     * The lock will be automatically removed after retention time of inactivity.
     * 
     * @param projectName the project name
     * @return the lock for the project
     */
    private Lock getLock(String projectName) {
        try {
            return projectLocks.get(projectName);
        } catch (Exception e) {
            log.error("Failed to get lock for project: {}", projectName, e);
            // Fallback to creating a new lock directly
            return new ReentrantLock();
        }
    }
    
    /**
     * Execute a Mono operation with project lock protection.
     * 
     * @param projectName the project name to lock
     * @param operation the operation to execute
     * @param <T> the type of result
     * @return Mono that executes the operation with lock protection
     */
    public <T> Mono<T> withLock(String projectName, Mono<T> operation) {
        return Mono.defer(() -> {
            Lock lock = getLock(projectName);
            log.debug("Acquiring lock for project: {}", projectName);
            lock.lock();
            log.debug("Lock acquired for project: {}", projectName);
            
            return operation
                .doFinally(signalType -> {
                    log.debug("Releasing lock for project: {} (signal: {})", projectName, signalType);
                    lock.unlock();
                    log.debug("Lock released for project: {}", projectName);
                });
        });
    }
    
    /**
     * Remove lock for a project (cleanup).
     * Should only be called when project is deleted.
     * 
     * @param projectName the project name
     */
    public void removeLock(String projectName) {
        projectLocks.invalidate(projectName);
        log.debug("Invalidated lock cache for project: {}", projectName);
    }
    
    /**
     * Get the number of active project locks.
     * Useful for monitoring and debugging.
     * 
     * @return the number of project locks
     */
    public int getActiveLockCount() {
        return (int) projectLocks.size();
    }
}
