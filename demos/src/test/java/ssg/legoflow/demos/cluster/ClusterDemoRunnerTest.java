package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link ClusterDemoRunner}.
 *
 * Verifies that the runner can orchestrate all demos.
 * Since the runner uses main(), we test individual demos via their run() methods.
 */
class ClusterDemoRunnerTest {

    @Test
    void testRunnerHasMain() {
        var mainMethod = Arrays.stream(ClusterDemoRunner.class.getMethods())
                .filter(m -> m.getName().equals("main"))
                .findFirst();
        assertThat(mainMethod).isPresent();
    }

    @Test
    void testAllDemosRunnable() throws Exception {
        var simDemo = new ClusterSimulationDemo();
        var simResult = simDemo.run();
        assertThat(simResult).isNotNull().isNotEmpty();

        var webDemo = new AutoDiscoveringWebClusterDemo();
        var webResult = webDemo.run();
        assertThat(webResult).isNotNull().isNotEmpty();

        var grpcDemo = new GrpcMicroserviceClusterDemo();
        var grpcResult = grpcDemo.run();
        assertThat(grpcResult).isNotNull().isNotEmpty();

        var electionDemo = new DistributedLeaderElectionDemo();
        var electionResult = electionDemo.run();
        assertThat(electionResult).isNotNull().isNotEmpty();

        var partitionDemo = new PartitionToleranceDemo();
        var partitionResult = partitionDemo.run();
        assertThat(partitionResult).isNotNull().isNotEmpty();
    }
}
