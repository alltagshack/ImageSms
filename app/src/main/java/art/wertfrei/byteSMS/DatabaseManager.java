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

    public void insert(byte refNum, byte seqNum, MimeCode mime, String adresse, long datum, byte[] daten) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MyDatabaseHelper.COLUMN_REF_NUM, refNum);
        values.put(MyDatabaseHelper.COLUMN_SEQ_NUM, seqNum);
        if (adresse != null) values.put(MyDatabaseHelper.COLUMN_ADRESSE, adresse);
        if (mime != MimeCode.NO) values.put(MyDatabaseHelper.COLUMN_MIME, mime.getCode());
        values.put(MyDatabaseHelper.COLUMN_DATUM, datum);
        if (daten != null) values.put(MyDatabaseHelper.COLUMN_DATEN, daten);

        db.insertWithOnConflict(MyDatabaseHelper.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public Cursor get(byte refNum) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT " + MyDatabaseHelper.COLUMN_REF_NUM + ", " +
                MyDatabaseHelper.COLUMN_SEQ_NUM + ", " +
                MyDatabaseHelper.COLUMN_ADRESSE + ", " +
                MyDatabaseHelper.COLUMN_MIME + ", " +
                MyDatabaseHelper.COLUMN_DATEN + ", " +
                MyDatabaseHelper.COLUMN_DATUM +
                " FROM " + MyDatabaseHelper.TABLE_NAME +
                " WHERE " + MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " != ? " +
                " ORDER BY " + MyDatabaseHelper.COLUMN_SEQ_NUM + " ASC";

        return db.rawQuery(query, new String[]{String.valueOf(refNum), String.valueOf(0)});
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
                MyDatabaseHelper.COLUMN_REF_NUM + " ASC, " +
                MyDatabaseHelper.COLUMN_SEQ_NUM + " ASC";

        return db.rawQuery(query, new String[]{String.valueOf(0)});
    }

    public int countFreshSeq(byte refNum) {
        // fresh => date is zero
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " = ?";
        String[] selectionArgs = { String.valueOf(refNum), String.valueOf(0) };

        Cursor cursor = db.query(MyDatabaseHelper.TABLE_NAME, null, selection, selectionArgs, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    public void unfreshSeq(byte refNum, long datum) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // remove old sequences => date is not zero
        db.delete(MyDatabaseHelper.TABLE_NAME,
                MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " != ?",
                new String[]{String.valueOf(refNum), String.valueOf(0)});

        ContentValues values = new ContentValues();
        values.put(MyDatabaseHelper.COLUMN_DATUM, datum);

        // all fresh seq got a new date!
        db.update(MyDatabaseHelper.TABLE_NAME, values,
                MyDatabaseHelper.COLUMN_REF_NUM + " = ? AND " + MyDatabaseHelper.COLUMN_DATUM + " = ?",
                new String[]{String.valueOf(refNum), String.valueOf(0)});

        db.close();
    }
}
