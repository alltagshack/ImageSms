package art.wertfrei.byteSMS;

public enum MimeCode {
    NO((byte) 0),
    JPG((byte) 1),
    GIF((byte) 2),
    PNG((byte) 3),
    WEBP((byte) 4),
    TXT((byte) 5);

    private final byte code;

    MimeCode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
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
            return MimeCode.NO;
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
            case "text/plain":
                return MimeCode.TXT;
            default:
                return MimeCode.NO;
        }
    }
}
