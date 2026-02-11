package cc.ryanc.staticpages;

import cc.ryanc.staticpages.extensions.Project;
import cc.ryanc.staticpages.extensions.ProjectVersion;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class StaticPagesPlugin extends BasePlugin {
    private final SchemeManager schemeManager;

    public StaticPagesPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Project.class);
        schemeManager.register(ProjectVersion.class);
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Project.class));
        schemeManager.unregister(Scheme.buildFromType(ProjectVersion.class));
    }
    
    /**
     * Configuration to enable scheduled tasks for automatic lock cleanup.
     */
    @Configuration
    @EnableScheduling
    public static class SchedulingConfiguration {
        // Enables @Scheduled annotation support
    }
}
