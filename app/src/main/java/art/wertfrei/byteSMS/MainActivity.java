package art.wertfrei.byteSMS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.room.DatabaseConfiguration;
import android.arch.persistence.room.InvalidationTracker;
import android.arch.persistence.room.Room;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity implements InputDialogFragment.InputDialogListener {
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
    private Button eSendButton;

    private byte[] shareBytes;
    private MimeCode shareMime;
    private boolean shareEncrypt;

    private int messageCount;
    private Bitmap capturedImage;

    private LocationManager locationManager;
    private ExecutorService executorService;
    private List<CityDistance> nextCities;

    private void initViews() {
        listLayout = findViewById(R.id.listLayout);
        numberLine = findViewById(R.id.numberLine);
        sendImageView = findViewById(R.id.imageView);
        fileDetails = findViewById(R.id.fileDetails);
        telEdit = findViewById(R.id.telEdit);
        sbCompression = findViewById(R.id.sbCompression);
        sbSize = findViewById(R.id.sbSize);
        sendButton = findViewById(R.id.button);
        eSendButton = findViewById(R.id.ebutton);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();

        final MyApplication myApp = (MyApplication) this.getApplicationContext();

        executorService = Executors.newSingleThreadExecutor();

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MyAsyncSend(MainActivity.this).execute();
            }
        });
        eSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareEncrypt = shareEncrypt? false : true;
                myApp.setTel(telEdit.getText().toString());
                showShareBytes();
            }
        });
        handleIntent(getIntent());

        sbCompression.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser == false) return;

                progress *= 10;
                if (progress < 5) progress = 5;
                if (progress > 100) progress = 100;
                myApp.setCompression(progress);
                scaleCapturedImage();
                showShareBytes();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser == false) return;

                progress *= 40;
                if (progress < 20) progress = 20;
                myApp.setImageSize(progress);
                scaleCapturedImage();
                showShareBytes();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
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

        myApp.cleanupTmpFiles();
    }

    private void handleSendIntent(Intent intent, String type) {
        if (type.startsWith("image/") || type.startsWith("text/") || type.startsWith("application/")) {
            Log.d(getString(R.string.app_name), "mime type: " + type);

            Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (fileUri != null) {

                try {
                    InputStream inputStream = getContentResolver().openInputStream(fileUri);
                    ContentResolver contentResolver = getContentResolver();
                    String mimeType = contentResolver.getType(fileUri);
                    int fileSize = inputStream.available();
                    shareBytes = new byte[fileSize];
                    shareEncrypt = false;

                    shareMime = MimeCode.fromString(mimeType);
                    if (shareMime == MimeCode.BIN) {
                        shareMime = MimeCode.fromString(type);
                    }
                    inputStream.read(shareBytes);
                    inputStream.close();

                    if (shareMime == MimeCode.JPG) {
                        MyApplication myApp = (MyApplication) this.getApplicationContext();
                        capturedImage = BitmapFactory.decodeByteArray(shareBytes, 0, shareBytes.length);

                        try {
                            File imageFile = myApp.copyUriToTempFile(fileUri, shareMime);
                            if (imageFile != null) {
                                ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
                                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                                capturedImage = rotateImage(capturedImage, orientation);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        sbCompression.setProgress(myApp.getCompression() / 10);
                        sbSize.setProgress(myApp.getImageSize() / 40);
                        scaleCapturedImage();
                    }

                    showShareBytes();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int countSms(byte[] bytes)
    {
        return (int) Math.ceil((double) (bytes.length + 1) / BinarySMS.SEGMENT_SIZE);
    }

    private void showShareBytes() {
        MyApplication myApp = (MyApplication) this.getApplicationContext();

        Bitmap compressedBitmap = BitmapFactory.decodeByteArray(shareBytes, 0, shareBytes.length);

        initViews();

        sendImageView.setVisibility(View.VISIBLE);
        fileDetails.setVisibility(View.VISIBLE);
        numberLine.setVisibility(View.VISIBLE);
        telEdit.setVisibility(View.VISIBLE);

        telEdit.setText(myApp.getTel());
        fileDetails.setText("");

        if (capturedImage != null) {
            sbSize.setVisibility(View.VISIBLE);
            sbCompression.setVisibility(View.VISIBLE);
        }

        if (shareMime == MimeCode.GIF) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(shareBytes);
            try {
                GifDrawable gifDrawable = new GifDrawable(inputStream);
                sendImageView.setImageDrawable(gifDrawable);
            } catch (IOException e) {
                // todo
                e.printStackTrace();
                return;
            }
        } else if (shareMime == MimeCode.TXT) {

            fileDetails.append(previewText(shareBytes, 2 * BinarySMS.SEGMENT_SIZE) + "\n");

        } else if (shareMime == MimeCode.BIN) {

            fileDetails.append(getString(R.string.send_as_binary) + "\n");

        } else if (shareMime == MimeCode.GEO) {

            try {
                String geoTag = new String(shareBytes, "UTF-8");
                fileDetails.append(geoTag + "\n");

                if (nextCities != null) {
                    for (CityDistance nextCity : nextCities)
                    {
                        fileDetails.append(nextCity + "\n");
                    }
                    MapView mv = new MapView(this);
                    double[] p = extractGeo(geoTag);
                    mv.setLocations(p[0], p[1], nextCities);
                    sendImageView.setImageBitmap(mv.getBitmap());
                    sendImageView.setVisibility(View.VISIBLE);
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        } else {
            if (compressedBitmap != null) {
                sendImageView.setImageBitmap(compressedBitmap);
            }
        }

        if (shareEncrypt) {
            File appFolder = myApp.getAppFolder();
            String path = appFolder.getPath() + "/" + telEdit.getText().toString().replace("+", "00") + ".pub";
            try {
                File file = new File(path);
                if (file.exists()) {
                    shareBytes = RSAEncryption.encrypt(shareBytes, file);
                    shareMime = shareMime.getEncrypt();

                    // if the shareBytes are encrypted, these parts are hidden
                    sendImageView.setVisibility(View.GONE);
                    sbSize.setVisibility(View.GONE);
                    sbCompression.setVisibility(View.GONE);
                    telEdit.setVisibility(View.GONE);
                    eSendButton.setEnabled(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            eSendButton.setEnabled(true);
        }

        fileDetails.append(
            "mime: " + shareMime.toString() + "\n" +
            "bytes: " +
            String.valueOf(shareBytes.length) +
            " (" +
            String.valueOf(countSms(shareBytes)) +
            " SMS)");

        if (countSms(shareBytes) > 255) {
            sendButton.setEnabled(false);
        } else {
            sendButton.setEnabled(true);
        }
    }

    public static String previewText(byte[] bytes, int maxChars) {
        if (bytes == null || bytes.length == 0) return "";

        String text;
        try {
            text = new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            text = new String(bytes);
        }

        if (text.length() <= maxChars) {
            return text;
        } else {
            return text.substring(0, maxChars) + "...";
        }
    }

    @Override
    public void onInputDialogResult(String input) {
        shareMime = MimeCode.TXT;
        capturedImage = null;
        shareEncrypt = false;
        shareBytes = input.getBytes();
        showShareBytes();
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
            progressDialog.setMax(countSms(shareBytes));
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
            if (success) {
                Toast.makeText(context, getString(R.string.send_done), Toast.LENGTH_SHORT).show();

                sendImageView.setVisibility(View.GONE);
                fileDetails.setVisibility(View.GONE);
                telEdit.setVisibility(View.GONE);
                numberLine.setVisibility(View.GONE);
                sbSize.setVisibility(View.GONE);
                sbCompression.setVisibility(View.GONE);
                capturedImage = null;
                shareMime = MimeCode.BIN;
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

    private void updateInbox() {
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

        if (mime.isEncrypt())
        {
            MyApplication myApp = (MyApplication) this.getApplicationContext();

            File appFolder = myApp.getAppFolder();
            String path = appFolder.getPath() + "/my.pem";
            try {
                File file = new File(path);
                if (file.exists()) {
                    fullMessage = RSAEncryption.decrypt(fullMessage, file);
                    mime = mime.getDecrypt();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(fullMessage, 0, fullMessage.length);
        ImageView imageView = null;
        if (bitmap != null) {

            if (mime == MimeCode.GIF) {
                imageView = new GifImageView(this);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(fullMessage);
                try {
                    GifDrawable gifDrawable = new GifDrawable(inputStream);
                    imageView.setImageDrawable(gifDrawable);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            } else {
                imageView = new ImageView(this);
                imageView.setImageBitmap(bitmap);
            }

        } else {
            if (mime == MimeCode.TXT)
            {
                textView.append(previewText(fullMessage, 2 * BinarySMS.SEGMENT_SIZE) + "\n");
            }
            else if (mime == MimeCode.GEO)
            {
                try {
                    String text = new String(fullMessage, "UTF-8");
                    textView.append(text + "\n");

                    textView.setOnLongClickListener(new View.OnLongClickListener() {

                        @Override
                        public boolean onLongClick(View v) {
                            TextView tv = (TextView) v;
                            double[] loc = extractGeo(tv.getText().toString());

                            if (loc != null) openMapApp(loc[0], loc[1]);

                            return true;
                        }
                    });
                    textView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            TextView tv = (TextView) v;
                            final double[] loc = extractGeo(tv.getText().toString());

                            executorService.execute(new Runnable() {
                                @Override
                                public void run() {
                                    MyApplication myApp = (MyApplication) getApplicationContext();
                                    nextCities = myApp.nextCities(loc[0], loc[1]);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            MapView mv = new MapView(MainActivity.this);
                                            mv.setLocations(loc[0], loc[1], nextCities);
                                            mv.showMapViewDialog(MainActivity.this);
                                        }
                                    });

                                }
                            });
                        }
                    });


                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }

        if (imageView != null) {
            LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            imageView.setLayoutParams(params2);
            imageView.setPadding(MESSAGE_PADDING, MESSAGE_PADDING, MESSAGE_PADDING, MESSAGE_PADDING);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            if (messageCount % 2 == 0) {
                imageView.setBackgroundResource(R.color.halfLine1);
            } else {
                imageView.setBackgroundResource(R.color.halfLine2);
            }
            listLayout.addView(imageView);
        }

        textView.append(
            "mime: " + mime.toString() + "\n" +
            "bytes: " +
            String.valueOf(fullMessage.length) +
            " (" +
            String.valueOf(countSms(fullMessage)) +
            " SMS)\n\n");
        textView.setTypeface(Typeface.MONOSPACE);
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
        textView.setPadding(MESSAGE_PADDING, MESSAGE_PADDING, MESSAGE_PADDING, MESSAGE_PADDING);
        listLayout.addView(textView);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("byte_data")) {

            byte[] fullMessage = intent.getByteArrayExtra("byte_data");
            String address = intent.getStringExtra("address");
            String dateStr = intent.getStringExtra("date");
            MimeCode mime = MimeCode.fromByte(intent.getByteExtra("byte_mime", MimeCode.BIN.getCode()));
            if (fullMessage != null) {
                showMessage(dateStr, mime, address, fullMessage);
            }
        } else if (intent != null && intent.getAction() != null && intent.getType() != null) {
            String action = intent.getAction();
            String type = intent.getType();

            if (Intent.ACTION_SEND.equals(action)) {
                if (PermissionUtils.externalGranted(this)) {
                    handleSendIntent(intent, type);
                } else {
                    String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                    PermissionUtils.requestPermissions(this, MyApplication.PERMISSION_REQ, permissions);
                }
            }
        } else {
            if (PermissionUtils.allGranted(this) == false) {
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
        shareMime = MimeCode.BIN;
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

    private void scaleCapturedImage() {
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

    public double[] extractGeo(String geoString) {
        String[] lines = geoString.split("\n");

        for (String line : lines) {
            if (line.startsWith("geo:")) {
                String coordinates = line.substring(4);
                String[] parts = coordinates.split(",");

                if (parts.length == 2) {
                    try {
                        double latitude = Double.parseDouble(parts[0]);
                        double longitude = Double.parseDouble(parts[1]);
                        return new double[]{latitude, longitude};
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    private String formatGeo(double latitude, double longitude) {
        String formattedLatitude = String.format(Locale.US, "%.6f", latitude);
        String formattedLongitude = String.format(Locale.US, "%.6f", longitude);

        return "geo:" + formattedLatitude + "," + formattedLongitude;
    }

    private void openMapApp(double latitude, double longitude) {
        Uri gmmIntentUri = Uri.parse(formatGeo(latitude, longitude));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        startActivity(mapIntent);
    }

    @SuppressLint("MissingPermission")
    private void getLocationUpdates() {

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                final double latitude = location.getLatitude();
                final double longitude = location.getLongitude();

                Log.d(getString(R.string.app_name), "Latitude: " + latitude + ", Longitude: " + longitude);

                locationManager.removeUpdates(this);

                try {
                    shareBytes = formatGeo(latitude, longitude).getBytes("UTF-8");
                    capturedImage = null;
                    shareEncrypt = false;
                    shareMime = MimeCode.GEO;
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            MyApplication myApp = (MyApplication) getApplicationContext();
                            nextCities = myApp.nextCities(latitude, longitude);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showShareBytes();
                                }
                            });

                        }
                    });

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        if (PermissionUtils.locationGranted(this)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
        } else {
            String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            PermissionUtils.requestPermissions(this, MyApplication.LOCATION_PERMISSION, permissions);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean granted = false;
        switch (requestCode) {
            case MyApplication.PERMISSION_REQ:
                granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    Toast.makeText(this, getString(R.string.restart_me), Toast.LENGTH_LONG).show();
                }
                break;
            case MyApplication.LOCATION_PERMISSION:
                granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    getLocationUpdates();
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
        outState.putBoolean("shareEncrypt", shareEncrypt);

        if (capturedImage != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            capturedImage.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            outState.putByteArray("capturedBytes", stream.toByteArray());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        shareEncrypt = savedInstanceState.getBoolean("shareEncrypt");
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
            /*
            File f = myApp.createAppFile("_temp.jpg");
            try {
                f.getCanonicalFile().delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
            */

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
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActive = false;
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
            case R.id.action_button_location:
                nextCities = null;
                Toast.makeText(MainActivity.this, getString(R.string.location_wait), Toast.LENGTH_SHORT).show();
                getLocationUpdates();
                return true;
            case R.id.action_button:
                dispatchTakePictureIntent();
                return true;
            case R.id.action_button_text:
                InputDialogFragment dialog = new InputDialogFragment();
                dialog.show(getSupportFragmentManager(), "InputDialogFragment");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
