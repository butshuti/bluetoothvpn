package edu.unt.nslab.butshuti.bluetoothvpn.utils.measurement;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Pair;
import android.widget.ImageView;

/**
 * Created by hazirex on 12/1/17.
 */

public class CDFDiagram {

    private final static float STROKE_SCALE_RATIO = 0.9f;
    private ImageView view;

    public CDFDiagram(ImageView view){
        this.view = view;
    }

    public void draw(Pair<Float, Float> histogram[], String arg, String units){
        if(histogram == null || histogram.length == 0){
            return;
        }
        int height = view.getHeight();
        int width = view.getWidth();
        if(width <=0 || height <= 0){
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        float cHeight = (float)(canvas.getHeight() * STROKE_SCALE_RATIO);
        float cWidth = (float)(canvas.getWidth() * STROKE_SCALE_RATIO);
        int xPadding = (int)(canvas.getWidth() * (1 - STROKE_SCALE_RATIO));
        int yPadding = (int)(canvas.getHeight() * (1 - STROKE_SCALE_RATIO));
        Rect strokes[] = new Rect[histogram.length];
        float prevX = 0;
        float strokeWidthUnit = cWidth / histogram[histogram.length-1].first;
        for(int i=0; i<histogram.length; i++){
            float y = STROKE_SCALE_RATIO * histogram[i].second / histogram[histogram.length-1].second;
            int strokeWidth = (int)(strokeWidthUnit * (histogram[i].first - prevX));
            int strokeXLext = (int)(strokeWidthUnit * prevX);
            int strokeXRight = (strokeXLext + strokeWidth - (int)(strokeWidth*0.15));
            int strokeYTop = invertY(0, cHeight, yPadding/2);
            int strokeYBottom = invertY(cHeight*y, cHeight, 0);
            strokes[i] = new Rect(strokeXLext + xPadding*2/3, strokeYTop, strokeXRight + xPadding*2/3, strokeYBottom);
            prevX = histogram[i].first;
        }
        Paint stroke = new Paint();
        Paint background = new Paint();
        background.setColor(Color.WHITE);
        background.setStyle(Paint.Style.FILL);
        stroke.setColor(0xFF5663B1);
        stroke.setStyle(Paint.Style.FILL);
        canvas.drawRect(new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), background);
        int xLineYCoords = invertY(0, cHeight, yPadding/2);
        canvas.drawLine(0, xLineYCoords, width, xLineYCoords, stroke);
        canvas.drawText(arg + "(" + units + ")", 0, invertY(0, cHeight, -yPadding), stroke);
        background.setStyle(Paint.Style.FILL_AND_STROKE);
        for(int i=0; i<histogram.length; i++){
            Rect rect = strokes[i];
            canvas.drawRect(rect, stroke);
            canvas.drawText("######", rect.left-2, invertY(0, cHeight, -yPadding/2), background);
            canvas.drawText(String.valueOf(histogram[i].first), rect.left, invertY(0, cHeight, -yPadding/2), stroke);
        }
        canvas.drawLine(xPadding*2/3, 0, xPadding*2/3, cHeight, stroke);
        int scaledHeight = (int)(cHeight*STROKE_SCALE_RATIO);
        for(int i=0; i<=scaledHeight; i+=(scaledHeight/5)){
            canvas.drawText(String.format("%.2f", 1.0*i/scaledHeight), 0, invertY(i*STROKE_SCALE_RATIO, cHeight, yPadding/2), stroke);
        }
        view.setImageBitmap(bitmap);
    }

    private static int invertY(float y, float height, float padding){
        return (int)(height - (y + padding));
    }
}
