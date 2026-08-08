/**
 * NATS cloud-native messaging protocol implementation.
 *
 * <p>Provides a complete NATS protocol stack including:
 * <ul>
 *   <li>Core text protocol codec (INFO, CONNECT, PUB, SUB, MSG, etc.)
 *   <li>Subject wildcards ({@code *} and {@code >})
 *   <li>Queue group load balancing
 *   <li>Request/reply pattern with automatic inbox management
 *   <li>Server with virtual threads, authentication, and message routing
 *   <li>Client with pub/sub, request/reply, and headers support
 *   <li>JetStream persistent streaming with streams, consumers, and pull subscriptions
 * </ul>
 *
 * @since 0.1.0
 */
package ssg.legoflow.messaging.nats;
