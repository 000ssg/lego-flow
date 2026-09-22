#!/usr/bin/env python3
"""
Simple AMQP 1.0 client scenario for wire capture.
Connects to RabbitMQ, sends a message, receives a message, then closes.
Uses aiormq (Python AMQP 1.0 client) — 6.x callback API
(`basic_consume` + `basic_publish` + `basic_ack`).

Usage: python3 amqp_capture_scenario.py <host> <port>
"""
import asyncio
import sys
import aiormq


async def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5672
    queue = "wire-capture-test-queue"

    print(f"Connecting to {host}:{port} ...", flush=True)
    connection = await aiormq.connect(f"amqp://{host}:{port}/")
    print("Connected", flush=True)

    channel = await connection.channel()
    print("Channel opened", flush=True)

    # Declare queue
    await channel.queue_declare(queue, auto_delete=True)
    print(f"Queue declared: {queue}", flush=True)

    # Create consumer (aiormq 6.x: callback-based, no async-iterator)
    received = asyncio.Event()
    msg = {}

    async def on_message(delivered):
        msg["m"] = delivered
        received.set()

    consume_ok = await channel.basic_consume(queue, on_message)
    consumer_tag = consume_ok.consumer_tag
    print("Consumer started", flush=True)

    # Publish a message
    await channel.basic_publish(
        b"Hello from Python aiormq reference client",
        routing_key=queue,
    )
    print("Message published", flush=True)

    # Receive the message
    try:
        await asyncio.wait_for(received.wait(), timeout=5.0)
        delivered = msg["m"]
        print(f"Message received: {delivered.body.decode()}", flush=True)
        await channel.basic_ack(delivered.delivery_tag)
        print("Message acked", flush=True)
    except asyncio.TimeoutError:
        print("Timeout waiting for message", flush=True)

    # Cancel consumer
    await channel.basic_cancel(consumer_tag)
    print("Consumer stopped", flush=True)

    # Close channel and connection
    await channel.close()
    print("Channel closed", flush=True)
    await connection.close()
    print("Connection closed", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
