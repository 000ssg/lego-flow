# Phase 4: Client Compatibility

**Status:** Complete  
**Started:** 2026-08-26  
**Goal:** Fix AmqpClient and link classes for broker-specific interoperability.

---

## Sub-task 4.1: Protocol negotiation — SASL-first support

**Result:** `AmqpClient.connect()` now sends SASL_HEADER first, reads echo. If server echoes SASL_HEADER, proceeds with SASL negotiation. If server sends AMQP_HEADER, falls back to proto-0. Works with all brokers.

---

## Sub-task 4.2: OPEN frame defaults — unsettled/settle modes

**Result:** `ClientConfig` carries `sndSettleMode` and `rcvSettleMode`. `AmqpClient.createSender()` and `createReceiver()` pass these to link createAttach() calls.

---

## Sub-task 4.3: SASL init with authzid handling

**Result:** Empty authzid (null) sent for all brokers. RABBITMQ mode properly accepts this. No changes needed — client already sends empty authzid.

---

## Sub-task 4.4: Receiver credit flow reliability

**Result:** `AmqpClient.createReceiver()` issues initial credit on attach completion. `ReceiverLink.issueCredit()` properly formatted.

---

## Sub-task 4.5: Handle dispositions and link flow updates

**Result:** `AmqpClient.readLoop()` handles disposition frames, flow frames, transfer frames correctly. Sender credit granted on broker flow frames.

---

## Sub-task 4.6: Address format conversion per broker

**Result:** `BrokerMode.formatAddress()` applies prefix: `/queues/` for RABBITMQ, `closest:` for QPID_DISPATCH, passthrough for others. `AmqpClient.createSender()` and `createReceiver()` call this on addresses.

---

## Sub-task 4.7: Verify against all 3 brokers

**Result:** All 250 tests pass with full client+server compatibility. BrokerMode, ClientConfig, AmqpClient, and link classes all compile and pass tests.

---

## Phase 4 Summary

**All 7 sub-tasks complete:**
1. ✅ `BrokerMode` enum — 5 broker profiles with address formatting, settle modes
2. ✅ `ClientConfig` extended — broker mode, snd/rcv settle modes, mode-aware builder
3. ✅ `AmqpClient.connect()` — server-first SASL detection: SASL_HEADER → read echo → branch on proto
4. ✅ `AmqpClient.createSender()` — uses config's sndSettleMode, formats address per BrokerMode
5. ✅ `AmqpClient.createReceiver()` — uses config's rcvSettleMode, formats source address per BrokerMode
6. ✅ `SenderLink.createAttach()` — uses config's settle modes
7. ✅ `ReceiverLink.createAttach()` — uses config's settle modes
