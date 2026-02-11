package cc.ryanc.staticpages.service;

import cc.ryanc.staticpages.extensions.ProjectVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VersionService {
    
    /**
     * Create a new version for a project
     * @param projectName the project name
     * @param description version description
     * @return the created version
     */
    Mono<ProjectVersion> createVersion(String projectName, String description);
    
    /**
     * List all versions for a project
     * @param projectName the project name
     * @return flux of versions
     */
    Flux<ProjectVersion> listVersions(String projectName);
    
    /**
     * Activate a specific version
     * @param versionName the version name
     * @return the activated version
     */
    Mono<ProjectVersion> activateVersion(String versionName);
    
    /**
     * Delete a specific version
     * @param versionName the version name
     * @return void
     */
    Mono<Void> deleteVersion(String versionName);
    
    /**
     * Get active version for a project
     * @param projectName the project name
     * @return the active version, or empty if none
     */
    Mono<ProjectVersion> getActiveVersion(String projectName);
    
    /**
     * Cleanup old versions based on maxVersions setting
     * @param projectName the project name
     * @return void
     */
    Mono<Void> cleanupOldVersions(String projectName);
    
    /**
     * Get next version number for a project
     * @param projectName the project name
     * @return the next version number
     */
    Mono<Integer> getNextVersionNumber(String projectName);
}
