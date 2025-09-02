package art.wertfrei.byteSMS;

import android.app.Application;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.Date;


public class MyApplication extends Application {

    public static final int PERMISSION_REQ = 0x0815;
    public static final int IMAGE_CAPTURE_REQ = 0x4711;

    private DatabaseManager dbManager;

    private final MessageCache broadcastCache = new MessageCache();

    public MessageCache getBroadcastCache() {
        return broadcastCache;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (dbManager == null) {
            dbManager = new DatabaseManager(this);
        }
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!pref.contains("REF_ID")) {
            pref.edit().putInt("REF_ID", 0).commit();
        }
        if (!pref.contains("IMG_SIZE")) {
            pref.edit().putInt("IMG_SIZE", 150).commit();
        }
        if (!pref.contains("IMG_COMP")) {
            pref.edit().putInt("IMG_COMP", 60).commit();
        }
        if (!pref.contains("LAST_TEL")) {
            pref.edit().putString("LAST_TEL", getString(R.string.last_tel)).commit();
        }
    }

    public Message getMessage(byte refNum, String adresse) {
        Cursor cursor = dbManager.get(refNum, adresse);
        Message m = null;

        if (cursor != null && cursor.moveToFirst()) {
            do {
                byte[] data = cursor.getBlob(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_DATEN));

                if (m == null) {
                    // cursor is set to the first seq of data -> it has the mime!
                    m = new Message(refNum, adresse);
                    m.adresse = cursor.getString(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_ADRESSE));
                    m.date = new Date(cursor.getLong(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_DATUM)));
                    m.mime = MimeCode.fromByte((byte) cursor.getInt(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_MIME)));
                }
                m.addBlob(data);

            } while (cursor.moveToNext());
            cursor.close();
        }
        return m;
    }

    public Cursor getMessages() {
        return dbManager.getAll();
    }

    public void messageReceived(byte refNum, String adresse, Date date) {
        dbManager.unfreshSeq(refNum, adresse, date.getTime());
    }

    public void updateMessage(byte refNum, String adresse, int seqNum, MimeCode mime, byte[] data) {
        dbManager.insert(refNum, adresse, seqNum, mime, 0, data);
    }

    public int countParts(byte refNum, String adresse) {
        return dbManager.countFreshSeq(refNum, adresse);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    public File getAppFolder() {
        File file = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            file = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    getPackageName()
            );
        } else {
            file = new File(Environment.getExternalStorageDirectory() + "/Documents/" + getPackageName());
        }

        try {
            if (!file.exists()) file.mkdirs();
        } catch (Exception e) {
            return null;
        }
        return file;
    }

    public File createAppFile(String name) {
        File file = getAppFolder();

        String path = file.getPath() + "/" + name;
        try {
            file = new File(path);
            if (!file.exists()) file.createNewFile();
        } catch (Exception e) {
            return null;
        }
        return file;
    }


    public String getTel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return prefs.getString("LAST_TEL", getString(R.string.last_tel));
    }

    public int getCompression() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return prefs.getInt("IMG_COMP", 60);
    }

    public void setCompression(int compression) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putInt("IMG_COMP", compression).apply();
    }

    public int getImageSize() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return prefs.getInt("IMG_SIZE", 150);
    }

    public void setImageSize(int imgSize) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putInt("IMG_SIZE", imgSize).apply();
    }

    public void setTel(String tel) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putString("LAST_TEL", tel).apply();
    }

    public byte getRef() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return (byte) prefs.getInt("REF_ID", 0);
    }

    public void incrRef() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        byte ref = (byte) prefs.getInt("REF_ID", 0);
        prefs.edit().putInt("REF_ID", (byte) (ref+1)%32).apply();
    }
}
