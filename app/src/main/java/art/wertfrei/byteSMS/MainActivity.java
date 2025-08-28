package art.wertfrei.byteSMS;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static boolean isActive = false;

    private static final int MESSAGE_PADDING = 20;

    private ImageView sendImageView;
    private LinearLayout listLayout;
    private LinearLayout numberLine;
    private TextView fileDetails;
    private EditText telEdit;
    private SeekBar sbCompression;
    private SeekBar sbSize;
    private Button sendButton;

    private byte[] shareBytes;
    private MimeCode shareMime;

    private int messageCount;
    private Bitmap capturedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listLayout = findViewById(R.id.listLayout);
        numberLine = findViewById(R.id.numberLine);
        sendImageView = findViewById(R.id.imageView);
        fileDetails = findViewById(R.id.fileDetails);
        telEdit = findViewById(R.id.telEdit);
        sbCompression = findViewById(R.id.sbCompression);
        sbSize = findViewById(R.id.sbSize);

        handleIntent(getIntent());

        sendButton = findViewById(R.id.button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MyAsyncSend(MainActivity.this).execute();
            }
        });

        sbCompression.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser == false) return;

                MyApplication myApp = (MyApplication) getApplicationContext();
                progress *= 10;
                if (progress < 5) progress = 5;
                if (progress > 100) progress = 100;
                myApp.setCompression(progress);
                scaleCapturedImage();
                showShareBytes();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser == false) return;

                MyApplication myApp = (MyApplication) getApplicationContext();
                progress *= 40;
                if (progress < 20) progress = 20;
                myApp.setImageSize(progress);
                scaleCapturedImage();
                showShareBytes();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }

        updateInbox();
    }

    private void handleSendIntent(Intent intent, String type)
    {
        if (type.startsWith("image/"))
        {
            Log.d(getString(R.string.app_name), "mime type: " + type);

            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {

                try {
                    sendImageView.setImageURI(imageUri);

                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    ContentResolver contentResolver = getContentResolver();
                    String mimeType = contentResolver.getType(imageUri);
                    int fileSize = inputStream.available();
                    shareBytes = new byte[fileSize];
                    shareMime = MimeCode.fromString(mimeType);
                    inputStream.read(shareBytes);
                    inputStream.close();

                    showShareBytes();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void showShareBytes()
    {
        MyApplication myApp = (MyApplication) this.getApplicationContext();

        Bitmap compressedBitmap = BitmapFactory.decodeByteArray(shareBytes, 0, shareBytes.length);
        // +1 for mime byte in first sms
        int countSms = (int) Math.ceil((double) (shareBytes.length +1) / BinarySMS.SEGMENT_SIZE);

        sendImageView.setVisibility(View.VISIBLE);
        fileDetails.setVisibility(View.VISIBLE);
        numberLine.setVisibility(View.VISIBLE);
        telEdit.setText(myApp.getTel());

        if (capturedImage != null) {
            sbSize.setVisibility(View.VISIBLE);
            sbCompression.setVisibility(View.VISIBLE);
        }

        if (countSms > 255) {
            sendButton.setEnabled(false);
        } else {
            sendButton.setEnabled(true);
        }

        sendImageView.setImageBitmap(compressedBitmap);

        fileDetails.setText(
                "mime: " + shareMime.toString() + "\n" +
                "bytes: " +
                        String.valueOf(shareBytes.length) +
                        " (" +
                        String.valueOf(countSms) +
                        " SMS)");
    }

    private class MyAsyncSend extends AsyncTask<Void, Integer, Boolean> {
        private Context context;
        private ProgressDialog progressDialog;

        public MyAsyncSend(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage(getString(R.string.wait));
            // +1 for mime byte in first sms
            progressDialog.setMax((int) Math.ceil((double) (shareBytes.length +1) / BinarySMS.SEGMENT_SIZE));
            progressDialog.setProgress(0);
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            MyApplication myApp = (MyApplication) getApplicationContext();

            String phoneNumber = telEdit.getText().toString();
            myApp.setTel(phoneNumber);

            return BinarySMS.getInstance().sendSms(myApp, shareBytes, shareMime, phoneNumber, new ProgressCallback() {
                @Override
                public void onProgressUpdate(int progress) {
                    publishProgress(progress);
                }
            });
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success)
            {
                Toast.makeText(context, getString(R.string.send_done), Toast.LENGTH_SHORT).show();

                sendImageView.setVisibility(View.GONE);
                fileDetails.setVisibility(View.GONE);
                numberLine.setVisibility(View.GONE);
                sbSize.setVisibility(View.GONE);
                sbCompression.setVisibility(View.GONE);
                capturedImage = null;
                shareMime = MimeCode.NO;
            } else {
                Toast.makeText(context, getString(R.string.send_error), Toast.LENGTH_SHORT).show();
            }
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            progressDialog.setProgress(values[0]);
        }
    }

    private void updateInbox()
    {
        MyApplication myApp = (MyApplication) this.getApplicationContext();
        Cursor cursor = myApp.getMessages();
        messageCount = 0;

        if (cursor != null && cursor.moveToFirst()) {
            Map<String, Message> messages = new LinkedHashMap<>();

            do {
                byte refNum = (byte) cursor.getShort(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_REF_NUM));
                byte[] data = cursor.getBlob(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_DATEN));
                String adresse = cursor.getString(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_ADRESSE));

                Message m = messages.get(String.valueOf(refNum) + "_" + adresse);
                if (m == null) {
                    // cursor is set to the first seq of data -> it has the mime!
                    m = new Message(refNum, adresse);
                    m.date = new Date(cursor.getLong(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_DATUM)));
                    m.mime = MimeCode.fromByte((byte) cursor.getInt(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_MIME)));
                    messages.put(String.valueOf(refNum) + "_" + adresse, m);
                }
                m.addBlob(data);

            } while (cursor.moveToNext());
            cursor.close();

            for (Map.Entry<String, Message> entry : messages.entrySet()) {
                Message m = entry.getValue();
                messageCount++;
                showMessage(
                        DateFormat.format(getString(R.string.date_format), m.date).toString(),
                        m.mime,
                        m.adresse,
                        m.getDaten()
                );
            }
        }
    }

    private void showMessage(String dateStr, MimeCode mime, String address, byte[] fullMessage) {
        TextView textView = new TextView(this);
        textView.setText(dateStr + "\n");
        textView.append(address + "\n");
        textView.append("mime: " + mime.toString() + "\n");
        textView.append(
                // +1 for mime byte in first sms
                "bytes: " +
                        String.valueOf(fullMessage.length) +
                        " (" +
                        String.valueOf((int) Math.ceil((double) (fullMessage.length +1) / BinarySMS.SEGMENT_SIZE)) +
                        " SMS)\n\n");

        textView.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textView.setGravity(Gravity.TOP);
        textView.setTextColor(Color.BLACK);
        if (messageCount % 2 == 0) {
            textView.setBackgroundResource(R.color.halfLine1);
        } else {
            textView.setBackgroundResource(R.color.halfLine2);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textView.setLayoutParams(params);
        textView.setPadding(MESSAGE_PADDING,MESSAGE_PADDING,MESSAGE_PADDING,MESSAGE_PADDING);

        Bitmap bitmap = BitmapFactory.decodeByteArray(fullMessage, 0, fullMessage.length);
        ImageView imageView =null;
        if (bitmap != null) {
            imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            imageView.setLayoutParams(params2);
            imageView.setPadding(MESSAGE_PADDING,MESSAGE_PADDING,MESSAGE_PADDING,MESSAGE_PADDING);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            if (messageCount % 2 == 0) {
                imageView.setBackgroundResource(R.color.halfLine1);
            } else {
                imageView.setBackgroundResource(R.color.halfLine2);
            }
            listLayout.addView(imageView);

        }
        listLayout.addView(textView);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("byte_data")) {

            byte[] fullMessage = intent.getByteArrayExtra("byte_data");
            String address = intent.getStringExtra("address");
            String dateStr = intent.getStringExtra("date");
            MimeCode mime = MimeCode.fromByte(intent.getByteExtra("byte_mime", MimeCode.NO.getCode()));
            if (fullMessage != null) {
                showMessage(dateStr, mime, address, fullMessage);
            }
        }
        else if (intent != null && intent.getAction() != null && intent.getType() != null)
        {
            String action = intent.getAction();
            String type = intent.getType();

            if (Intent.ACTION_SEND.equals(action))
            {
                if (PermissionUtils.externalGranted(this))
                {
                    handleSendIntent(intent, type);
                }
                else {
                    String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                    PermissionUtils.requestPermissions(this, MyApplication.PERMISSION_REQ, permissions);
                }
            }
        } else {
            if (PermissionUtils.allGranted(this) == false)
            {
                String[] permissions = new String[]{
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.RECEIVE_MMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.VIBRATE,
                        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Manifest.permission.ACCESS_NOTIFICATION_POLICY,
                        Manifest.permission.CAMERA
                };
                PermissionUtils.requestPermissions(this, MyApplication.PERMISSION_REQ, permissions);
            }
        }
    }

    private void dispatchTakePictureIntent() {
        capturedImage = null;
        shareMime = MimeCode.NO;
        MyApplication myApp = (MyApplication) this.getApplicationContext();
        File image = myApp.createAppFile("_temp.jpg");

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            Uri photoURI = FileProvider.getUriForFile(getApplicationContext(),
                    getPackageName() + ".fileprovider",
                    image
            );
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, MyApplication.IMAGE_CAPTURE_REQ);
        }
    }

    private void scaleCapturedImage()
    {
        MyApplication myApp = (MyApplication) this.getApplicationContext();

        if (capturedImage == null) return;

        int width = capturedImage.getWidth();
        int height = capturedImage.getHeight();
        float imgSize = (float) myApp.getImageSize();
        float scalingFactor = Math.min(imgSize / width, imgSize / height);
        width = Math.round(width * scalingFactor);
        height = Math.round(height * scalingFactor);
        Bitmap smallImage = Bitmap.createScaledBitmap(capturedImage, width, height, true);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        smallImage.compress(Bitmap.CompressFormat.JPEG, myApp.getCompression(), stream);

        shareBytes = stream.toByteArray();
    }

    private Bitmap rotateImage(Bitmap bitmap, int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImageByDegrees(bitmap, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImageByDegrees(bitmap, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImageByDegrees(bitmap, 270);
            default:
                return bitmap;
        }
    }

    private Bitmap rotateImageByDegrees(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean granted = false;
        switch (requestCode) {
            case MyApplication.PERMISSION_REQ:
                granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    Toast.makeText(this, getString(R.string.restart_me), Toast.LENGTH_LONG).show();
                } else {
                    //nobody knows what to do
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (sendImageView != null && sendImageView.isShown()) {
            outState.putByteArray("shareBytes", shareBytes);
        }
        if (shareMime != null) {
            outState.putByte("shareMime", shareMime.getCode());
        }
        if (capturedImage != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            capturedImage.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            outState.putByteArray("capturedBytes", stream.toByteArray());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        shareBytes = savedInstanceState.getByteArray("shareBytes");
        shareMime = MimeCode.fromByte(savedInstanceState.getByte("shareMime"));
        byte[] byteArray = savedInstanceState.getByteArray("capturedBytes");

        if (byteArray != null)
        {
            capturedImage = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        }
        if (shareBytes != null)
        {
            showShareBytes();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MyApplication myApp = (MyApplication) this.getApplicationContext();

        if (requestCode == MyApplication.IMAGE_CAPTURE_REQ && resultCode == RESULT_OK) {
            String imagePath = myApp.getAppFolder().getPath() + "/_temp.jpg";

            capturedImage = BitmapFactory.decodeFile(imagePath);
            shareMime = MimeCode.JPG;
            try {
                ExifInterface exif = new ExifInterface(imagePath);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                capturedImage = rotateImage(capturedImage, orientation);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // cleanup
            File f = myApp.createAppFile("_temp.jpg");
            try {
                f.getCanonicalFile().delete();
            } catch (IOException e) {
                e.printStackTrace();
            }

            sbCompression.setProgress(myApp.getCompression()/10);
            sbSize.setProgress(myApp.getImageSize()/40);

            scaleCapturedImage();
            showShareBytes();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActive = true;
        IntentFilter filter = new IntentFilter("android.intent.action.DATA_SMS_RECEIVED");
        BinarySMS binarySms = BinarySMS.getInstance();
        registerReceiver(binarySms, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActive = false;
        unregisterReceiver(BinarySMS.getInstance());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_button:
                dispatchTakePictureIntent();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
