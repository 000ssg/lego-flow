import org.gradle.api.Plugin;
import org.gradle.api.Project;
import java.util.ArrayList;
import java.util.List;

public class JacocoReportPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (!project.equals(project.getRootProject())) {
            return; // Only apply to root project
        }

        var jacocoTask = project.getTasks().register("jacocoAggregateReport", JacocoAggregateReportTask.class, task -> {
            List<String> execDirPaths = new ArrayList<>();
            for (Project sub : project.getSubprojects()) {
                if (sub.getPlugins().hasPlugin("java-library") || sub.getPlugins().hasPlugin("java")) {
                    execDirPaths.add(sub.getBuildDir().toPath().resolve("jacoco").toString());
                }
            }
            task.setExecDirPaths(execDirPaths);
            task.setOutputDir(project.getBuildDir().toPath().resolve("jacoco/aggregate").toFile());
        });

        project.getTasks().register("copyJacocoForCI", 
            org.gradle.api.tasks.Copy.class, copy -> {
                var sourceDir = project.getBuildDir().toPath().resolve("jacoco/aggregate/html");
                var destDir = project.getProjectDir().toPath().resolve("target/site/jacoco/jacoco-aggregate");
                
                copy.from(sourceDir);
                copy.into(destDir.toFile());
                copy.dependsOn(jacocoTask);
            });
    }
}
