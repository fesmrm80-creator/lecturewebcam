package com.lecture.webcam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextView tvStatus;
    private ValueCallback<Uri[]> filePathCallback;
    private String selectedVideoBase64 = null;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Toast.makeText(this, "تم تحديث الصلاحيات", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<Intent> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri videoUri = result.getData().getData();
                    if (videoUri != null) {
                        tvStatus.setText("الحالة: جارٍ تجهيز الفيديو...");
                        loadVideoAsDataUrl(videoUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnPickVideo = findViewById(R.id.btnPickVideo);
        Button btnReload = findViewById(R.id.btnReload);

        requestNeededPermissions();
        setupWebView();

        btnPickVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            videoPickerLauncher.launch(Intent.createChooser(intent, "اختر فيديو الدرس"));
        });

        btnReload.setOnClickListener(v -> webView.reload());

        webView.loadUrl("https://web.whatsapp.com");
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void loadVideoAsDataUrl(Uri uri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) return;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[8192];
                int nRead;
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                inputStream.close();
                String base64 = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
                selectedVideoBase64 = "data:video/mp4;base64," + base64;

                runOnUiThread(() -> {
                    tvStatus.setText("الحالة: تم تحميل الفيديو بنجاح وجاهز للبث في المتصفح");
                    injectVideoOverrideJs();
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("الحالة: تعذر قراءة الفيديو (جرّب ملف MP4 أصغر حجماً)"));
            }
        }).start();
    }

    private void injectVideoOverrideJs() {
        if (selectedVideoBase64 == null) return;
        String js = "(function() {" +
                "  if (!navigator.mediaDevices) navigator.mediaDevices = {};" +
                "  navigator.mediaDevices.getUserMedia = async function(constraints) {" +
                "    if (constraints && constraints.video) {" +
                "      const video = document.createElement('video');" +
                "      video.src = '" + selectedVideoBase64 + "';" +
                "      video.loop = true;" +
                "      video.muted = true;" +
                "      video.playsInline = true;" +
                "      await video.play();" +
                "      const stream = video.captureStream ? video.captureStream() : video.mozCaptureStream();" +
                "      return stream;" +
                "    }" +
                "    return new MediaStream();" +
                "  };" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUserAgentString(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (selectedVideoBase64 != null) {
                    injectVideoOverrideJs();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    videoPickerLauncher.launch(intent);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }
}
