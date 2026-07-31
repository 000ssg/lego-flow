import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.IReportGroupVisitor;

import java.io.*;
import java.util.*;

public class JacocoAggregateReportTask extends DefaultTask {

    private List<String> execDirPaths = new ArrayList<>();
    private File outputDir;

    public JacocoAggregateReportTask() {
        setDescription("Generate aggregate JaCoCo coverage report (HTML + XML)");
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
        var dataStore = loader.getExecutionDataStore();
        var execData = dataStore.getContents();

        getLogger().info("Sessions: {}, classes: {}, files: {}",
            sessions.size(), execData.size(), fileCount);

        if (sessions.isEmpty()) {
            getLogger().warn("No coverage data found.");
            createPlaceholderReport();
            return;
        }

        // Generate XML report
        generateXmlReport(sessions, execData);

        // Generate HTML report with proper visitor lifecycle
        generateHtmlReport(loader, sessions, execData);

        getLogger().info("Reports generated in: " + outputDir.getAbsolutePath());
    }

    private void generateXmlReport(
            List<org.jacoco.core.data.SessionInfo> sessions,
            Collection<org.jacoco.core.data.ExecutionData> execData) throws Exception {
        File xmlFile = new File(outputDir, "jacoco-aggregate.xml");
        try (OutputStream out = new FileOutputStream(xmlFile)) {
            XMLFormatter formatter = new XMLFormatter();
            IReportVisitor visitor = formatter.createVisitor(out);
            visitor.visitInfo(new ArrayList<>(sessions), execData);
            visitor.visitGroup("Lego Flow"); // Required before visitEnd
            visitor.visitEnd();
        }
        getLogger().info("XML report: " + xmlFile.getAbsolutePath());
    }

    private void generateHtmlReport(
            ExecFileLoader loader,
            List<org.jacoco.core.data.SessionInfo> sessions,
            Collection<org.jacoco.core.data.ExecutionData> execData) throws Exception {
        File htmlDir = new File(outputDir, "html");
        if (!htmlDir.exists()) {
            htmlDir.mkdirs();
        }

        HTMLFormatter formatter = new HTMLFormatter();
        FileMultiReportOutput output = new FileMultiReportOutput(htmlDir);
        IReportVisitor visitor = formatter.createVisitor(output);
        visitor.visitInfo(new ArrayList<>(sessions), execData);

        // CRITICAL: Must call visitGroup() before visitEnd() to initialize SessionsPage.
        // Without this, HTMLFormatter throws NPE at visitEnd().
        IReportGroupVisitor group = visitor.visitGroup("Lego Flow");

        // Optional: Analyze class files for coverage data (source highlighting)
        try {
            var dataStore = loader.getExecutionDataStore();
            CoverageBuilder builder = new CoverageBuilder();
            Analyzer analyzer = new Analyzer(dataStore, builder);
            
            List<File> classDirs = collectClassDirectories();
            int analyzed = 0;
            for (File classDir : classDirs) {
                try {
                    analyzeDirectory(analyzer, classDir);
                    analyzed++;
                } catch (Exception e) {
                    // Skip problematic directories silently
                }
            }

            if (!classDirs.isEmpty()) {
                IBundleCoverage bundle = builder.getBundle("Lego Flow");
                File srcRoot = findSourceRoot();
                DirectorySourceFileLocator locator = 
                    new DirectorySourceFileLocator(srcRoot, "UTF-8", 5);
                group.visitBundle(bundle, locator);
                getLogger().info("Analyzed {} modules for coverage", analyzed);
            }
        } catch (Exception e) {
            // Coverage analysis is optional - visitGroup alone prevents NPE
            getLogger().debug("Coverage class analysis skipped: " + e.getMessage());
        }

        visitor.visitEnd();
        output.close();
    }

    private void analyzeDirectory(Analyzer analyzer, File dir) throws Exception {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.getName().endsWith(".class")) {
                try (InputStream is = new FileInputStream(entry)) {
                    analyzer.analyzeAll(is, entry.getName());
                }
            } else if (entry.isDirectory()) {
                analyzeDirectory(analyzer, entry);
            }
        }
    }

    private List<File> collectClassDirectories() {
        List<File> result = new ArrayList<>();
        for (String execDirPath : execDirPaths) {
            String base = execDirPath.contains("/build/jacoco")
                ? execDirPath.substring(0, execDirPath.lastIndexOf("/build/jacoco"))
                : "";
            if (!base.isEmpty()) {
                File classesDir = new File(base, "build/classes/java/main");
                if (classesDir.exists()) {
                    result.add(classesDir);
                }
            }
        }
        return result;
    }

    private File findSourceRoot() {
        // Return project root as source root for source highlighting
        return getProject().getProjectDir();
    }

    private void createPlaceholderReport() throws Exception {
        try (FileWriter w = new FileWriter(new File(outputDir, "jacoco-aggregate.xml"))) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<coverage version=\"0.8.14\" lines-valid=\"0\" lines-covered=\"0\" line-rate=\"0\"></coverage>");
        }
        File htmlDir = new File(outputDir, "html");
        if (!htmlDir.exists()) htmlDir.mkdirs();
        try (FileWriter w = new FileWriter(new File(htmlDir, "index.html"))) {
            w.write("<html><body><h1>No Coverage Data</h1>\n" +
                "<p>Run tests with JaCoCo instrumentation first.</p></body></html>");
        }
    }
}
