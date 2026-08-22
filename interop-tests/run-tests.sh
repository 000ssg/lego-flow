#!/bin/bash
set -e

# Build classpath from all jars in /jars directory
CLASSPATH="/testclasses"
for jar in /jars/*; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Running with classpath:"
echo "$CLASSPATH" | tr ':' '\n' | head -20
echo "..."

cd /testclasses
java -cp "$CLASSPATH" \
    -Dinterop.amqp.host=rabbitmq \
    -Dinterop.amqp.port=5672 \
    -Dinterop.amqp.username=guest \
    -Dinterop.amqp.password=guest \
    -Dinterop.amqp.queue=interop-amqp091-test \
    -Dinterop.amqp.exchange=amqp091-test-ex \
    org.junit.platform.console.ConsoleLauncher \
    --select-class=ssg.legoflow.interop.amqp.Amqp091InteropTest \
    --include-method-names \
    --details=verbose \
    2>&1
