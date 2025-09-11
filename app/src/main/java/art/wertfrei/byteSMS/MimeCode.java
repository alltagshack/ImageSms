package art.wertfrei.byteSMS;

public enum MimeCode {
    BIN((byte) 0x00),
    JPG((byte) 0x01),
    GIF((byte) 0x02),
    PNG((byte) 0x03),
    WEBP((byte) 0x04),
    TXT((byte) 0x05),
    GEO((byte) 0x06),
    eBIN((byte) 0x10),
    eJPG((byte) 0x11),
    eGIF((byte) 0x12),
    ePNG((byte) 0x13),
    eWEBP((byte)0x14),
    eTXT((byte) 0x15),
    eGEO((byte) 0x16);

    private byte code;

    MimeCode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public boolean isEncrypt() {
        return (code & 0x10) != 0;
    }

    public MimeCode getEncrypt() {
        byte c = (byte) (code | 0x10);
        return fromByte(c);
    }

    public MimeCode getDecrypt() {
        byte c = (byte) (code & ~0x10);
        return fromByte(c);
    }

    public static MimeCode fromByte(byte code) {
        for (MimeCode c : MimeCode.values()) {
            if (c.getCode() == code) {
                return c;
            }
        }
        throw new IllegalArgumentException("unknown mime code: " + code);
    }

    public static MimeCode fromString(String mimeType) {
        if (mimeType == null) {
            return MimeCode.BIN;
        }

        switch (mimeType) {
            case "image/jpeg":
                return MimeCode.JPG;
            case "image/png":
                return MimeCode.PNG;
            case "image/gif":
                return MimeCode.GIF;
            case "image/webp":
                return MimeCode.WEBP;
            case "text/csv":
            case "text/markdown":
            case "text/plain":
                return MimeCode.TXT;
            default:
                return MimeCode.BIN;
        }
    }
}
