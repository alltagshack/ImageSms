package art.wertfrei.byteSMS;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MyDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "mydatabase.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_NAME = "my_table";
    public static final String COLUMN_REF_NUM = "refNum";
    public static final String COLUMN_MIME = "mimeNum";
    public static final String COLUMN_SEQ_NUM = "seqNum";
    public static final String COLUMN_ADRESSE = "adresse";
    public static final String COLUMN_DATUM = "datum";
    public static final String COLUMN_DATEN = "daten";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_REF_NUM + " INTEGER NOT NULL, " +
                    COLUMN_SEQ_NUM + " INTEGER NOT NULL, " +
                    COLUMN_ADRESSE + " TEXT, " +
                    COLUMN_MIME + " INTEGER, " +
                    COLUMN_DATUM + " INTEGER, " +
                    COLUMN_DATEN + " BLOB, " +
                    "PRIMARY KEY (" + COLUMN_REF_NUM + ", " + COLUMN_SEQ_NUM + "));";

    public MyDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}