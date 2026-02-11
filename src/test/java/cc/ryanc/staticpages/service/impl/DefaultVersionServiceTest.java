package cc.ryanc.staticpages.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cc.ryanc.staticpages.extensions.Project;
import cc.ryanc.staticpages.extensions.ProjectVersion;
import cc.ryanc.staticpages.service.ProjectLockManager;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.BackupRootGetter;

@ExtendWith(MockitoExtension.class)
class DefaultVersionServiceTest {
    
    @Mock
    private ReactiveExtensionClient client;
    
    @Mock
    private BackupRootGetter backupRootGetter;
    
    @TempDir
    private Path tempDir;
    
    private DefaultVersionService versionService;
    private ProjectLockManager lockManager;
    
    @BeforeEach
    void setUp() {
        lenient().when(backupRootGetter.get()).thenReturn(tempDir.resolve("backup"));
        lockManager = new ProjectLockManager(3600000); // 1 hour
        versionService = new DefaultVersionService(client, backupRootGetter, lockManager);
    }
    
    @Test
    void shouldGetNextVersionNumber() {
        // Given
        String projectName = "test-project";
        
        ProjectVersion v1 = createVersion(projectName, 1);
        ProjectVersion v2 = createVersion(projectName, 2);
        
        when(client.list(eq(ProjectVersion.class), any(Predicate.class), any(Comparator.class)))
            .thenReturn(Flux.just(v2, v1));
        
        // When & Then
        StepVerifier.create(versionService.getNextVersionNumber(projectName))
            .expectNext(3)
            .verifyComplete();
    }
    
    @Test
    void shouldGetNextVersionNumberWhenNoVersions() {
        // Given
        String projectName = "test-project";
        
        when(client.list(eq(ProjectVersion.class), any(Predicate.class), any(Comparator.class)))
            .thenReturn(Flux.empty());
        
        // When & Then
        StepVerifier.create(versionService.getNextVersionNumber(projectName))
            .expectNext(1)
            .verifyComplete();
    }
    
    @Test
    void shouldCreateVersion() {
        // Given
        String projectName = "test-project";
        String description = "Test version";
        
        Project project = new Project();
        project.setSpec(new Project.Spec());
        project.getSpec().setDirectory("test-dir");
        project.getSpec().setMaxVersions(10);
        
        ProjectVersion newVersion = createVersion(projectName, 1);
        
        when(client.list(eq(ProjectVersion.class), any(Predicate.class), any(Comparator.class)))
            .thenReturn(Flux.empty());
        when(client.create(any(ProjectVersion.class))).thenReturn(Mono.just(newVersion));
        when(client.get(Project.class, projectName)).thenReturn(Mono.just(project));
        
        // When & Then
        StepVerifier.create(versionService.createVersion(projectName, description))
            .assertNext(version -> {
                assertThat(version.getSpec().getProjectName()).isEqualTo(projectName);
                assertThat(version.getSpec().getVersion()).isEqualTo(1);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldListVersions() {
        // Given
        String projectName = "test-project";
        
        ProjectVersion v1 = createVersion(projectName, 1);
        ProjectVersion v2 = createVersion(projectName, 2);
        ProjectVersion v3 = createVersion(projectName, 3);
        
        when(client.list(eq(ProjectVersion.class), any(Predicate.class), any(Comparator.class)))
            .thenReturn(Flux.just(v3, v2, v1));
        
        // When & Then
        StepVerifier.create(versionService.listVersions(projectName))
            .expectNext(v3, v2, v1)
            .verifyComplete();
    }
    
    @Test
    void shouldGetActiveVersion() {
        // Given
        String projectName = "test-project";
        
        ProjectVersion v1 = createVersion(projectName, 1);
        ProjectVersion v2 = createVersion(projectName, 2);
        v2.getSpec().setActive(true);
        
        when(client.list(eq(ProjectVersion.class), any(Predicate.class), any(Comparator.class)))
            .thenReturn(Flux.just(v2, v1));
        
        // When & Then
        StepVerifier.create(versionService.getActiveVersion(projectName))
            .expectNext(v2)
            .verifyComplete();
    }
    
    private ProjectVersion createVersion(String projectName, int versionNumber) {
        ProjectVersion version = new ProjectVersion();
        version.setMetadata(new Metadata());
        version.getMetadata().setName(projectName + "-version-" + versionNumber);
        
        ProjectVersion.Spec spec = new ProjectVersion.Spec();
        spec.setProjectName(projectName);
        spec.setVersion(versionNumber);
        spec.setDisplayName("v" + versionNumber);
        spec.setDirectory("versions/version-" + versionNumber);
        spec.setActive(false);
        
        version.setSpec(spec);
        return version;
    }
}
