package art.wertfrei.byteSMS;

import javax.crypto.Cipher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

public class RSAEncryption {

    private static byte[] readFileToByteArray(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return data;
    }

    private static PublicKey loadOpenSSHPublicKey(File pubKeyFile) throws Exception {
        byte[] keyBytes = readFileToByteArray(pubKeyFile);
        String keyString = new String(keyBytes);

        String[] parts = keyString.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid OpenSSH public key format");
        }

        String keyType = parts[0]; // "ssh-rsa"
        byte[] decoded = SimpleBase64.decode(parts[1]);

        if (!"ssh-rsa".equals(keyType)) {
            throw new IllegalArgumentException("Not an RSA public key");
        }

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decoded));

        int len = dis.readInt();
        byte[] typeBytes = new byte[len];
        dis.readFully(typeBytes);

        int eLength = dis.readInt();
        byte[] eBytes = new byte[eLength];
        dis.readFully(eBytes);
        BigInteger e = new BigInteger(1, eBytes);

        int nLength = dis.readInt();
        byte[] nBytes = new byte[nLength];
        dis.readFully(nBytes);
        BigInteger n = new BigInteger(1, nBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(spec);
    }

    public static int getMaxRSABlockSize(RSAPublicKey key) {
        int keySizeBytes = (key.getModulus().bitLength() + 7) / 8;
        return keySizeBytes - 11; // PKCS#1 Padding
    }

    public static byte[] encrypt(byte[] data, File pub) throws Exception {
        PublicKey publicKey = loadOpenSSHPublicKey(pub);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        int maxBlock = getMaxRSABlockSize((RSAPublicKey) publicKey);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (int i = 0; i < data.length; i += maxBlock) {
            int len = Math.min(maxBlock, data.length - i);
            byte[] chunk = Arrays.copyOfRange(data, i, i + len);
            out.write(cipher.doFinal(chunk));
        }
        return out.toByteArray();
    }

    public static byte[] decrypt(byte[] encrypted, File myPem) throws Exception {
        byte[] keyBytes = readFileToByteArray(myPem);
        String privateKeyPEM = new String(keyBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decoded = SimpleBase64.decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey priv = keyFactory.generatePrivate(spec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, priv);

        return cipher.doFinal(encrypted);
    }
}
