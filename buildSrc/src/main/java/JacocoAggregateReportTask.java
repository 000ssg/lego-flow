import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.xml.XMLFormatter;
import java.io.*;
import java.util.*;

public class JacocoAggregateReportTask extends DefaultTask {

    private List<String> execDirPaths = new ArrayList<>();
    private File outputDir;

    public JacocoAggregateReportTask() {
        setDescription("Generate aggregate JaCoCo XML coverage report");
        setGroup("verification");
    }

    public void setExecDirPaths(List<String> paths) { this.execDirPaths = paths; }
    @org.gradle.api.tasks.Input
    public List<String> getExecDirPaths() { return execDirPaths; }
    
    public void setOutputDir(File dir) { this.outputDir = dir; }
    @org.gradle.api.tasks.OutputDirectory
    public File getOutputDir() { return outputDir; }

    @TaskAction
    public void generate() throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Load all .exec files
        ExecFileLoader loader = new ExecFileLoader();
        int fileCount = 0;
        
        for (String execDirPath : execDirPaths) {
            File execDir = new File(execDirPath);
            if (!execDir.exists()) continue;
            File[] execFiles = execDir.listFiles((d, name) -> name.endsWith(".exec"));
            if (execFiles != null) {
                for (File exec : execFiles) {
                    try {
                        loader.load(exec);
                        fileCount++;
                        getLogger().info("Loaded: " + exec.getAbsolutePath());
                    } catch (Exception e) {
                        getLogger().warn("Failed to load " + exec + ": " + e.getMessage());
                    }
                }
            }
        }

        var sessions = loader.getSessionInfoStore().getInfos();
        var execData = loader.getExecutionDataStore().getContents();

        getLogger().info("Sessions: {}, classes: {}, files: {}", 
            sessions.size(), execData.size(), fileCount);

        if (sessions.isEmpty()) {
            getLogger().warn("No coverage data found. Create empty XML for CI compatibility.");
            File xmlFile = new File(outputDir, "jacoco-aggregate.xml");
            try (FileWriter w = new FileWriter(xmlFile)) {
                w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<coverage version=\"0.8.14\" lines-valid=\"0\" lines-covered=\"0\" line-rate=\"0\" " +
                    "branches-valid=\"0\" branches-covered=\"0\" branch-rate=\"0\" complexity=\"0\">\n");
            }
            return;
        }

        // Generate XML report only (HTML formatter has NPE bug in 0.8.x programmatic API)
        File xmlFile = new File(outputDir, "jacoco-aggregate.xml");
        
        try (OutputStream out = new FileOutputStream(xmlFile)) {
            XMLFormatter formatter = new XMLFormatter();
            var visitor = formatter.createVisitor(out);
            visitor.visitInfo(new ArrayList<>(sessions), execData);
            visitor.visitEnd();
            getLogger().info("XML report: " + xmlFile.getAbsolutePath());
        }

        // Also copy to Maven-compatible path for CI artifact upload  
        File mavenPath = new File(outputDir.getParentFile().getParentFile(), 
            "target/site/jacoco/jacoco-aggregate");
        if (!mavenPath.exists()) {
            mavenPath.mkdirs();
        }
        
        // Copy XML to Maven-compatible path  
        try (InputStream is = new FileInputStream(xmlFile);
             OutputStream os = new FileOutputStream(new File(mavenPath, "jacoco-aggregate.xml"))) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
        }

        getLogger().info("Reports generated successfully in: " + outputDir.getAbsolutePath());
    }
}
