# AMQP Flakiness Investigation Log

## Error Analysis
```
Invalid header from server: [0, 0, 0, 28, 2, 1, 0, 0]
```

This is a frame header, not a protocol header:
- Bytes 0-3: `0, 0, 0, 28` = size = 28
- Byte 4: `2` = DOFF
- Byte 5: `1` = frame type (AMQP, not SASL)
- Bytes 6-7: `0, 0` = channel

## Hypothesis
The server is sending an AMQP frame before the client has sent its protocol header (SASL_HEADER). This suggests:
1. Server's executor thread starts before client sends SASL_HEADER
2. Server sends frame prematurely
3. Or data leakage from previous connection

## Execution Trace Needed
Need to capture:
1. When client sends SASL_HEADER
2. When server reads SASL_HEADER
3. When server sends SASL_HEADER echo
4. When server sends AMQP frame

## Test Results
- `testMultipleClients` fails ~20% of the time
- Other tests in same class pass consistently
- Full suite passes (771 tests)

## Next Steps
1. Add nanosecond-precision timestamps to trace execution order
2. Check if server connection pool has stale state
3. Verify no data leakage between connections
4. Check if executor submission causes race condition
