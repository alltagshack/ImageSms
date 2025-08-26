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

import java.util.Date;

public class BinarySMS extends BroadcastReceiver {

    private static final byte MESSAGE_START_REF = 0x42;
    // normal sms 160, multi data sms + port 128 (140 - 1totalLengthByte - 1headerLengthByte - 5headerBytes - somePortBytes)
    // 128 - id - numer - total = 125
    public static final int SEGMENT_SIZE = 130;

    public boolean sendSms(Context context, byte[] bytes, MimeCode mime, String phoneNumber, ProgressCallback callback)
    {
        MyApplication myApp = (MyApplication) context.getApplicationContext();
        // +1 for mime byte in first sms
        byte totalSegments = (byte) Math.ceil((double) (bytes.length+1) / SEGMENT_SIZE);

        if (PermissionUtils.writeGranted(context)) {
            short pduDataPort = (short) context.getResources().getInteger(R.integer.pdu_data_port);

            for (int i = 0; i < totalSegments; i++) {
                int start = i * SEGMENT_SIZE;
                int length = Math.min(SEGMENT_SIZE, bytes.length - start);
                byte[] segment = new byte[length+3];
                segment[0] = (byte) (MESSAGE_START_REF + myApp.getRef());
                segment[1] = (byte) (i+1);
                segment[2] = totalSegments;
                if (i == 0) {
                    segment[3] = mime.getCode();
                    System.arraycopy(bytes, start, segment, 4, length-1);
                } else {
                    System.arraycopy(bytes, start-1, segment, 3, length);
                }

                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendDataMessage(phoneNumber, null, pduDataPort, segment, null, null);

                Log.d(context.getString(R.string.app_name), "pdu " + (i+1) + ": " + MyApplication.bytesToHex(segment));

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


    private static class HandleDataSms extends AsyncTask<Object, Void, Void> {
        private final Context context;

        HandleDataSms(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        protected Void doInBackground(Object... pdus) {
            MyApplication myApp = (MyApplication) context.getApplicationContext();
            String address = "";
            int totalParts = 0;
            Message m = null;
            for (Object pdu : pdus)
            {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
                byte[] data = message.getUserData();
                address = message.getDisplayOriginatingAddress();

                Log.d(context.getString(R.string.app_name), "UserData: " + MyApplication.bytesToHex(data));

                byte refNum = data[0];
                byte seqNum = data[1];
                totalParts = data[2];

                Log.d(context.getString(R.string.app_name), "Ref[" + refNum + "], Part[" + seqNum + "/" + totalParts + "]");


                int payloadStart = 3;
                MimeCode mime = MimeCode.NO;
                if (seqNum == 1) {
                    mime = MimeCode.fromByte(data[3]);
                    payloadStart = 4;
                }

                int payloadLength = data.length - payloadStart;
                byte[] payload = new byte[payloadLength];
                System.arraycopy(data, payloadStart, payload, 0, payloadLength);

                myApp.updateMessage(refNum, seqNum, mime, address, payload);

                // try to join the data: ----------------------------------------

                if (myApp.countParts(refNum) == totalParts && totalParts > 0) {
                    Date date = new Date();
                    myApp.messageReceived(refNum, date);

                    m = myApp.getMessage(refNum);
                    byte[] fullMessage = null;
                    if (m != null) {
                        fullMessage = m.getDaten();
                        Log.d(context.getString(R.string.app_name), "Received full Data SMS (" + fullMessage.length + " bytes).");
                    }

                    if (MainActivity.isActive) {
                        Intent mainActivityIntent = new Intent(context, MainActivity.class);
                        mainActivityIntent.putExtra("address", address);
                        mainActivityIntent.putExtra("date", DateFormat.format(context.getString(R.string.date_format), date).toString());
                        mainActivityIntent.putExtra("byte_data", fullMessage);
                        mainActivityIntent.putExtra("byte_mime", m.mime.getCode());
                        //mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(mainActivityIntent);
                    } else {
                        createNotification(context, refNum, address, fullMessage);
                    }


                } else {
                    Log.w(context.getString(R.string.app_name),"Incomplete message received. Expected parts: " + totalParts + ", got: " + myApp.countParts(refNum));
                }
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

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        new HandleDataSms(context).execute(pdus);
    }

    private static void createNotification(Context context, byte refNum, String text, byte[] bytes) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Bitmap iconBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        iconBitmap = Bitmap.createScaledBitmap(iconBitmap, 64, 64, false);

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

        //int lightColor = ContextCompat.getColor(context, R.color.colorPrimary);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getPackageName())
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setTicker(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setLargeIcon(iconBitmap)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setLights(Color.CYAN, 4000, 1000)
                .setVibrate(new long[]{2000})
                .setContentIntent(PendingIntent.getActivity(context, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true);

        notificationManager.notify(refNum, builder.build());
    }
}
