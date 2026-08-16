package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;

/**
 * Aggregate test that runs all cluster demo tests via delegation.
 *
 * <p>Use to run the full cluster demo test suite:
 * <pre>./gradlew :demos:test --tests "ssg.legoflow.demos.cluster.DemoClusterAllTest"</pre>
 */
class DemoClusterAllTest {

    @Test void clusterSimulation() throws Exception {
        new ClusterSimulationDemoTest().testDemoRunsSuccessfully();
    }

    @Test void distributedLeaderElection() throws Exception {
        new DistributedLeaderElectionDemoTest().testDemoRunsSuccessfully();
    }

    @Test void grpcMicroserviceCluster() throws Exception {
        new GrpcMicroserviceClusterDemoTest().testDemoRunsSuccessfully();
    }

    @Test void autoDiscoveringWebCluster() throws Exception {
        new AutoDiscoveringWebClusterDemoTest().testDemoRunsSuccessfully();
    }

    @Test void partitionTolerance() throws Exception {
        new PartitionToleranceDemoTest().testDemoRunsSuccessfully();
    }

    @Test void dnsSdDiscovery() throws Exception {
        new DnsSdDiscoveryDemoTest().testDnsSdConfigBuilder();
    }

    @Test void etcdCoordination() throws Exception {
        new EtcdCoordinationDemoTest().testKeyValueStorePutGet();
    }

    @Test void clusterDemoRunner() throws Exception {
        new ClusterDemoRunnerTest().testAllDemosRunnable();
    }
}
