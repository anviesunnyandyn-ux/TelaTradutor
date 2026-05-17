package com.telatradutor.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingBubbleService extends Service {
    private WindowManager windowManager;
    private TextView bubble;
    private WindowManager.LayoutParams params;
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        bubble = new TextView(this);
        bubble.setText("TR");
        bubble.setTextSize(18);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setBackgroundColor(0xFF4F46E5);
        bubble.setPadding(24, 24, 24, 24);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 80;
        params.y = 200;

        bubble.setOnClickListener(v -> Toast.makeText(
                this,
                "Próxima fase: capturar tela e detectar texto.",
                Toast.LENGTH_SHORT
        ).show());

        bubble.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(bubble, params);
                    return true;
            }
            return false;
        });

        windowManager.addView(bubble, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubble != null) windowManager.removeView(bubble);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
