package com.telatradutor.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 2001;

    private TextView statusText;
    private ImageView previewImage;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private boolean imageCaptured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        buildLayout();
    }

    private void buildLayout() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(36, 48, 36, 48);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("TelaTradutor");
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);

        TextView description = new TextView(this);
        description.setText("Versão 0.4.0: correção da captura de tela. Depois vamos ligar isso ao OCR e tradução.");
        description.setTextSize(17);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 22, 0, 28);

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
        statusText.setTextSize(17);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 28, 0, 18);

        previewImage = new ImageView(this);
        previewImage.setAdjustViewBounds(true);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        imageParams.setMargins(0, 10, 0, 0);

        root.addView(title);
        root.addView(description);
        root.addView(permissionButton, fullWidthParams());
        root.addView(startButton, fullWidthParams());
        root.addView(captureButton, fullWidthParams());
        root.addView(statusText);
        root.addView(previewImage, imageParams);
        setContentView(scrollView);
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        return params;
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Permissão já liberada.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFloatingBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Libere a permissão de sobreposição primeiro.", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        startService(new Intent(this, FloatingBubbleService.class));
        Toast.makeText(this, "Bolha iniciada.", Toast.LENGTH_SHORT).show();
    }

    private void requestScreenCapture() {
        statusText.setText("Status: pedindo permissão de captura...");
        previewImage.setImageDrawable(null);
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA_PROJECTION) return;

        if (resultCode != RESULT_OK || data == null) {
            statusText.setText("Status: permissão de captura negada.");
            Toast.makeText(this, "Captura cancelada.", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Status: permissão aceita. Capturando imagem...");
        cleanupCaptureResources();
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            statusText.setText("Status: não foi possível iniciar a captura.");
            return;
        }
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                runOnUiThread(() -> statusText.setText("Status: captura encerrada."));
            }
        }, new Handler(getMainLooper()));
        startOneShotCapture();
    }

    private void startOneShotCapture() {
        cleanupDisplayResources();
        imageCaptured = false;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        captureThread = new HandlerThread("TelaTradutorCaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, android.graphics.PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            if (imageCaptured) return;
            imageCaptured = true;
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;
                Bitmap bitmap = imageToBitmap(image);
                runOnUiThread(() -> {
                    previewImage.setImageBitmap(bitmap);
                    statusText.setText("Status: captura funcionou! A imagem apareceu abaixo.");
                    Toast.makeText(this, "Captura funcionou.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> statusText.setText("Status: erro na captura: " + message));
            } finally {
                if (image != null) image.close();
                runOnUiThread(() -> new Handler().postDelayed(this::cleanupCaptureResources, 700));
            }
        }, captureHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "TelaTradutorOneShot",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
        );
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        int bitmapWidth = screenWidth + rowPadding / pixelStride;

        Bitmap paddedBitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888);
        paddedBitmap.copyPixelsFromBuffer(buffer);
        Bitmap croppedBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, screenWidth, screenHeight);
        paddedBitmap.recycle();
        return croppedBitmap;
    }

    private void cleanupDisplayResources() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
    }

    private void cleanupCaptureResources() {
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
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
    }

    @Override
    protected void onDestroy() {
        cleanupCaptureResources();
        super.onDestroy();
    }
}
