package art.wertfrei.byteSMS;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

public class BinarySMS extends BroadcastReceiver {

    private static final byte MESSAGE_START_REF = 0x42;
    // normal sms 160, multi data sms + port 128 (140 - 1totalLengthByte - 1headerLengthByte - 5headerBytes - somePortBytes)
    // 128 - id - numer - total = 125
    public static final int SEGMENT_SIZE = 130;

    private static BinarySMS instance;

    public static BinarySMS getInstance() {
        if (instance == null) {
            instance = new BinarySMS();
        }
        return instance;
    }

    public boolean sendSms(Context context, byte[] bytes, MimeCode mime, String phoneNumber, ProgressCallback callback)
    {
        MyApplication myApp = (MyApplication) context.getApplicationContext();
        // +1 for mime byte in first sms
        int totalSegments = (int) Math.ceil((double) (bytes.length+1) / SEGMENT_SIZE);
        int offset = 0;

        if (PermissionUtils.writeGranted(context)) {
            short pduDataPort = (short) context.getResources().getInteger(R.integer.pdu_data_port);

            for (int i = 0; i < totalSegments; i++) {

                int headerLength = 3; // refNum, seqNum, totalParts
                if (i == 0) headerLength++; // +1 Byte for Mime

                int payloadLength = Math.min(SEGMENT_SIZE - headerLength, bytes.length - offset);
                byte[] segment = new byte[headerLength + payloadLength];

                segment[0] = (byte) (MESSAGE_START_REF + myApp.getRef());
                segment[1] = (byte) i;
                segment[2] = (byte) totalSegments;
                int payloadStart = 3;
                if (i == 0) {
                    segment[3] = mime.getCode();
                    payloadStart = 4;
                }
                System.arraycopy(bytes, offset, segment, payloadStart, payloadLength);

                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendDataMessage(phoneNumber, null, pduDataPort, segment, null, null);

                Log.d(context.getString(R.string.app_name), "data " + i + ": " + MyApplication.bytesToHex(segment));
                offset += payloadLength;

                try {
                    if (i%10 == 9) {
                        Thread.sleep(1500);
                    } else {
                        Thread.sleep(300);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (callback != null) {
                    callback.onProgressUpdate(i+1);
                }
            }
            myApp.incrRef();
            return true;
        } else {
            String[] permissions = new String[]{Manifest.permission.SEND_SMS};
            PermissionUtils.requestPermissions(context, MyApplication.PERMISSION_REQ, permissions);
        }
        return false;
    }



    private static class HandleDataSms extends AsyncTask<Void, Void, Void> {
        private final Context context;
        private final SmsMessage message;

        HandleDataSms(Context context, SmsMessage message) {
            this.context = context;
            this.message = message;
        }

        @Override
        protected Void doInBackground(Void ... voids) {
            MyApplication myApp = (MyApplication) context.getApplicationContext();
            String address = "";
            int totalParts = 0;
            Message m = null;

            byte[] data = message.getUserData();
            address = message.getOriginatingAddress();

            Log.d(context.getString(R.string.app_name), "UserData: " + MyApplication.bytesToHex(data));

            byte refNum = data[0];
            int seqNum = data[1] & 0xFF;
            totalParts = data[2] & 0xFF;

            Log.d(myApp.getString(R.string.app_name), "Ref[" + refNum + "], Part[" + (seqNum + 1) + "/" + totalParts + "]");

            int payloadStart = 3;
            MimeCode mime = MimeCode.BIN;
            if (seqNum == 0) {
                mime = MimeCode.fromByte(data[3]);
                payloadStart = 4;
            }

            int payloadLength = data.length - payloadStart;
            byte[] payload = new byte[payloadLength];
            System.arraycopy(data, payloadStart, payload, 0, payloadLength);

            myApp.updateMessage(refNum, address, seqNum, mime, payload);

            // try to join the data: ----------------------------------------

            if (myApp.countParts(refNum, address) == totalParts && totalParts > 0) {
                Date date = new Date(message.getTimestampMillis());
                myApp.messageReceived(refNum, address, date);

                m = myApp.getMessage(refNum, address);
                byte[] fullMessage = null;
                if (m != null) {
                    fullMessage = m.getDaten();
                    Log.d(myApp.getString(R.string.app_name), "Received full Data SMS (" + fullMessage.length + " bytes).");

                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(m.date);
                    String filename = ts + "_" + address.replace("+", "00") + "." + m.mime.toString().toLowerCase();
                    File f = myApp.createAppFile(filename);
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(fullMessage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (MainActivity.isActive) {
                    Intent mainActivityIntent = new Intent(context, MainActivity.class);
                    mainActivityIntent.putExtra("address", address);
                    mainActivityIntent.putExtra("date", DateFormat.format(myApp.getString(R.string.date_format), date).toString());
                    mainActivityIntent.putExtra("byte_data", fullMessage);
                    mainActivityIntent.putExtra("byte_mime", m.mime.getCode());
                    mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mainActivityIntent);
                } else {
                    createNotification(context, m);
                }

            } else {
                Log.w(myApp.getString(R.string.app_name), "Incomplete message received. Expected parts: " + totalParts + ", got: " + myApp.countParts(refNum, address));
            }
            return null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction().equals("android.intent.action.DATA_SMS_RECEIVED") == false) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        final Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        MyApplication myApp = (MyApplication) context.getApplicationContext();

        SmsMessage message = SmsMessage.createFromPdu((byte[]) pdus[0]);
        byte[] data = message.getUserData();
        String filename = message.getOriginatingAddress() + "_" + String.valueOf(data[0]) + "_" + String.valueOf(data[1]) + ".tmp";
        File f = myApp.createAppFile(filename);
        if (f != null) {
            new HandleDataSms(context.getApplicationContext(), message).execute();
        }
    }

    private static void createNotification(Context context, Message message) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        byte[] bytes = message.getDaten();

        Bitmap iconBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (iconBitmap != null) {
            iconBitmap = Bitmap.createScaledBitmap(iconBitmap, 64, 64, false);
        }

        Intent mainActivityIntent = new Intent(context, MainActivity.class);
        mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    context.getPackageName(),
                    context.getString(R.string.app_name) + " Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("The channel of " + context.getString(R.string.app_name));
            channel.enableLights(true);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        int lightColor = ContextCompat.getColor(context, R.color.colorPrimary);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getPackageName())
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message.adresse)
                .setTicker(message.adresse)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setLights(lightColor, 4000, 1000)
                .setVibrate(new long[]{2000})
                .setContentIntent(PendingIntent.getActivity(context, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true);

        if (iconBitmap != null) {
            builder.setLargeIcon(iconBitmap);
        }

        notificationManager.notify(message.refNum, builder.build());
    }
}
