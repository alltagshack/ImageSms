package art.wertfrei.byteSMS;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DatabaseManager {
    private MyDatabaseHelper dbHelper;

    public DatabaseManager(Context context) {
        dbHelper = new MyDatabaseHelper(context);
    }

    public void insert(byte refNum, String adresse, int seqNum, MimeCode mime, long datum, byte[] daten) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MyDatabaseHelper.COLUMN_REF_NUM, refNum);
        values.put(MyDatabaseHelper.COLUMN_SEQ_NUM, seqNum);
        values.put(MyDatabaseHelper.COLUMN_ADRESSE, adresse);
        if (mime != MimeCode.BIN) values.put(MyDatabaseHelper.COLUMN_MIME, mime.getCode());
        values.put(MyDatabaseHelper.COLUMN_DATUM, datum);
        if (daten != null) values.put(MyDatabaseHelper.COLUMN_DATEN, daten);

        db.insertWithOnConflict(MyDatabaseHelper.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public Cursor get(byte refNum, String adresse) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT " + MyDatabaseHelper.COLUMN_REF_NUM + ", " +
                MyDatabaseHelper.COLUMN_SEQ_NUM + ", " +
                MyDatabaseHelper.COLUMN_ADRESSE + ", " +
                MyDatabaseHelper.COLUMN_MIME + ", " +
                MyDatabaseHelper.COLUMN_DATEN + ", " +
                MyDatabaseHelper.COLUMN_DATUM +
                " FROM " + MyDatabaseHelper.TABLE_NAME +
                " WHERE " + MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_ADRESSE + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " != ? " +
                " ORDER BY " + MyDatabaseHelper.COLUMN_SEQ_NUM + " ASC";

        return db.rawQuery(query, new String[]{String.valueOf(refNum), adresse, String.valueOf(0)});
    }

    public Cursor getAll() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT " + MyDatabaseHelper.COLUMN_REF_NUM + ", " +
                MyDatabaseHelper.COLUMN_SEQ_NUM + ", " +
                MyDatabaseHelper.COLUMN_ADRESSE + ", " +
                MyDatabaseHelper.COLUMN_MIME + ", " +
                MyDatabaseHelper.COLUMN_DATEN + ", " +
                MyDatabaseHelper.COLUMN_DATUM +
                " FROM " + MyDatabaseHelper.TABLE_NAME +
                " WHERE " + MyDatabaseHelper.COLUMN_DATUM + " != ? " +
                " ORDER BY " + MyDatabaseHelper.COLUMN_DATUM + " DESC, " +
                MyDatabaseHelper.COLUMN_ADRESSE + " ASC, " +
                MyDatabaseHelper.COLUMN_REF_NUM + " ASC, " +
                MyDatabaseHelper.COLUMN_SEQ_NUM + " ASC";

        return db.rawQuery(query, new String[]{String.valueOf(0)});
    }

    public int countFreshSeq(byte refNum, String adresse) {
        // fresh => date is zero
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_ADRESSE + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " = ?";
        String[] selectionArgs = { String.valueOf(refNum), adresse, String.valueOf(0) };

        Cursor cursor = db.query(MyDatabaseHelper.TABLE_NAME, null, selection, selectionArgs, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    public void unfreshSeq(byte refNum, String adresse, long datum) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(MyDatabaseHelper.COLUMN_DATUM, datum);

        db.beginTransaction();
        try {
            // remove old sequences => date is not zero
            db.delete(MyDatabaseHelper.TABLE_NAME,
                    MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_ADRESSE + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " != ?",
                    new String[]{String.valueOf(refNum), adresse, String.valueOf(0)});

            // all fresh seq got a new date!
            int rowsAffected = db.update(MyDatabaseHelper.TABLE_NAME, values,
                    MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_ADRESSE + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " = ?",
                    new String[]{String.valueOf(refNum), adresse, String.valueOf(0)});

            db.setTransactionSuccessful();
        } catch (Exception e) {
            // todo
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
