package com.example.billscanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private PreviewView viewFinder;
    private TextView resultText;
    private TextView validHistoryText;
    private TextView invalidHistoryText;
    private Switch debugSwitch;
    private Switch cameraSwitch;
    private Button resetButton;
    private BillAnalyzer billAnalyzer;
    private ProcessCameraProvider cameraProvider;
    private boolean useFrontCamera = false;

    private final Set<String> processedSerials = new HashSet<>();
    private final List<String> validDisplayList = new ArrayList<>();
    private final List<String> invalidDisplayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        resultText = findViewById(R.id.resultText);
        validHistoryText = findViewById(R.id.validHistoryText);
        invalidHistoryText = findViewById(R.id.invalidHistoryText);
        debugSwitch = findViewById(R.id.debugSwitch);
        cameraSwitch = findViewById(R.id.cameraSwitch);
        resetButton = findViewById(R.id.resetButton);

        validHistoryText.setMovementMethod(new ScrollingMovementMethod());
        invalidHistoryText.setMovementMethod(new ScrollingMovementMethod());

        billAnalyzer = new BillAnalyzer();

        cameraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useFrontCamera = !isChecked;
            restartCamera();
        });

        resetButton.setOnClickListener(v -> {
            processedSerials.clear();
            validDisplayList.clear();
            invalidDisplayList.clear();
            updateHistoryUI();
        });

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraPreview();
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void restartCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            bindCameraPreview();
        }
    }

    private void bindCameraPreview() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .addOnSuccessListener(visionText -> {
                    BillAnalyzer.AnalysisResult result = billAnalyzer.analyze(visionText, debugSwitch.isChecked());
                    resultText.setText(result.status);

                    boolean historyChanged = false;
                    for (BillAnalyzer.DetectedSerial ds : result.detectedSerials) {
                        if (processedSerials.add(ds.serialDigits)) {
                            if (ds.isObserved) {
                                invalidDisplayList.add(ds.fullText);
                            } else {
                                validDisplayList.add(ds.fullText);
                            }
                            historyChanged = true;
                        }
                    }

                    if (historyChanged) {
                        updateHistoryUI();
                    }
                })
                .addOnCompleteListener(task -> image.close());
        });

        CameraSelector cameraSelector = useFrontCamera
            ? CameraSelector.DEFAULT_FRONT_CAMERA
            : CameraSelector.DEFAULT_BACK_CAMERA;

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void updateHistoryUI() {
        StringBuilder validBuilder = new StringBuilder("VÁLIDOS:\n");
        for (String s : validDisplayList) {
            validBuilder.append("• ").append(s).append("\n");
        }
        validHistoryText.setText(validBuilder.toString());

        StringBuilder invalidBuilder = new StringBuilder("OBSERVADOS:\n");
        for (String s : invalidDisplayList) {
            invalidBuilder.append("• ").append(s).append("\n");
        }
        invalidHistoryText.setText(invalidBuilder.toString());
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}
