# Connection handshake frame sequencing

AMQP 0-9-1 handshake: greeting(8 bytes) -> start -> start-ok -> tune -> tune-ok -> open -> open-ok.

RabbitMQ 4.x may skip greeting response. Client detects by checking if first 7 bytes start with "AMQP" (0x41 0x4D 0x51 0x50). If greeting, read 4 more bytes, then read frame.

Greeting is EXACTLY 8 bytes: 0x41 0x4D 0x51 0x50 0x00 0x00 0x09 0x01. A 9-byte greeting causes immediate EOF.

Tuning: use min(client, server) for channelMax, frameMax, heartbeat. If either heartbeat is 0, disable.