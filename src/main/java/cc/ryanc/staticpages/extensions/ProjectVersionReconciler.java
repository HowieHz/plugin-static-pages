package cc.ryanc.staticpages.extensions;

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
        // No-op reconciler - ProjectVersion lifecycle is managed by VersionService
        // Attempting to fetch and update on every reconciliation can cause timeouts
        // and unnecessary reconciliation loops
        return Result.doNotRetry();
    }
    
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new ProjectVersion())
            .build();
    }
}
