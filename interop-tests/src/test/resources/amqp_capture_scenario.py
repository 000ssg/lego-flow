#!/usr/bin/env python3
"""
Simple AMQP 1.0 client scenario for wire capture.
Connects to RabbitMQ, sends a message, receives a message, then closes.
Uses aiormq (Python AMQP 1.0 client).

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

    # Create consumer
    async with channel.consume(queue) as queue_iter:
        print("Consumer started", flush=True)

        # Publish a message
        message = aiormq.NewMessage(
            "Hello from Python aiormq reference client",
            exchange="",
            routing_key=queue,
        )
        await channel.default_exchange.publish(message, routing_key=queue)
        print("Message published", flush=True)

        # Receive the message
        try:
            received = await asyncio.wait_for(queue_iter.__anext__(), timeout=5.0)
            print(f"Message received: {received.body.decode()}", flush=True)
            await received.ack()
            print("Message acked", flush=True)
        except asyncio.TimeoutError:
            print("Timeout waiting for message", flush=True)

    # Close channel and connection
    await channel.close()
    print("Channel closed", flush=True)
    await connection.close()
    print("Connection closed", flush=True)

if __name__ == "__main__":
    asyncio.run(main())
