package ssg.legoflow.ssh.transport;

/**
 * Base sealed interface for all SSH binary protocol messages.
 *
 * <p>Each implementation corresponds to a specific SSH message type as defined
 * in RFC 4253, RFC 4252, and RFC 4254. The sealed hierarchy enables exhaustive
 * pattern matching in switch expressions.
 *
 * @since 0.1.0
 */
public sealed interface SshPacket permits
        SshPacket.Disconnect,
        SshPacket.Ignore,
        SshPacket.Unimplemented,
        SshPacket.Debug,
        SshPacket.ServiceRequest,
        SshPacket.ServiceAccept,
        SshPacket.NewKeys,
        SshPacket.KexDhInit,
        SshPacket.KexDhReply,
        SshPacket.KexEcdhInit,
        SshPacket.KexEcdhReply,
        SshPacket.UserAuthRequest,
        SshPacket.UserAuthFailure,
        SshPacket.UserAuthSuccess,
        SshPacket.UserAuthBanner,
        SshPacket.UserAuthInfoRequest,
        SshPacket.UserAuthInfoResponse,
        SshPacket.GlobalRequest,
        SshPacket.RequestSuccess,
        SshPacket.RequestFailure,
        SshPacket.ChannelOpen,
        SshPacket.ChannelOpenConfirmation,
        SshPacket.ChannelOpenFailure,
        SshPacket.ChannelWindowAdjust,
        SshPacket.ChannelData,
        SshPacket.ChannelExtendedData,
        SshPacket.ChannelEof,
        SshPacket.ChannelClose,
        SshPacket.ChannelRequest,
        SshPacket.ChannelSuccess,
        SshPacket.ChannelFailure {

    /**
     * Returns the SSH message type code for this packet.
     *
     * @return the message type byte value
     */
    byte messageType();

    // --- Transport layer messages ---

    /**
     * SSH_MSG_DISCONNECT (type 1).
     *
     * @param reasonCode  the disconnect reason code
     * @param description human-readable disconnect description
     * @param language    language tag (RFC 3066)
     * @since 0.1.0
     */
    record Disconnect(int reasonCode, String description, String language) implements SshPacket {
        @Override public byte messageType() { return 1; }
    }

    /**
     * SSH_MSG_IGNORE (type 2).
     *
     * @param data arbitrary data to be ignored
     * @since 0.1.0
     */
    record Ignore(byte[] data) implements SshPacket {
        @Override public byte messageType() { return 2; }
    }

    /**
     * SSH_MSG_UNIMPLEMENTED (type 3).
     *
     * @param sequenceNumber the sequence number of the unrecognized packet
     * @since 0.1.0
     */
    record Unimplemented(long sequenceNumber) implements SshPacket {
        @Override public byte messageType() { return 3; }
    }

    /**
     * SSH_MSG_DEBUG (type 4).
     *
     * @param alwaysDisplay whether the message should always be displayed
     * @param message       the debug message
     * @param language      language tag
     * @since 0.1.0
     */
    record Debug(boolean alwaysDisplay, String message, String language) implements SshPacket {
        @Override public byte messageType() { return 4; }
    }

    /**
     * SSH_MSG_SERVICE_REQUEST (type 5).
     *
     * @param serviceName the requested service name
     * @since 0.1.0
     */
    record ServiceRequest(String serviceName) implements SshPacket {
        @Override public byte messageType() { return 5; }
    }

    /**
     * SSH_MSG_SERVICE_ACCEPT (type 6).
     *
     * @param serviceName the accepted service name
     * @since 0.1.0
     */
    record ServiceAccept(String serviceName) implements SshPacket {
        @Override public byte messageType() { return 6; }
    }

    /**
     * SSH_MSG_NEWKEYS (type 21).
     *
     * @since 0.1.0
     */
    record NewKeys() implements SshPacket {
        @Override public byte messageType() { return 21; }
    }

    // --- Key exchange DH messages ---

    /**
     * SSH_MSG_KEXDH_INIT (type 30).
     *
     * @param e the client's DH public value
     * @since 0.1.0
     */
    record KexDhInit(byte[] e) implements SshPacket {
        @Override public byte messageType() { return 30; }
    }

    /**
     * SSH_MSG_KEXDH_REPLY (type 31).
     *
     * @param hostKey   the server's public host key
     * @param f         the server's DH public value
     * @param signature the signature of the exchange hash
     * @since 0.1.0
     */
    record KexDhReply(byte[] hostKey, byte[] f, byte[] signature) implements SshPacket {
        @Override public byte messageType() { return 31; }
    }

    // --- Key exchange ECDH messages ---

    /**
     * SSH_MSG_KEX_ECDH_INIT (type 30).
     *
     * @param clientPublicKey the client's ephemeral ECDH public key
     * @since 0.1.0
     */
    record KexEcdhInit(byte[] clientPublicKey) implements SshPacket {
        @Override public byte messageType() { return 30; }
    }

    /**
     * SSH_MSG_KEX_ECDH_REPLY (type 31).
     *
     * @param hostKey         the server's public host key
     * @param serverPublicKey the server's ephemeral ECDH public key
     * @param signature       the signature of the exchange hash
     * @since 0.1.0
     */
    record KexEcdhReply(byte[] hostKey, byte[] serverPublicKey, byte[] signature) implements SshPacket {
        @Override public byte messageType() { return 31; }
    }

    // --- User authentication messages ---

    /**
     * SSH_MSG_USERAUTH_REQUEST (type 50).
     *
     * @param username    the user name
     * @param serviceName the service to start after authentication
     * @param methodName  the authentication method name
     * @param methodData  method-specific data
     * @since 0.1.0
     */
    record UserAuthRequest(String username, String serviceName, String methodName,
                           byte[] methodData) implements SshPacket {
        @Override public byte messageType() { return 50; }
    }

    /**
     * SSH_MSG_USERAUTH_FAILURE (type 51).
     *
     * @param authMethodsThatCanContinue list of methods that can continue
     * @param partialSuccess             whether partial success occurred
     * @since 0.1.0
     */
    record UserAuthFailure(java.util.List<String> authMethodsThatCanContinue,
                           boolean partialSuccess) implements SshPacket {
        @Override public byte messageType() { return 51; }
    }

    /**
     * SSH_MSG_USERAUTH_SUCCESS (type 52).
     *
     * @since 0.1.0
     */
    record UserAuthSuccess() implements SshPacket {
        @Override public byte messageType() { return 52; }
    }

    /**
     * SSH_MSG_USERAUTH_BANNER (type 53).
     *
     * @param message  the banner message
     * @param language language tag
     * @since 0.1.0
     */
    record UserAuthBanner(String message, String language) implements SshPacket {
        @Override public byte messageType() { return 53; }
    }

    /**
     * SSH_MSG_USERAUTH_INFO_REQUEST (type 60) for keyboard-interactive.
     *
     * @param name        name of the info request
     * @param instruction instruction for the user
     * @param language    language tag
     * @param prompts     list of prompts
     * @param echos       whether each prompt should be echoed
     * @since 0.1.0
     */
    record UserAuthInfoRequest(String name, String instruction, String language,
                               java.util.List<String> prompts,
                               java.util.List<Boolean> echos) implements SshPacket {
        @Override public byte messageType() { return 60; }
    }

    /**
     * SSH_MSG_USERAUTH_INFO_RESPONSE (type 61) for keyboard-interactive.
     *
     * @param responses the user responses to the prompts
     * @since 0.1.0
     */
    record UserAuthInfoResponse(java.util.List<String> responses) implements SshPacket {
        @Override public byte messageType() { return 61; }
    }

    // --- Connection protocol messages ---

    /**
     * SSH_MSG_GLOBAL_REQUEST (type 80).
     *
     * @param requestName the request name
     * @param wantReply   whether a reply is expected
     * @param data        request-specific data
     * @since 0.1.0
     */
    record GlobalRequest(String requestName, boolean wantReply, byte[] data) implements SshPacket {
        @Override public byte messageType() { return 80; }
    }

    /**
     * SSH_MSG_REQUEST_SUCCESS (type 81).
     *
     * @param data response data
     * @since 0.1.0
     */
    record RequestSuccess(byte[] data) implements SshPacket {
        @Override public byte messageType() { return 81; }
    }

    /**
     * SSH_MSG_REQUEST_FAILURE (type 82).
     *
     * @since 0.1.0
     */
    record RequestFailure() implements SshPacket {
        @Override public byte messageType() { return 82; }
    }

    /**
     * SSH_MSG_CHANNEL_OPEN (type 90).
     *
     * @param channelType      the channel type (e.g., "session", "direct-tcpip")
     * @param senderChannel    the sender's channel number
     * @param initialWindowSize initial window size
     * @param maxPacketSize    maximum packet size
     * @param data             channel-type-specific data
     * @since 0.1.0
     */
    record ChannelOpen(String channelType, int senderChannel, long initialWindowSize,
                       long maxPacketSize, byte[] data) implements SshPacket {
        @Override public byte messageType() { return 90; }
    }

    /**
     * SSH_MSG_CHANNEL_OPEN_CONFIRMATION (type 91).
     *
     * @param recipientChannel the recipient's channel number
     * @param senderChannel    the sender's channel number
     * @param initialWindowSize initial window size
     * @param maxPacketSize    maximum packet size
     * @since 0.1.0
     */
    record ChannelOpenConfirmation(int recipientChannel, int senderChannel,
                                   long initialWindowSize, long maxPacketSize) implements SshPacket {
        @Override public byte messageType() { return 91; }
    }

    /**
     * SSH_MSG_CHANNEL_OPEN_FAILURE (type 92).
     *
     * @param recipientChannel the recipient's channel number
     * @param reasonCode       the failure reason code
     * @param description      human-readable description
     * @param language         language tag
     * @since 0.1.0
     */
    record ChannelOpenFailure(int recipientChannel, int reasonCode,
                              String description, String language) implements SshPacket {
        @Override public byte messageType() { return 92; }
    }

    /**
     * SSH_MSG_CHANNEL_WINDOW_ADJUST (type 93).
     *
     * @param recipientChannel the recipient's channel number
     * @param bytesToAdd       number of bytes to add to window
     * @since 0.1.0
     */
    record ChannelWindowAdjust(int recipientChannel, long bytesToAdd) implements SshPacket {
        @Override public byte messageType() { return 93; }
    }

    /**
     * SSH_MSG_CHANNEL_DATA (type 94).
     *
     * @param recipientChannel the recipient's channel number
     * @param data             the data
     * @since 0.1.0
     */
    record ChannelData(int recipientChannel, byte[] data) implements SshPacket {
        @Override public byte messageType() { return 94; }
    }

    /**
     * SSH_MSG_CHANNEL_EXTENDED_DATA (type 95).
     *
     * @param recipientChannel the recipient's channel number
     * @param dataTypeCode     the data type code (1 = SSH_EXTENDED_DATA_STDERR)
     * @param data             the data
     * @since 0.1.0
     */
    record ChannelExtendedData(int recipientChannel, int dataTypeCode, byte[] data) implements SshPacket {
        @Override public byte messageType() { return 95; }
    }

    /**
     * SSH_MSG_CHANNEL_EOF (type 96).
     *
     * @param recipientChannel the recipient's channel number
     * @since 0.1.0
     */
    record ChannelEof(int recipientChannel) implements SshPacket {
        @Override public byte messageType() { return 96; }
    }

    /**
     * SSH_MSG_CHANNEL_CLOSE (type 97).
     *
     * @param recipientChannel the recipient's channel number
     * @since 0.1.0
     */
    record ChannelClose(int recipientChannel) implements SshPacket {
        @Override public byte messageType() { return 97; }
    }

    /**
     * SSH_MSG_CHANNEL_REQUEST (type 98).
     *
     * @param recipientChannel the recipient's channel number
     * @param requestType      the request type (e.g., "pty-req", "shell", "exec")
     * @param wantReply        whether a reply is expected
     * @param data             request-specific data
     * @since 0.1.0
     */
    record ChannelRequest(int recipientChannel, String requestType,
                          boolean wantReply, byte[] data) implements SshPacket {
        @Override public byte messageType() { return 98; }
    }

    /**
     * SSH_MSG_CHANNEL_SUCCESS (type 99).
     *
     * @param recipientChannel the recipient's channel number
     * @since 0.1.0
     */
    record ChannelSuccess(int recipientChannel) implements SshPacket {
        @Override public byte messageType() { return 99; }
    }

    /**
     * SSH_MSG_CHANNEL_FAILURE (type 100).
     *
     * @param recipientChannel the recipient's channel number
     * @since 0.1.0
     */
    record ChannelFailure(int recipientChannel) implements SshPacket {
        @Override public byte messageType() { return 100; }
    }
}
