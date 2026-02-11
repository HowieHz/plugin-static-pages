package cc.ryanc.staticpages.extensions;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.NonNull;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "staticpage.halo.run", version = "v1alpha1", kind = "ProjectVersion",
    plural = "projectversions", singular = "projectversion")
public class ProjectVersion extends AbstractExtension {
    
    @Schema(requiredMode = REQUIRED)
    private Spec spec;
    
    @Getter(onMethod_ = @NonNull)
    @Schema(requiredMode = NOT_REQUIRED)
    private Status status = new Status();
    
    public void setStatus(Status status) {
        this.status = (status == null ? new Status() : status);
    }
    
    @Data
    @Schema(name = "ProjectVersionSpec")
    public static class Spec {
        @Schema(requiredMode = REQUIRED, description = "Project name this version belongs to")
        private String projectName;
        
        @Schema(requiredMode = REQUIRED, description = "Version number, incremental")
        private Integer version;
        
        @Schema(requiredMode = NOT_REQUIRED, description = "Version display name")
        private String displayName;
        
        @Schema(requiredMode = NOT_REQUIRED, description = "Directory name where version files are stored")
        private String directory;
        
        @Schema(requiredMode = NOT_REQUIRED, description = "Whether this version is active")
        private Boolean active = false;
        
        @Schema(requiredMode = NOT_REQUIRED, description = "Size of the version in bytes")
        private Long size = 0L;
        
        @Schema(requiredMode = NOT_REQUIRED, description = "Creation timestamp")
        private Instant creationTime;
        
        @Schema(requiredMode = NOT_REQUIRED, description = "Description of this version")
        private String description;
    }
    
    @Data
    @Schema(name = "ProjectVersionStatus")
    public static class Status {
        @Schema(description = "Last modified timestamp")
        private Instant lastModified;
    }
}
