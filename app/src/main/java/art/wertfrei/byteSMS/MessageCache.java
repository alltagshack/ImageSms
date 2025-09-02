package art.wertfrei.byteSMS;

import android.telephony.SmsMessage;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageCache {
    private static final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();
    private static final long EXPIRY_MILLIS = 60_000;

    public static boolean isDuplicate(SmsMessage message) {
        long now = System.currentTimeMillis();

        // remove old
        Iterator<Map.Entry<String, Long>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > EXPIRY_MILLIS) {
                it.remove();
            }
        }

        Long prev = cache.putIfAbsent(message.getOriginatingAddress() + "|" + message.getTimestampMillis(), now);
        return prev != null;
    }
}