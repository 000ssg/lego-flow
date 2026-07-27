/**
 * Apache Kafka wire protocol implementation.
 *
 * <p>Provides a complete binary protocol codec, broker, producer, consumer,
 * and admin client — all JDK-only, no external dependencies except SLF4J.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@code codec} — Binary encode/decode for all Kafka API request/response types</li>
 *   <li>{@code protocol} — API key enums, request/response records</li>
 *   <li>{@code record} — Kafka v2 record batch format with compression</li>
 *   <li>{@code broker} — TCP server with topic management, consumer groups, transactions</li>
 *   <li>{@code client} — Producer, consumer, admin client</li>
 *   <li>{@code common} — Shared types (TopicPartition, Node, Errors, Partitioner)</li>
 *   <li>{@code demo} — Progressive demo applications</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.messaging.kafka;
