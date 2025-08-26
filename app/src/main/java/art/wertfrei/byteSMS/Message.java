package art.wertfrei.byteSMS;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Message {
    public byte refNum;
    public Date date;
    public String adresse;
    public MimeCode mime;

    private List<byte[]> blobs;

    public Message(byte refNum) {
        this.refNum = refNum;
        this.mime = MimeCode.NO;
        this.blobs = new ArrayList<>();
    }

    public byte[] getDaten() {
        int totalLength = 0;
        for (byte[] blob : blobs) {
            totalLength += blob.length;
        }

        byte[] merged = new byte[totalLength];
        int currentIndex = 0;
        for (byte[] blob : blobs) {
            System.arraycopy(blob, 0, merged, currentIndex, blob.length);
            currentIndex += blob.length;
        }
        return merged;
    }

    public void addBlob(byte[] blob) {
        blobs.add(blob);
    }
}