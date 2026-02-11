package cc.ryanc.staticpages.extensions;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

@Component
@RequiredArgsConstructor
public class ProjectVersionReconciler implements Reconciler<Reconciler.Request> {
    private final ExtensionClient client;
    
    @Override
    public Result reconcile(Request request) {
        return client.fetch(ProjectVersion.class, request.name())
            .map(version -> {
                // Update last modified timestamp
                if (version.getStatus() == null) {
                    version.setStatus(new ProjectVersion.Status());
                }
                version.getStatus().setLastModified(Instant.now());
                client.update(version);
                return Result.doNotRetry();
            })
            .orElse(Result.doNotRetry());
    }
    
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new ProjectVersion())
            .build();
    }
}
