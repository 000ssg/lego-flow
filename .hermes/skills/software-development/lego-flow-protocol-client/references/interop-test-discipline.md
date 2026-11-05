# Interop Test Discipline

## Scope rule
When a protocol client already works against the primary reference server (e.g., Artemis for AMQP 1.0), interop test changes for secondary brokers should be **TEST-ONLY**. Fix addressing, broker mode, or pre-declared resources in the test/docker-compose, not in core client code. The primary reference defines correctness.

Before modifying core protocol code for interop:
1. Verify the change is needed for the PRIMARY reference, not just a secondary one
2. If only a secondary broker fails, fix the test configuration
3. Pre-declare queues/exchanges in docker-compose entrypoint
4. Verify the actual plugin/version running in the container before assuming API behavior

## Docker automation
Always use `docker-compose.yml` to provision reference servers. Include resource auto-creation in the entrypoint:

```yaml
entrypoint: /bin/sh -c "
  ./docker-entrypoint.sh &&
  rabbitmq-plugins enable --online rabbitmq_amqp1_0 &&
  rabbitmqadmin declare queue --name interop-test-queue --durable true &&
  exec rabbitmq-server"
```

## Commit verification
Never commit before verifying ALL tests pass:
1. Run `./gradlew test` — check for failures
2. Run `mvn test -pl '!benchmarks'` — check for failures
3. Fix EVERYTHING that fails, including pre-existing bugs
4. Only then update docs and commit

## Common anti-patterns to avoid
- Committing after fixing only your changes while ignoring pre-existing failures
- Modifying core protocol code to work around secondary broker quirks
- Assuming plugin names/versions without verifying inside the container
- Committing without running both Maven and Gradle test suites
