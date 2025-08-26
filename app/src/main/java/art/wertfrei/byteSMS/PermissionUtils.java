package art.wertfrei.byteSMS;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

public final class PermissionUtils {

    private PermissionUtils() {
    }

    public static boolean checkPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean writeGranted(Context context) {
        return checkPermission(context, Manifest.permission.SEND_SMS);
    }

    public static boolean externalGranted(Context context) {
        return (checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE));
    }

    public static boolean allGranted(Context context) {

        return (checkPermission(context, Manifest.permission.RECEIVE_SMS) &&
                checkPermission(context, Manifest.permission.RECEIVE_MMS) &&
                checkPermission(context, Manifest.permission.SEND_SMS) &&
                checkPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                checkPermission(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) &&
                checkPermission(context, Manifest.permission.VIBRATE) &&
                checkPermission(context, Manifest.permission.ACCESS_NOTIFICATION_POLICY) &&
                checkPermission(context, Manifest.permission.CAMERA)
        );
    }

    public static void requestPermissions(Object o, int permissionId, String... permissions) {
        if (o instanceof Activity) {
            ActivityCompat.requestPermissions((AppCompatActivity) o, permissions, permissionId);
        }
    }
}
