/**
 * Redis RESP2/RESP3 wire protocol implementation.
 *
 * <p>Provides a complete Redis-compatible server and client with support
 * for all major Redis data types (strings, lists, sets, sorted sets,
 * hashes, streams), pub/sub messaging, transactions with WATCH-based
 * optimistic locking, pipelining, and minimal cluster protocol support.
 *
 * <h2>Package Structure</h2>
 * <ul>
 *   <li>{@code protocol} - RESP wire protocol codec and parser</li>
 *   <li>{@code command} - Command definitions, registry, and argument parsing</li>
 *   <li>{@code server} - TCP server with virtual threads, key-value store, expiration</li>
 *   <li>{@code server.impl} - Command handler implementations by category</li>
 *   <li>{@code client} - Client with convenience methods, pipelining, pub/sub</li>
 *   <li>{@code cluster} - Hash slot calculation and cluster topology</li>
 *   <li>{@code demo} - Demonstration applications</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.database.redis;
