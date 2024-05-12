package cc.ryanc.staticpages.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import cc.ryanc.staticpages.extensions.Project;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.infra.BackupRootGetter;

@ExtendWith(MockitoExtension.class)
class PageProjectServiceImplTest {
    @Mock
    private BackupRootGetter backupRootGetter;

    @TempDir
    private Path tempDir;

    @InjectMocks
    private PageProjectServiceImpl pageProjectService;

    @BeforeEach
    void setUp() {
        lenient().when(backupRootGetter.get()).thenReturn(tempDir.resolve("backup"));
    }

    @Test
    void extractProjectFilePath() {
        var project = new Project();
        project.setSpec(new Project.Spec());
        project.getSpec().setDirectory("a/b/c");
        var path = pageProjectService.extractProjectFilePath(project, "d/e/f");

        Path expectedPath = Paths.get(tempDir.toString(), "static", "a", "b", "c", "d", "e", "f");
        assertThat(path).isEqualTo(expectedPath);
    }

    @Test
    void concatPath() {
        var path = PageProjectServiceImpl.concatPath(tempDir);
        assertThat(path).isEqualTo(tempDir);

        path = PageProjectServiceImpl.concatPath(tempDir, "a", "b", "c");
        assertThat(path).isEqualTo(Paths.get(tempDir.toString(), "a", "b", "c"));
    }
}
