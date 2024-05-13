package cc.ryanc.staticpages.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProjectFile {
    @EqualsAndHashCode.Include
    private String name;

    /**
     * 文件或目录的完整路径
     */
    @EqualsAndHashCode.Include
    private String path;

    private boolean isDirectory;

    private long size;
    /**
     * 文件类型（如 txt, jpg, java 等）
     */
    private String type;

    private Instant lastModifiedTime;

    private boolean canRead;

    private boolean canWrite;
}
