package cc.ryanc.staticpages.service.impl;

import cc.ryanc.staticpages.extensions.Project;
import cc.ryanc.staticpages.extensions.ProjectVersion;
import cc.ryanc.staticpages.service.VersionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.BackupRootGetter;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultVersionService implements VersionService {
    private final ReactiveExtensionClient client;
    private final BackupRootGetter backupRootGetter;
    
    @Override
    public Mono<ProjectVersion> createVersion(String projectName, String description) {
        return getNextVersionNumber(projectName)
            .flatMap(versionNumber -> {
                var version = new ProjectVersion();
                version.setMetadata(new Metadata());
                version.getMetadata().setGenerateName(projectName + "-version-");
                
                var spec = new ProjectVersion.Spec();
                spec.setProjectName(projectName);
                spec.setVersion(versionNumber);
                spec.setDisplayName("v" + versionNumber);
                spec.setDirectory("versions/version-" + versionNumber);
                spec.setActive(false);
                spec.setCreationTime(Instant.now());
                spec.setDescription(description);
                spec.setSize(0L);
                
                version.setSpec(spec);
                
                return client.create(version);
            })
            .flatMap(version -> cleanupOldVersions(projectName).thenReturn(version));
    }
    
    @Override
    public Flux<ProjectVersion> listVersions(String projectName) {
        return client.list(ProjectVersion.class, 
            version -> projectName.equals(version.getSpec().getProjectName()), 
            Comparator.comparing((ProjectVersion v) -> v.getSpec().getVersion()).reversed());
    }
    
    @Override
    public Mono<ProjectVersion> activateVersion(String versionName) {
        return client.get(ProjectVersion.class, versionName)
            .flatMap(version -> {
                var projectName = version.getSpec().getProjectName();
                // Deactivate all other versions
                return listVersions(projectName)
                    .filter(v -> !versionName.equals(v.getMetadata().getName()))
                    .filter(v -> Boolean.TRUE.equals(v.getSpec().getActive()))
                    .flatMap(v -> {
                        v.getSpec().setActive(false);
                        return client.update(v);
                    })
                    .then(Mono.defer(() -> {
                        // Activate this version
                        version.getSpec().setActive(true);
                        return client.update(version)
                            .flatMap(v -> updateSymbolicLink(projectName, v).thenReturn(v));
                    }));
            });
    }
    
    @Override
    public Mono<Void> deleteVersion(String versionName) {
        return client.get(ProjectVersion.class, versionName)
            .flatMap(version -> {
                var projectName = version.getSpec().getProjectName();
                var versionDir = version.getSpec().getDirectory();
                
                // Don't allow deleting active version
                if (Boolean.TRUE.equals(version.getSpec().getActive())) {
                    return Mono.error(new IllegalStateException(
                        "Cannot delete active version. Please activate another version first."));
                }
                
                // Delete the version directory
                return client.get(Project.class, projectName)
                    .flatMap(project -> {
                        Path versionPath = getStaticRootPath()
                            .resolve(project.getSpec().getDirectory())
                            .resolve(versionDir);
                        
                        return Mono.fromCallable(() -> {
                            FileSystemUtils.deleteRecursively(versionPath);
                            return true;
                        }).subscribeOn(Schedulers.boundedElastic());
                    })
                    .then(client.delete(version));
            });
    }
    
    @Override
    public Mono<ProjectVersion> getActiveVersion(String projectName) {
        return listVersions(projectName)
            .filter(v -> Boolean.TRUE.equals(v.getSpec().getActive()))
            .next();
    }
    
    @Override
    public Mono<Void> cleanupOldVersions(String projectName) {
        return client.get(Project.class, projectName)
            .flatMap(project -> {
                Integer maxVersions = project.getSpec().getMaxVersions();
                if (maxVersions == null || maxVersions <= 0) {
                    // Unlimited versions
                    return Mono.empty();
                }
                
                return listVersions(projectName)
                    .collectList()
                    .flatMapMany(versions -> {
                        if (versions.size() <= maxVersions) {
                            return Flux.empty();
                        }
                        
                        // Sort by version number descending, keep the newest maxVersions
                        versions.sort(Comparator.comparing(
                            (ProjectVersion v) -> v.getSpec().getVersion()).reversed());
                        
                        // Delete versions beyond maxVersions limit
                        return Flux.fromIterable(versions.subList(maxVersions, versions.size()))
                            .filter(v -> !Boolean.TRUE.equals(v.getSpec().getActive()))
                            .flatMap(v -> deleteVersion(v.getMetadata().getName())
                                .onErrorResume(e -> {
                                    log.warn("Failed to delete old version {}: {}", 
                                        v.getMetadata().getName(), e.getMessage());
                                    return Mono.empty();
                                }));
                    })
                    .then();
            });
    }
    
    @Override
    public Mono<Integer> getNextVersionNumber(String projectName) {
        return listVersions(projectName)
            .map(v -> v.getSpec().getVersion())
            .reduce(0, Math::max)
            .map(max -> max + 1);
    }
    
    /**
     * Update symbolic link to point to active version
     */
    private Mono<Void> updateSymbolicLink(String projectName, ProjectVersion version) {
        return client.get(Project.class, projectName)
            .flatMap(project -> Mono.fromCallable(() -> {
                Path projectPath = getStaticRootPath().resolve(project.getSpec().getDirectory());
                Path currentLink = projectPath.resolve("current");
                Path versionPath = projectPath.resolve(version.getSpec().getDirectory());
                
                try {
                    // Remove existing link if it exists
                    if (Files.exists(currentLink)) {
                        if (Files.isSymbolicLink(currentLink)) {
                            Files.delete(currentLink);
                        } else {
                            // If it's a regular directory, we need to handle this differently
                            log.warn("'current' exists but is not a symbolic link, deleting it");
                            FileSystemUtils.deleteRecursively(currentLink);
                        }
                    }
                    
                    // Create new symbolic link
                    Files.createSymbolicLink(currentLink, versionPath);
                    log.info("Updated symbolic link for project {} to version {}", 
                        projectName, version.getSpec().getVersion());
                } catch (IOException e) {
                    throw Exceptions.propagate(e);
                }
                return null;
            }).subscribeOn(Schedulers.boundedElastic()).then());
    }
    
    private Path getStaticRootPath() {
        return backupRootGetter.get().getParent().resolve("static");
    }
}
