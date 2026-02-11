package cc.ryanc.staticpages.extensions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

/**
 * Reconciler for ProjectVersion extension.
 * <p>
 * This is intentionally a no-op reconciler. The ProjectVersion lifecycle
 * (creation, activation, deletion) is fully managed by {@link cc.ryanc.staticpages.service.VersionService}.
 * <p>
 * Previous implementation attempted to update a lastModified timestamp on every reconciliation,
 * which caused:
 * <ul>
 *   <li>Blocking operation timeouts under load</li>
 *   <li>Unnecessary reconciliation loops (update triggers reconcile, which triggers update, etc.)</li>
 *   <li>Performance degradation</li>
 * </ul>
 * <p>
 * The reconciler component must remain registered for the extension framework,
 * but performs no operations to avoid these issues.
 *
 * @see cc.ryanc.staticpages.service.VersionService
 */
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
