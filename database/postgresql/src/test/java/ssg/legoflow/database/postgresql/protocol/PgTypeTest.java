package ssg.legoflow.database.postgresql.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PgType}.
 */
class PgTypeTest {

    @Test
    void testInt4Oid() {
        assertThat(PgType.INT4.oid()).isEqualTo(23);
    }

    @Test
    void testVarcharOid() {
        assertThat(PgType.VARCHAR.oid()).isEqualTo(1043);
    }

    @Test
    void testTextOid() {
        assertThat(PgType.TEXT.oid()).isEqualTo(25);
    }

    @Test
    void testBoolOid() {
        assertThat(PgType.BOOL.oid()).isEqualTo(16);
    }

    @Test
    void testInt8Oid() {
        assertThat(PgType.INT8.oid()).isEqualTo(20);
    }

    @Test
    void testFloat8Oid() {
        assertThat(PgType.FLOAT8.oid()).isEqualTo(701);
    }

    @Test
    void testTimestampOid() {
        assertThat(PgType.TIMESTAMP.oid()).isEqualTo(1114);
    }

    @Test
    void testJsonOid() {
        assertThat(PgType.JSON.oid()).isEqualTo(114);
    }

    @Test
    void testJsonbOid() {
        assertThat(PgType.JSONB.oid()).isEqualTo(3802);
    }

    @Test
    void testUuidOid() {
        assertThat(PgType.UUID.oid()).isEqualTo(2950);
    }

    @Test
    void testByteaOid() {
        assertThat(PgType.BYTEA.oid()).isEqualTo(17);
    }

    @Test
    void testFromOidKnown() {
        assertThat(PgType.fromOid(23)).isEqualTo(PgType.INT4);
        assertThat(PgType.fromOid(25)).isEqualTo(PgType.TEXT);
        assertThat(PgType.fromOid(16)).isEqualTo(PgType.BOOL);
    }

    @Test
    void testFromOidUnknown() {
        assertThat(PgType.fromOid(99999)).isEqualTo(PgType.UNKNOWN);
    }

    @Test
    void testFromNameDirect() {
        assertThat(PgType.fromName("int4")).isEqualTo(PgType.INT4);
        assertThat(PgType.fromName("varchar")).isEqualTo(PgType.VARCHAR);
        assertThat(PgType.fromName("text")).isEqualTo(PgType.TEXT);
    }

    @Test
    void testFromNameAlias() {
        assertThat(PgType.fromName("integer")).isEqualTo(PgType.INT4);
        assertThat(PgType.fromName("bigint")).isEqualTo(PgType.INT8);
        assertThat(PgType.fromName("boolean")).isEqualTo(PgType.BOOL);
        assertThat(PgType.fromName("real")).isEqualTo(PgType.FLOAT4);
    }

    @Test
    void testFromNameUnknown() {
        assertThat(PgType.fromName("nosuchtype")).isEqualTo(PgType.UNKNOWN);
    }

    @Test
    void testTypeSizeFixed() {
        assertThat(PgType.BOOL.typeSize()).isEqualTo(1);
        assertThat(PgType.INT2.typeSize()).isEqualTo(2);
        assertThat(PgType.INT4.typeSize()).isEqualTo(4);
        assertThat(PgType.INT8.typeSize()).isEqualTo(8);
    }

    @Test
    void testTypeSizeVariable() {
        assertThat(PgType.TEXT.typeSize()).isEqualTo(-1);
        assertThat(PgType.VARCHAR.typeSize()).isEqualTo(-1);
        assertThat(PgType.BYTEA.typeSize()).isEqualTo(-1);
    }

    @Test
    void testTypeName() {
        assertThat(PgType.INT4.typeName()).isEqualTo("int4");
        assertThat(PgType.VARCHAR.typeName()).isEqualTo("varchar");
    }
}
