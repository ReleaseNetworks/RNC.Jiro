package de.releaseteam.jiro.protocol;

public class Protocol {
    public static final int FRAME_START = 0xAA;

    public static final int PKT_CONFIG   = 1;
    public static final int PKT_DATA     = 2;
    public static final int PKT_CONTROL  = 3;
    public static final int PKT_REGISTER = 4;
    public static final int PKT_DISPLAY  = 5;

    public static final int TYPE_UINT8 = 0;
    public static final int TYPE_INT16 = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_BOOL  = 3;

    public static final int ROLE_INPUT  = 0;
    public static final int ROLE_OUTPUT = 1;

    public static final int DISPLAY_TEXT   = 0;
    public static final int DISPLAY_FIELDS = 1;

    private Protocol() {
    }

    public static int checksum(byte[] data) {
        return checksum(data, 0, data.length);
    }

    public static int checksum(byte[] data, int offset, int length) {
        int c = 0;
        for (int i = offset; i < offset + length; i++) {
            c ^= (data[i] & 0xFF);
        }
        return c;
    }

    public static Object decodeValue(int type, byte[] raw) {
        return decodeValue(type, raw, 0);
    }

    public static Object decodeValue(int type, byte[] raw, int offset) {
        switch (type) {
            case TYPE_UINT8:
                return raw[offset] & 0xFF;
            case TYPE_INT16:
                return (short) ((raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8));
            case TYPE_FLOAT:
                int bits = (raw[offset] & 0xFF)
                        | ((raw[offset + 1] & 0xFF) << 8)
                        | ((raw[offset + 2] & 0xFF) << 16)
                        | ((raw[offset + 3] & 0xFF) << 24);
                return Float.intBitsToFloat(bits);
            case TYPE_BOOL:
                return raw[offset] != 0;
            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    public static byte[] encodeValue(int type, Object value) {
        switch (type) {
            case TYPE_UINT8: {
                int v = ((Number) value).intValue();
                return new byte[]{(byte) (v & 0xFF)};
            }
            case TYPE_INT16: {
                int v = ((Number) value).intValue();
                return new byte[]{
                        (byte) (v & 0xFF),
                        (byte) ((v >> 8) & 0xFF)
                };
            }
            case TYPE_FLOAT: {
                float v = ((Number) value).floatValue();
                int bits = Float.floatToIntBits(v);
                return new byte[]{
                        (byte) (bits & 0xFF),
                        (byte) ((bits >> 8) & 0xFF),
                        (byte) ((bits >> 16) & 0xFF),
                        (byte) ((bits >> 24) & 0xFF)
                };
            }
            case TYPE_BOOL: {
                boolean v;
                if (value instanceof Boolean) {
                    v = (Boolean) value;
                } else if (value instanceof Number) {
                    v = ((Number) value).intValue() != 0;
                } else {
                    v = Boolean.parseBoolean(value.toString());
                }
                return new byte[]{(byte) (v ? 1 : 0)};
            }
            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    public static byte[] buildFrame(int ptype, byte[] payload) {
        int payloadLen = payload != null ? payload.length : 0;
        byte[] frame = new byte[4 + payloadLen];
        frame[0] = (byte) FRAME_START;
        frame[1] = (byte) ptype;
        frame[2] = (byte) payloadLen;
        if (payload != null && payloadLen > 0) {
            System.arraycopy(payload, 0, frame, 3, payloadLen);
        }
        frame[3 + payloadLen] = (byte) checksum(frame, 1, payloadLen + 2);
        return frame;
    }
}
