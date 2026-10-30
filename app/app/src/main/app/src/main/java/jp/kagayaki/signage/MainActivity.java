package jp.kagayaki.signage;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "KagayakiSignagePrefs";
    private static final String KEY_START_URL = "start_url";
    private static final int PERMISSION_REQ_CODE = 1001;

    private WebView webView;
    private SharedPreferences prefs;
    private String registeredHost = "";

    private int secretKeyCount = 0;
    private long lastSecretKeyTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#f4f1ea"));
        setContentView(webView);

        setupSecureWebView();
        requestOsRuntimePermissions();
    }

    private void requestOsRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            };
            List<String> needed = new ArrayList<>();
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), PERMISSION_REQ_CODE);
                return;
            }
        }
        bootOrShowUrlSetup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        bootOrShowUrlSetup();
    }

    private void bootOrShowUrlSetup() {
        String savedUrl = prefs.getString(KEY_START_URL, "");
        if (savedUrl == null || savedUrl.trim().isEmpty()) {
            showFacilityUrlDialog("");
        } else {
            loadFacilityHubUrl(savedUrl.trim());
        }
    }

    private void loadFacilityHubUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                Toast.makeText(this, "セキュリティ保護のため https:// で始まるURLのみ設定可能です", Toast.LENGTH_LONG).show();
                showFacilityUrlDialog(url);
                return;
            }
            registeredHost = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            webView.loadUrl(url);
        } catch (Exception e) {
            showFacilityUrlDialog(url);
        }
    }

    private boolean isTrustedOrigin(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        if (host.isEmpty()) return false;

        if (!registeredHost.isEmpty() && (host.equals(registeredHost) || host.endsWith("." + registeredHost))) {
            return true;
        }
        return host.equals("script.google.com")
            || host.endsWith(".script.google.com")
            || host.endsWith(".googleusercontent.com")
            || host.equals("docs.google.com")
            || host.equals("drive.google.com");
    }

    private void setupSecureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public boolean isNativeKiosk() {
                return true;
            }
        }, "KagayakiNativeApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri != null && "https".equalsIgnoreCase(uri.getScheme())) {
                    return false;
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    Uri origin = request.getOrigin();
                    if (isTrustedOrigin(origin)) {
                        request.grant(request.getResources());
                    } else {
                        request.deny();
                    }
                });
            }
        });
    }

    private void showFacilityUrlDialog(String currentUrl) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = 40;
        layout.setPadding(pad, pad, pad, pad);

        TextView note = new TextView(this);
        note.setText("この施設のハブURL（https://.../index.html）を入力してください。\n（※運用中に再設定する場合はリモコンの「0」または「戻る」を5連打）");
        note.setTextSize(16f);
        layout.addView(note);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("https://example.com/index.html");
        input.setText(currentUrl);
        layout.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("Kagayaki Signage - 施設URL設定")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("保存して起動", (dialog, which) -> {
                String entered = input.getText().toString().trim();
                if (!entered.startsWith("https://")) {
                    Toast.makeText(MainActivity.this, "https:// から始まるURLを入力してください", Toast.LENGTH_LONG).show();
                    showFacilityUrlDialog(entered);
                    return;
                }
                prefs.edit().putString(KEY_START_URL, entered).apply();
                hideSystemUI();
                loadFacilityHubUrl(entered);
            })
            .setNeutralButton("再読み込み", (dialog, which) -> {
                hideSystemUI();
                webView.reload();
            })
            .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_0 || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_BACK) {
                long now = System.currentTimeMillis();
                if (now - lastSecretKeyTime > 800) {
                    secretKeyCount = 1;
                } else {
                    secretKeyCount++;
                }
                lastSecretKeyTime = now;

                if (secretKeyCount >= 5) {
                    secretKeyCount = 0;
                    String saved = prefs.getString(KEY_START_URL, "");
                    showFacilityUrlDialog(saved);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_BACK) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }
}
