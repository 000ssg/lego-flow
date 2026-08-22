import socket
import struct
import sys

print("=== Python AMQP test ===", file=sys.stderr)

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(10)
s.connect(('rabbitmq', 5672))
print("Connected", file=sys.stderr)

s.setblocking(False)
try:
    data = s.recv(4096)
    print(f"Received {len(data)} bytes", file=sys.stderr)
    if len(data) > 0:
        print(f"Raw: {data[:20].hex()}", file=sys.stderr)
        if len(data) >= 4:
            size, = struct.unpack('>I', data[0:4])
            frame_type = data[4]
            print(f"Size={size} type=0x{frame_type:02x}", file=sys.stderr)
        sys.stdout.write(data.decode('latin-1'))
    else:
        print("No data", file=sys.stderr)
except BlockingIOError:
    print("Would block", file=sys.stderr)
except Exception as e:
    print(f"Error: {e}", file=sys.stderr)

s.close()
