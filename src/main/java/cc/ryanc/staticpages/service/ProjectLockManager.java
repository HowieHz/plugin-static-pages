package cc.ryanc.staticpages.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Manages locks for project operations to prevent race conditions during
 * concurrent version activations, uploads, and other file operations.
 * 
 * @author HowieHz
 */
@Slf4j
@Component
public class ProjectLockManager {
    
    private final ConcurrentHashMap<String, Lock> projectLocks = new ConcurrentHashMap<>();
    
    /**
     * Get or create a lock for the specified project.
     * 
     * @param projectName the project name
     * @return the lock for this project
     */
    public Lock getLock(String projectName) {
        return projectLocks.computeIfAbsent(projectName, k -> {
            log.debug("Creating new lock for project: {}", projectName);
            return new ReentrantLock();
        });
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
        Lock removed = projectLocks.remove(projectName);
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
}
