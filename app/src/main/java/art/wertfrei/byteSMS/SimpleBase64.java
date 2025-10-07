package art.wertfrei.byteSMS;

import java.util.Arrays;

public class SimpleBase64 {

    private static final String BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int padding = 0;

        for (int i = 0; i < data.length; i += 3) {
            int byte1 = data[i] & 0xff;
            int byte2 = (i + 1 < data.length) ? data[i + 1] & 0xff : 0;
            int byte3 = (i + 2 < data.length) ? data[i + 2] & 0xff : 0;

            int encodedChar1 = byte1 >> 2;
            int encodedChar2 = ((byte1 & 0x03) << 4) | (byte2 >> 4);
            int encodedChar3 = ((byte2 & 0x0f) << 2) | (byte3 >> 6);
            int encodedChar4 = byte3 & 0x3f;

            sb.append(BASE64_CHARS.charAt(encodedChar1));
            sb.append(BASE64_CHARS.charAt(encodedChar2));
            sb.append(i + 1 < data.length ? BASE64_CHARS.charAt(encodedChar3) : '=');
            sb.append(i + 2 < data.length ? BASE64_CHARS.charAt(encodedChar4) : '=');
        }

        return sb.toString();
    }

    public static byte[] decode(String encoded) {
        StringBuilder cleanedInput = new StringBuilder();
        for (char c : encoded.toCharArray()) {
            if (c != '\n' && c != '\r' && c != ' ') {
                cleanedInput.append(c);
            }
        }

        String cleanedStr = cleanedInput.toString();
        int paddingCount = 0;
        if (cleanedStr.length() > 0) {
            char lastChar = cleanedStr.charAt(cleanedStr.length() - 1);
            if (lastChar == '=') {
                paddingCount++;
                if (cleanedStr.length() > 1 && cleanedStr.charAt(cleanedStr.length() - 2) == '=') {
                    paddingCount++;
                }
            }
        }

        int byteCount = (cleanedStr.length() * 3) / 4 - paddingCount;
        byte[] decodedBytes = new byte[byteCount];

        int index = 0;
        for (int i = 0; i < cleanedStr.length(); i += 4) {
            int char1 = BASE64_CHARS.indexOf(cleanedStr.charAt(i));
            int char2 = BASE64_CHARS.indexOf(cleanedStr.charAt(i + 1));
            int char3 = (i + 2 < cleanedStr.length()) ? BASE64_CHARS.indexOf(cleanedStr.charAt(i + 2)) : -1;
            int char4 = (i + 3 < cleanedStr.length()) ? BASE64_CHARS.indexOf(cleanedStr.charAt(i + 3)) : -1;

            decodedBytes[index++] = (byte) ((char1 << 2) | (char2 >> 4));

            if (char3 != -1) {
                decodedBytes[index++] = (byte) ((char2 << 4) | (char3 >> 2));
            }
            if (char4 != -1) {
                decodedBytes[index++] = (byte) ((char3 << 6) | char4);
            }
        }

        return decodedBytes;
    }
}
