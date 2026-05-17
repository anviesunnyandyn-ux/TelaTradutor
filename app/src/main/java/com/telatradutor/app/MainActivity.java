package com.telatradutor.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private static final int REQUEST_SCREEN_CAPTURE = 5001;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ImageView previewImage;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(40, 40, 40, 40);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("TelaTradutor");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        TextView description = new TextView(this);
        description.setText("Versão 0.2.0: bolha flutuante + teste de captura da tela. Depois vamos ligar isso ao OCR e tradução.");
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 24, 0, 24);

        Button permissionButton = new Button(this);
        permissionButton.setText("1. Permitir sobreposição");
        permissionButton.setOnClickListener(v -> requestOverlayPermission());

        Button startButton = new Button(this);
        startButton.setText("2. Iniciar bolha flutuante");
        startButton.setOnClickListener(v -> startFloatingBubble());

        Button captureButton = new Button(this);
        captureButton.setText("3. Testar captura de tela");
        captureButton.setOnClickListener(v -> requestScreenCapture());

        statusText = new TextView(this);
        statusText.setText("Status: aguardando teste.");
        statusText.setTextSize(15);
        statusText.setPadding(0, 28, 0, 16);
        statusText.setGravity(Gravity.CENTER);

        previewImage = new ImageView(this);
        previewImage.setAdjustViewBounds(true);
        previewImage.setMaxHeight(900);

        root.addView(title);
        root.addView(description);
        root.addView(permissionButton);
        root.addView(startButton);
        root.addView(captureButton);
        root.addView(statusText);
        root.addView(previewImage);
        setContentView(scrollView);
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Permissão de sobreposição já liberada.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFloatingBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Libere a permissão de sobreposição primeiro.", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        startService(new Intent(this, FloatingBubbleService.class));
        Toast.makeText(this, "Bolha iniciada.", Toast.LENGTH_LONG).show();
    }

    private void requestScreenCapture() {
        if (projectionManager == null) {
            Toast.makeText(this, "MediaProjection não está disponível neste aparelho.", Toast.LENGTH_LONG).show();
            return;
        }
        statusText.setText("Status: pedindo permissão de captura...");
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                statusText.setText("Status: permissão liberada. Capturando imagem...");
                startProjection(resultCode, data);
            } else {
                statusText.setText("Status: permissão de captura negada.");
                Toast.makeText(this, "Você negou a captura de tela.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startProjection(int resultCode, Intent data) {
        stopProjection();

        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            statusText.setText("Status: erro ao iniciar captura.");
            return;
        }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                runOnUiThread(() -> statusText.setText("Status: captura finalizada."));
            }
        }, new Handler(Looper.getMainLooper()));

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "TelaTradutorCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );

        new Handler(Looper.getMainLooper()).postDelayed(this::captureOneFrame, 900);
    }

    private void captureOneFrame() {
        if (imageReader == null) return;

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                statusText.setText("Status: não consegui capturar a imagem. Tente de novo.");
                return;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            Bitmap bitmap = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
            previewImage.setImageBitmap(cropped);
            statusText.setText("Status: captura feita com sucesso. Próxima fase: OCR.");
            Toast.makeText(this, "Captura feita!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            statusText.setText("Status: erro na captura: " + e.getMessage());
        } finally {
            if (image != null) image.close();
            stopProjection();
        }
    }

    private void stopProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopProjection();
        super.onDestroy();
    }
}
