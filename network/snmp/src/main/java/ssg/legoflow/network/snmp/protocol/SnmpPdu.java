package ssg.legoflow.network.snmp.protocol;

/**
 * Sealed interface representing all SNMPv3 Protocol Data Unit (PDU) types.
 *
 * <p>Each permitted implementation corresponds to a specific PDU type defined
 * in RFC 3416. The sealed hierarchy enables exhaustive pattern matching.
 *
 * <p>PDU types use context-specific implicit tags:
 * <ul>
 *   <li>[0] GetRequest-PDU</li>
 *   <li>[1] GetNextRequest-PDU</li>
 *   <li>[2] Response-PDU (GetResponse)</li>
 *   <li>[3] SetRequest-PDU</li>
 *   <li>[5] GetBulkRequest-PDU</li>
 *   <li>[6] InformRequest-PDU</li>
 *   <li>[7] SNMPv2-Trap-PDU</li>
 * </ul>
 *
 * @since 0.1.0
 */
public sealed interface SnmpPdu {

    /**
     * Returns the request ID for this PDU.
     *
     * @return the request ID
     */
    int requestId();

    /**
     * Returns the variable bindings for this PDU.
     *
     * @return the variable binding list
     */
    VarBindList varBindList();

    /**
     * Returns the BER tag number for this PDU type.
     *
     * @return the context-specific tag number
     */
    int tagNumber();

    /**
     * GetRequest PDU — retrieves the values of specified OIDs.
     *
     * @param requestId   the request identifier
     * @param errorStatus the error status (0 for requests)
     * @param errorIndex  the error index (0 for requests)
     * @param varBindList the variable bindings
     * @since 0.1.0
     */
    record GetRequest(int requestId, int errorStatus, int errorIndex,
                      VarBindList varBindList) implements SnmpPdu {
        @Override
        public int tagNumber() { return 0; }
    }

    /**
     * GetNextRequest PDU — retrieves the next OIDs in lexicographic order.
     *
     * @param requestId   the request identifier
     * @param errorStatus the error status (0 for requests)
     * @param errorIndex  the error index (0 for requests)
     * @param varBindList the variable bindings
     * @since 0.1.0
     */
    record GetNextRequest(int requestId, int errorStatus, int errorIndex,
                          VarBindList varBindList) implements SnmpPdu {
        @Override
        public int tagNumber() { return 1; }
    }

    /**
     * Response PDU — returned by an agent in response to a request.
     *
     * @param requestId   the request identifier (matches the request)
     * @param errorStatus the error status (0 = noError)
     * @param errorIndex  the index of the first VarBind in error (1-based, 0 if no error)
     * @param varBindList the variable bindings with values
     * @since 0.1.0
     */
    record Response(int requestId, int errorStatus, int errorIndex,
                    VarBindList varBindList) implements SnmpPdu {
        @Override
        public int tagNumber() { return 2; }
    }

    /**
     * SetRequest PDU — sets the values of specified OIDs.
     *
     * @param requestId   the request identifier
     * @param errorStatus the error status (0 for requests)
     * @param errorIndex  the error index (0 for requests)
     * @param varBindList the variable bindings with values to set
     * @since 0.1.0
     */
    record SetRequest(int requestId, int errorStatus, int errorIndex,
                      VarBindList varBindList) implements SnmpPdu {
        @Override
        public int tagNumber() { return 3; }
    }

    /**
     * GetBulkRequest PDU — retrieves large amounts of data efficiently.
     *
     * @param requestId      the request identifier
     * @param nonRepeaters   number of non-repeating VarBinds at start
     * @param maxRepetitions maximum repetitions for remaining VarBinds
     * @param varBindList    the variable bindings
     * @since 0.1.0
     */
    record GetBulkRequest(int requestId, int nonRepeaters, int maxRepetitions,
                          VarBindList varBindList) implements SnmpPdu {
        @Override
        public int tagNumber() { return 5; }
    }

    /**
     * InformRequest PDU — manager-to-manager notification requiring acknowledgment.
     *
     * @param requestId   the request identifier
     * @param errorStatus the error status (0 for requests)
     * @param errorIndex  the error index (0 for requests)
     * @param varBindList the variable bindings
     * @since 0.1.0
     */
    record InformRequest(int requestId, int errorStatus, int errorIndex,
                         VarBindList varBindList) implements SnmpPdu {
        @Override
        public int tagNumber() { return 6; }
    }

    /**
     * SNMPv2-Trap PDU — agent-to-manager notification (no acknowledgment).
     *
     * @param requestId   the request identifier
     * @param errorStatus the error status (0 for traps)
     * @param errorIndex  the error index (0 for traps)
     * @param varBindList the variable bindings (includes sysUpTime.0 and snmpTrapOID.0)
     * @since 0.1.0
     */
    record TrapV2(int requestId, int errorStatus, int errorIndex,
                  VarBindList varBindList) implements SnmpPdu {
        @Override
        public int tagNumber() { return 7; }
    }
}
