#!/usr/bin/env python3
"""
AMQP 1.0 reference client scenario for wire capture using Qpid Proton.
Qpid Proton is a verified AMQP 1.0 implementation (ISO/IEC 19464-1).

Connects to Artemis AMQP 1.0 broker, sends a message, receives it, then closes.
Prints status messages to stdout for synchronization.

Usage: python3 amqp10_capture_scenario.py <host> <port>
"""
import sys
import proton

def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5672
    queue = "wire-capture-test-queue-10"
    container = "proton-ref-client"

    # Build container
    c = proton.Container(container)
    print(f"Connecting to {host}:{port} ...", flush=True)

    # Connect
    conn = c.connect(host=host, port=port)
    print("Connected", flush=True)

    # Wait for connection open
    while not conn.opened():
        c.process(1)
    print("Connection opened", flush=True)

    # Open session
    session = conn.session()
    print("Session opened", flush=True)

    # Open sender link
    sender = session.sender(queue)
    print("Sender link opened", flush=True)

    # Open receiver link
    receiver = session.receiver(queue)
    print("Receiver link opened", flush=True)

    # Flow to get credit
    while not receiver.credit:
        c.process(1)
    print("Receiver has credit", flush=True)

    # Send a message
    msg = proton.Message(
        address=queue,
        subject="Reference AMQP 1.0 test",
        body="Hello from Qpid Proton AMQP 1.0 reference client"
    )
    sender.send(msg)
    print("Message sent", flush=True)

    # Wait for the message to arrive
    while not receiver.fetch():
        c.process(1)
    received = receiver.fetch()
    print(f"Message received: {received.body}", flush=True)

    # Accept
    receiver.accept()
    print("Message accepted", flush=True)

    # Close
    sender.close()
    receiver.close()
    session.close()
    conn.close()
    print("All closed", flush=True)

if __name__ == "__main__":
    main()
