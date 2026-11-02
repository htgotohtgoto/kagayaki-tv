package jp.kagayaki.signage;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String START_URL = "https://kagayaki.securesite.jp/dhonkan.AKfycbw851aTGx3Ky88N2hZb_xnuWwZo4t1V4oWC-f0SJ7NaktjBktLNGYfm35ej6bJiBYhBlg.html";
    private static final long RETRY_INTERVAL_MS = 5000;
    private static final int SHOW_GUIDE_RETRY_COUNT = 6;

    private WebView webView;
    private LinearLayout waitingOverlay;
    private TextView statusText;
    private TextView subGuideBox;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private boolean hasErrorInCurrentLoad = false;
    private boolean isRetryScheduled = false;

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            isRetryScheduled = false;
            if (webView != null) {
                hasErrorInCurrentLoad = false;
                statusText.setText("ネットワーク接続を待機しています... (自動再接続: " + retryCount + "回目)\n（Wi-Fiが繋がると自動的に開始します。そのままお待ちください）");
                webView.loadUrl(START_URL);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#0d1b2a"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0d1b2a"));
        setupWebViewSettings();

        waitingOverlay = createWaitingOverlay();

        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.addView(waitingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootLayout);
        webView.loadUrl(START_URL);
    }

    private void setupWebViewSettings() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!hasErrorInCurrentLoad && !"about:blank".equals(url)) {
                    handler.removeCallbacks(retryRunnable);
                    isRetryScheduled = false;
                    retryCount = 0;
                    waitingOverlay.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    webView.requestFocus();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    handleMainFrameError();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (failingUrl != null && failingUrl.equals(START_URL)) {
                    handleMainFrameError();
                }
            }
        });
    }

    private void handleMainFrameError() {
        hasErrorInCurrentLoad = true;
        retryCount++;

        webView.setVisibility(View.INVISIBLE);
        waitingOverlay.setVisibility(View.VISIBLE);

        statusText.setText("ネットワーク接続を待機しています...\n（Wi-Fiが繋がると自動的に開始します。そのままお待ちください）");

        if (retryCount >= SHOW_GUIDE_RETRY_COUNT) {
            subGuideBox.setVisibility(View.VISIBLE);
        }

        if (!isRetryScheduled) {
            isRetryScheduled = true;
            handler.postDelayed(retryRunnable, RETRY_INTERVAL_MS);
        }
    }

    private LinearLayout createWaitingOverlay() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#0d1b2a"));
        int pad = dpToPx(36);
        layout.setPadding(pad, pad, pad, pad);

        ImageView logoView = new ImageView(this);
        int logoResId = getResources().getIdentifier("logo", "drawable", getPackageName());
        if (logoResId == 0) {
            logoResId = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        }
        if (logoResId != 0) {
            logoView.setImageResource(logoResId);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dpToPx(240), dpToPx(130));
            logoParams.bottomMargin = dpToPx(24);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layout.addView(logoView, logoParams);
        }

        TextView titleView = new TextView(this);
        titleView.setText("かがやきサイネージ");
        titleView.setTextColor(Color.parseColor("#00e676"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dpToPx(18);
        layout.addView(titleView, titleParams);

        statusText = new TextView(this);
        statusText.setText("ネットワーク接続を待機しています...\n（Wi-Fiが繋がると自動的に開始します。そのままお待ちください）");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        statusText.setTypeface(null, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusText.setLineSpacing(dpToPx(6), 1.0f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.bottomMargin = dpToPx(28);
        layout.addView(statusText, statusParams);

        subGuideBox = new TextView(this);
        subGuideBox.setText(
                "【1分以上この画面が変わらない場合の対処法】\n" +
                "① リモコンの「ホーム（家マーク）」ボタンを押し、数秒待ってからもう一度「かがやきサイネージ」を開き直してください。\n" +
                "② それでも繋がらない場合は、施設のWi-Fiルーター（親機）とテレビ裏の機器（Google TV）の電源コンセントを抜き、5分ほど待ってから差し直してください。"
        );
        subGuideBox.setTextColor(Color.parseColor("#ffeb3b"));
        subGuideBox.setBackgroundColor(Color.parseColor("#1b263b"));
        subGuideBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        subGuideBox.setGravity(Gravity.LEFT);
        subGuideBox.setLineSpacing(dpToPx(5), 1.0f);
        int boxPad = dpToPx(20);
        subGuideBox.setPadding(boxPad, boxPad, boxPad, boxPad);
        subGuideBox.setVisibility(View.GONE);

        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(
                dpToPx(780),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layout.addView(subGuideBox, guideParams);

        return layout;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (waitingOverlay != null && waitingOverlay.getVisibility() == View.VISIBLE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                handler.removeCallbacks(retryRunnable);
                retryRunnable.run();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(retryRunnable);
        super.onDestroy();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
