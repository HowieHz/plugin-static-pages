package cc.ryanc.staticpages.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Manages locks for project operations to prevent race conditions during
 * concurrent version activations, uploads, and other file operations.
 * 
 * Includes automatic cleanup of unused locks to prevent memory leaks.
 * 
 * @author HowieHz
 */
@Slf4j
@Component
public class ProjectLockManager {
    
    private final ConcurrentHashMap<String, LockInfo> projectLocks = new ConcurrentHashMap<>();
    
    @Value("${static-pages.lock.retention-time:3600000}") // Default: 1 hour
    private long lockRetentionMillis;
    
    /**
     * Holds a lock and its last access time for automatic cleanup.
     */
    @Getter
    private static class LockInfo {
        private final ReentrantLock lock;
        private volatile long lastAccessTime;
        
        public LockInfo() {
            this.lock = new ReentrantLock();
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        /**
         * Update the last access time to current time.
         */
        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Get or create a lock for the specified project.
     * 
     * @param projectName the project name
     * @return the lock for this project
     */
    public Lock getLock(String projectName) {
        LockInfo lockInfo = projectLocks.computeIfAbsent(projectName, k -> {
            log.debug("Creating new lock for project: {}", projectName);
            return new LockInfo();
        });
        lockInfo.updateAccessTime();
        return lockInfo.getLock();
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
        LockInfo removed = projectLocks.remove(projectName);
        if (removed != null) {
            log.debug("Removed lock for project: {}", projectName);
        }
    }
    
    /**
     * Get the number of active project locks.
     * Useful for monitoring and debugging.
     * 
     * @return the number of project locks
     */
    public int getActiveLockCount() {
        return projectLocks.size();
    }
    
    /**
     * Scheduled task to clean up unused locks automatically.
     * Runs every 30 minutes by default (configurable via static-pages.lock.cleanup-interval).
     * 
     * Removes locks that:
     * 1. Haven't been accessed for more than the retention time (default: 1 hour)
     * 2. Are not currently held by any thread (safe to remove)
     * 
     * This prevents memory leaks from abandoned locks while maintaining safety.
     */
    @Scheduled(fixedDelayString = "${static-pages.lock.cleanup-interval:1800000}") // Default: 30 minutes
    public void cleanupUnusedLocks() {
        long timeout = lockRetentionMillis;
        long now = System.currentTimeMillis();
        int removedCount = 0;
        
        log.debug("Starting lock cleanup task. Current lock count: {}", projectLocks.size());
        
        for (var entry : projectLocks.entrySet()) {
            String projectName = entry.getKey();
            LockInfo lockInfo = entry.getValue();
            
            // Check if lock hasn't been accessed for longer than retention time
            if ((now - lockInfo.getLastAccessTime()) > timeout) {
                // Try to acquire the lock - if successful, it's not in use
                if (lockInfo.getLock().tryLock()) {
                    try {
                        // Double-check the lock is still in the map and still unused
                        LockInfo current = projectLocks.get(projectName);
                        if (current == lockInfo && 
                            (now - lockInfo.getLastAccessTime()) > timeout) {
                            projectLocks.remove(projectName);
                            removedCount++;
                            log.info("Cleaned up unused lock for project: {} (idle for {} ms)", 
                                projectName, now - lockInfo.getLastAccessTime());
                        }
                    } finally {
                        lockInfo.getLock().unlock();
                    }
                } else {
                    log.debug("Lock for project {} is in use, skipping cleanup", projectName);
                }
            }
        }
        
        log.debug("Lock cleanup completed. Removed: {}, Remaining: {}", 
            removedCount, projectLocks.size());
    }
}
