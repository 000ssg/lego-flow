# Lego Flow Kafka Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-399-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

Apache Kafka wire protocol module for the Lego Flow framework, providing a single-node broker (with multi-broker simulation), producer, consumer, and admin client.

## Overview

This module implements the Apache Kafka binary protocol over TCP, enabling Java applications to build Kafka-compatible brokers and clients without any external dependencies beyond SLF4J. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
BrokerCluster (multi-broker simulation, leader election, reassignment)
  -> Admin Client / Producer / Consumer (application layer)
    -> Consumer Group Coordinator (group membership, rebalance, offset storage)
      -> Transaction Manager (producer IDs, idempotency, transactions, consumer-in-txn)
        -> Config Manager (broker + topic dynamic configuration)
          -> SASL Auth (PLAIN, SCRAM-SHA-256, credential store)
            -> Partition Log + LogStorage (pluggable: in-memory or mmap files)
              -> Record Batch Codec (v2 format, CRC32C, varint, GZIP)
                -> Wire Protocol Codec (37 API types, frame encoding)
                  -> TCP Transport (service module channels)
```

## Features

- **37 Kafka API types** -- Produce, Fetch, ListOffsets, Metadata, LeaderAndIsr, StopReplica, UpdateMetadata, ControlledShutdown, OffsetCommit, OffsetFetch, FindCoordinator, JoinGroup, Heartbeat, LeaveGroup, SyncGroup, DescribeGroups, ListGroups, SaslHandshake, ApiVersions, CreateTopics, DeleteTopics, DeleteRecords, InitProducerId, OffsetForLeaderEpoch, AddPartitionsToTxn, AddOffsetsToTxn, EndTxn, WriteTxnMarkers, TxnOffsetCommit, DescribeConfigs, AlterConfigs, SaslAuthenticate, CreatePartitions, DeleteGroups, AlterPartitionReassignments, ListPartitionReassignments, OffsetDelete
- **Single-node broker** -- topic management (create/delete/expand), partitioned append-only logs with compaction, consumer group coordination, offset storage, dynamic configuration, virtual thread per connection
- **Multi-broker simulation** -- in-process N-broker cluster with leader election, partition reassignment, controlled shutdown, replica state tracking
- **SASL authentication** -- PLAIN (RFC 4616) and SCRAM-SHA-256 (RFC 7677), per-connection auth state, credential store
- **Producer** -- configurable partitioner (key hash, round-robin), record batching, GZIP compression, configurable acks (0/1/all), retries with backoff, idempotent mode, transactional mode
- **Consumer** -- consumer group join/sync/heartbeat/leave, partition assignment (range/sticky/cooperative-sticky), auto-commit and manual offset commit, seek to offset/beginning/end, rebalance listener, poll loop
- **Admin client** -- topic CRUD, cluster metadata, API version negotiation, consumer group inspection/deletion, offset listing/deletion, coordinator discovery, config describe/alter, partition expansion, record deletion
- **Record batch v2** -- magic=2 format with CRC32C checksums, varint-encoded records, GZIP compression, record headers
- **Idempotent production** -- producer ID + epoch + per-partition sequence numbers, duplicate detection, out-of-order rejection
- **Full transactions** -- InitProducerId with epoch fencing, AddPartitionsToTxn, AddOffsetsToTxn, TxnOffsetCommit (consumer-in-transaction), WriteTxnMarkers, commit/abort lifecycle
- **Consumer groups** -- full rebalance protocol (EMPTY -> PREPARING_REBALANCE -> COMPLETING_REBALANCE -> STABLE -> DEAD), heartbeat-based liveness, session timeout expiry, range/sticky/cooperative assignment strategies (KIP-429)
- **Dynamic configuration** -- per-topic and broker-level config management via DescribeConfigs/AlterConfigs
- **Log compaction** -- key-based deduplication, tombstone handling, triggered by cleanup.policy=compact
- **Pluggable storage** -- `LogStorage` interface with in-memory (default) and memory-mapped file implementations; segment-based files with sparse index and auto-recovery
- **50+ error codes** -- complete Kafka error code enumeration with messages

## Quick Start

### Start a broker and produce/consume

```java
try (var broker = new KafkaBroker("localhost", 9092)) {
    broker.start();
    broker.createTopic("my-topic", 3);

    // Produce
    try (var producer = new KafkaProducer("localhost", 9092, "my-producer")) {
        producer.init();
        producer.send("my-topic", "key-1", "value-1");
    }

    // Consume
    try (var consumer = new KafkaConsumer("localhost", 9092, "my-consumer", "my-group")) {
        consumer.subscribe(List.of("my-topic"));
        List<ConsumerRecord> records = consumer.poll(5000);
        consumer.commitSync();
    }
}
```

### Admin operations

```java
try (var admin = new KafkaAdminClient("localhost", 9092, "admin")) {
    admin.connect();
    admin.createTopic("new-topic", 4);
    var metadata = admin.metadata(null); // all topics
    admin.deleteTopics(List.of("old-topic"));
}
```

### Idempotent producer

```java
try (var producer = new KafkaProducer("localhost", port, "idempotent-producer",
        Partitioner.keyHash(), (short) -1, 3, 100, Compression.NONE, true, null)) {
    producer.init(); // obtains producer ID + epoch
    producer.send("my-topic", "key", "value"); // deduped by sequence number
}
```

### Transactional producer

```java
try (var producer = new KafkaProducer("localhost", port, "txn-producer",
        Partitioner.roundRobin(), (short) -1, 3, 100, Compression.NONE, true, "my-txn")) {
    producer.init();
    producer.beginTransaction();
    producer.addPartitionsToTransaction(List.of(new TopicPartition("topic", 0)));
    producer.send("topic", "key", "value");
    producer.commitTransaction(); // or abortTransaction()
}
```

### Consumer with rebalance listener

```java
try (var consumer = new KafkaConsumer("localhost", port, "client", "group")) {
    consumer.setRebalanceListener(new RebalanceListener() {
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            System.out.println("Assigned: " + partitions);
        }
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            System.out.println("Revoked: " + partitions);
        }
    });
    consumer.subscribe(List.of("my-topic"));
    while (true) {
        List<ConsumerRecord> records = consumer.poll(1000);
        for (var rec : records) {
            System.out.println(rec.keyAsString() + " -> " + rec.valueAsString());
        }
    }
}
```

## Package Structure

```
ssg.legoflow.messaging.kafka/
├── auth/              -- SASL authentication: PlainSaslServer, ScramSha256Server, CredentialStore
├── broker/            -- Broker: KafkaBroker, ConsumerGroupCoordinator, PartitionLog, TransactionManager, ConfigManager, BrokerCluster, ReplicaManager, PartitionAssigner
├── client/            -- Clients: KafkaProducer, KafkaConsumer, KafkaAdminClient, KafkaConnection
├── codec/             -- Wire protocol codec: encode/decode for all 37 API types
├── common/            -- Shared types: ApiKey (37 keys), KafkaErrors, TopicPartition, Node, Partitioner
├── protocol/          -- Request/response records: 74 record classes (37 request + 37 response)
├── record/            -- Record batch: RecordBatch (v2), Record, Header, Compression
└── demo/              -- Demo applications and examples
```

## Demo Applications

1. **SimpleProducerConsumerDemo** -- Starts broker, produces messages with string keys/values, consumes them with consumer group
2. **AdminClientDemo** -- API version negotiation, topic create/delete, metadata inspection
3. **TransactionalProducerDemo** -- Transactional producer with commit/abort lifecycle

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- TCP transport, lifecycle management, virtual threads

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
