package ssg.legoflow.http.security;

import java.nio.ByteBuffer;

public interface SecurityExtension {

    String getName();

    ByteBuffer processOutbound(ByteBuffer data);

    ByteBuffer processInbound(ByteBuffer data);
}
