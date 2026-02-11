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
    private static final String VERSIONS_DIR = "versions";
    private static final String VERSION_DIR_PREFIX = "version-";
    
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
                spec.setDirectory(VERSIONS_DIR + "/" + VERSION_DIR_PREFIX + versionNumber);
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
                            .flatMap(v -> copyVersionToRoot(projectName, v).thenReturn(v));
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
                    .then(client.delete(version).then());
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
     * Copy version files to project root directory
     */
    private Mono<Void> copyVersionToRoot(String projectName, ProjectVersion version) {
        return client.get(Project.class, projectName)
            .flatMap(project -> Mono.fromCallable(() -> {
                Path projectPath = getStaticRootPath().resolve(project.getSpec().getDirectory());
                Path versionPath = projectPath.resolve(version.getSpec().getDirectory());
                
                try {
                    // Create project directory if it doesn't exist
                    Files.createDirectories(projectPath);
                    
                    // Clear project root (except .versions directory)
                    if (Files.exists(projectPath) && Files.isDirectory(projectPath)) {
                        Files.list(projectPath)
                            .filter(path -> !path.getFileName().toString().equals(VERSIONS_DIR))
                            .forEach(path -> {
                                try {
                                    FileSystemUtils.deleteRecursively(path);
                                } catch (IOException e) {
                                    log.warn("Failed to delete {}: {}", path, e.getMessage());
                                }
                            });
                    }
                    
                    // Copy files from version directory to project root
                    if (Files.exists(versionPath) && Files.isDirectory(versionPath)) {
                        Files.walk(versionPath)
                            .forEach(source -> {
                                try {
                                    Path destination = projectPath.resolve(
                                        versionPath.relativize(source));
                                    if (Files.isDirectory(source)) {
                                        Files.createDirectories(destination);
                                    } else {
                                        Files.copy(source, destination, 
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    }
                                } catch (IOException e) {
                                    log.warn("Failed to copy {} to {}: {}", 
                                        source, projectPath, e.getMessage());
                                }
                            });
                    }
                    
                    log.info("Copied version {} to project root for project {}", 
                        version.getSpec().getVersion(), projectName);
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
