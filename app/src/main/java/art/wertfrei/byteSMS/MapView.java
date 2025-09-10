package art.wertfrei.byteSMS;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class MapView extends View {
    public static int DEFAULT_WIDTH = 300;
    public static int DEFAULT_HEIGHT = 200;
    public static int PADDING = 20;


    private List<CityDistance> locations;
    private double latitude;
    private double longitude;
    private Paint paint;
    private int width, height;

    public MapView(Context context) {
        super(context);
        init();
    }

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setLocations(double lat, double lon, List<CityDistance> locations) {
        this.latitude = lat;
        this.longitude = lon;
        this.locations = locations;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.TRANSPARENT);

        double minLat = latitude;
        double maxLat = latitude;
        double minLon = longitude;
        double maxLon = longitude;

        for (CityDistance location : locations) {
            minLat = Math.min(minLat, location.city.getLatitude());
            maxLat = Math.max(maxLat, location.city.getLatitude());
            minLon = Math.min(minLon, location.city.getLongitude());
            maxLon = Math.max(maxLon, location.city.getLongitude());
        }

        float latRange = (float) (maxLat - minLat);
        float lonRange = (float) (maxLon - minLon);

        float xCurrent = (float) ((longitude - minLon) / lonRange * (width - 2 * PADDING)) + PADDING;
        float yCurrent = (float) ((maxLat - latitude) / latRange * (height - 2 * PADDING)) + PADDING;

        paint.setColor(Color.RED);
        canvas.drawLine(xCurrent - 4, yCurrent, xCurrent + 4, yCurrent, paint);
        canvas.drawLine(xCurrent, yCurrent - 4, xCurrent, yCurrent + 4, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(14);

        for (CityDistance location : locations) {
            String name = location.city.getName();
            float x = (float) ((location.city.getLongitude() - minLon) / lonRange * (width - 2 * PADDING)) + PADDING;
            float y = (float) ((maxLat - location.city.getLatitude()) / latRange * (height - 2 * PADDING)) + PADDING;

            canvas.drawCircle(x, y, 2, paint);

            String cityShortName = name.substring(0, Math.min(10, name.length()));
            float textX = x + 6;
            float textY = y - 6;

            if (textX + paint.measureText(cityShortName) > width - PADDING) {
                textX = width - PADDING - paint.measureText(cityShortName);
            }
            if (textY < PADDING) {
                textY = y + 16;
            }

            canvas.drawText(cityShortName, textX, textY, paint);
        }
    }


    public Bitmap getBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(DEFAULT_WIDTH, DEFAULT_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        this.layout(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.draw(canvas);

        return bitmap;
    }

    public void showMapViewDialog(Context context) {
        if (locations == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.mapview_dialog, null);

        ImageView imageView = dialogView.findViewById(R.id.dialog_image);
        TextView textView = dialogView.findViewById(R.id.dialog_text);
        Button okButton = dialogView.findViewById(R.id.dialog_ok_button);

        imageView.setImageBitmap(getBitmap());

        textView.setText("");
        for (CityDistance nextCity : locations)
        {
            textView.append(nextCity + "\n");
        }

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}

