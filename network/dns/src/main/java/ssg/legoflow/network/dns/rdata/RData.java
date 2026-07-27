package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;

/**
 * Sealed interface for DNS RDATA (resource data) types.
 *
 * <p>Each permitted subtype represents a specific DNS record type's data
 * payload. The sealed hierarchy enables exhaustive pattern matching.
 *
 * @since 1.0.0
 */
public sealed interface RData permits
        ARecord, AaaaRecord, NsRecord, CnameRecord, PtrRecord,
        MxRecord, SoaRecord, TxtRecord, SrvRecord, NaptrRecord,
        CaaRecord, OptRecord,
        DnskeyRecord, RrsigRecord, DsRecord, NsecRecord, Nsec3Record, Nsec3ParamRecord,
        RawRData {

    /**
     * Returns the DNS record type for this RDATA.
     *
     * @return the record type
     * @since 1.0.0
     */
    RecordType type();
}
