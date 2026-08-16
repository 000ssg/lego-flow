package ssg.legoflow.demos.cluster;

import java.util.Map;

/**
 * Runner for all cluster protocol demos.
 *
 * <p>Executes each demo in sequence with clear separation.
 * Use individual demo classes' main() methods to run them standalone.
 *
 * <p>Demos:
 * <ol>
 *   <li>{@link ClusterSimulationDemo} — Core cluster membership + consistent hashing</li>
 *   <li>{@link AutoDiscoveringWebClusterDemo} — Web server cluster simulation</li>
 *   <li>{@link GrpcMicroserviceClusterDemo} — gRPC load balancing</li>
 *   <li>{@link DistributedLeaderElectionDemo} — Leader election</li>
 *   <li>{@link PartitionToleranceDemo} — Network partition simulation</li>
 * </ol>
 */
public final class ClusterDemoRunner {

    private ClusterDemoRunner() {}

    /**
     * Runs all cluster demos in sequence.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("========================================================");
        System.out.println("  Lego Flow — Cluster Protocol Demos");
        System.out.println("========================================================");
        System.out.println();

        String filter = args.length > 0 ? args[0] : null;

        runDemo("Cluster Simulation", ClusterSimulationDemo::new, "simulation", filter,
                demo -> ((ClusterSimulationDemo) demo).run());

        runDemo("Auto-Discovering Web Cluster", AutoDiscoveringWebClusterDemo::new, "web", filter,
                demo -> ((AutoDiscoveringWebClusterDemo) demo).run());

        runDemo("gRPC Microservice Cluster", GrpcMicroserviceClusterDemo::new, "grpc", filter,
                demo -> ((GrpcMicroserviceClusterDemo) demo).run());

        runDemo("Distributed Leader Election", DistributedLeaderElectionDemo::new, "election", filter,
                demo -> ((DistributedLeaderElectionDemo) demo).run());

        runDemo("Partition Tolerance", PartitionToleranceDemo::new, "partition", filter,
                demo -> ((PartitionToleranceDemo) demo).run());

        System.out.println();
        System.out.println("=== All demos completed ===");
    }

    @FunctionalInterface
    private interface DemoFactory<T> {
        T create() throws Exception;
    }

    @FunctionalInterface
    private interface DemoRunner<T> {
        Map<String, Object> run(T demo) throws Exception;
    }

    private static <T> void runDemo(String name, DemoFactory<T> factory,
                                     String filterTag, String filter,
                                     DemoRunner<T> runner) throws Exception {
        if (filter != null && !filter.equalsIgnoreCase(filterTag)
                && !filter.equalsIgnoreCase(name)) {
            return;
        }

        System.out.println("--------------------------------------------------------");
        System.out.println("  Demo: " + name);
        System.out.println("--------------------------------------------------------");

        try {
            var demo = factory.create();
            var result = runner.run(demo);
            if (result instanceof Map<?, ?> results) {
                results.forEach((k, v) -> System.out.println("    " + k + " = " + v));
            }
        } catch (Exception e) {
            System.out.println("    Demo failed: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        System.out.println();
    }
}
