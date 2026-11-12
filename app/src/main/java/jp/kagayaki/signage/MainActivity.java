package jp.kagayaki.signage;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

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

    // Android標準 TTS（音声合成）エンジン管理
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private final List<Voice> jaVoices = new ArrayList<>();

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

        // Android標準の音声合成(TTS)エンジンを初期化
        tts = new TextToSpeech(this, this);

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

        // 親ハブHTMLから呼び出せる AndroidTTS ブリッジを登録
        webView.addJavascriptInterface(new AndroidTtsBridge(), "AndroidTTS");

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

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int res = tts.setLanguage(Locale.JAPANESE);
            isTtsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED);

            if (isTtsReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    jaVoices.clear();
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        for (Voice v : voices) {
                            if (v != null && v.getLocale() != null
                                    && "ja".equals(v.getLocale().getLanguage())
                                    && !v.isNetworkConnectionRequired()) {
                                jaVoices.add(v);
                            }
                        }
                        Collections.sort(jaVoices, new Comparator<Voice>() {
                            @Override
                            public int compare(Voice a, Voice b) {
                                return a.getName().compareTo(b.getName());
                            }
                        });
                    }
                } catch (Exception ignored) {
                    jaVoices.clear();
                }
            }

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    if (utteranceId != null) {
                        notifyHubTtsEvent(utteranceId, "started", null);
                    }
                }

                @Override
                public void onDone(String utteranceId) {
                    if (utteranceId != null) {
                        notifyHubTtsEvent(utteranceId, "ended", null);
                    }
                }

                @Deprecated
                @Override
                public void onError(String utteranceId) {
                    if (utteranceId != null) {
                        notifyHubTtsEvent(utteranceId, "error", "ANDROID_TTS_ERROR");
                    }
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    if (utteranceId != null) {
                        notifyHubTtsEvent(utteranceId, "error", "ANDROID_TTS_ERROR_" + errorCode);
                    }
                }
            });
        }
    }

    private void notifyHubTtsEvent(final String requestId, final String eventType, final String code) {
        final String safeId = requestId.replace("'", "\\'");
        final String safeCode = (code != null) ? ("'" + code.replace("'", "\\'") + "'") : "null";
        final String js = "if(window.HubAudioEngine && window.HubAudioEngine.onAndroidTtsEvent){"
                + "window.HubAudioEngine.onAndroidTtsEvent('" + safeId + "','" + eventType + "'," + safeCode + ");}";
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    try {
                        webView.evaluateJavascript(js, null);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    public class AndroidTtsBridge {

        @JavascriptInterface
        public boolean isAvailable() {
            return isTtsReady;
        }

        @JavascriptInterface
        public void speak(final String requestId, final String text, final String roleKey, final float rate, final float pitch) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isTtsReady || tts == null) {
                        notifyHubTtsEvent(requestId, "error", "TTS_NOT_READY");
                        return;
                    }
                    try {
                        tts.stop();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !jaVoices.isEmpty()) {
                            int idx = 0;
                            if ("OURA".equals(roleKey)) {
                                idx = 1 % jaVoices.size();
                            } else if ("NAKA".equals(roleKey)) {
                                idx = 2 % jaVoices.size();
                            } else if ("YOCCHAN".equals(roleKey)) {
                                idx = 3 % jaVoices.size();
                            }
                            tts.setVoice(jaVoices.get(idx));
                        }

                        float clampedRate = Math.max(0.4f, Math.min(2.5f, rate));
                        float clampedPitch = Math.max(0.3f, Math.min(2.0f, pitch));
                        tts.setSpeechRate(clampedRate);
                        tts.setPitch(clampedPitch);

                        Bundle params = new Bundle();
                        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);

                        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, requestId);
                        if (result == TextToSpeech.ERROR) {
                            notifyHubTtsEvent(requestId, "error", "TTS_SPEAK_FAILED");
                        }
                    } catch (Exception e) {
                        notifyHubTtsEvent(requestId, "error", "TTS_EXCEPTION");
                    }
                }
            });
        }

        @JavascriptInterface
        public void stop() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (tts != null) {
                        try {
                            tts.stop();
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }
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
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
        super.onDestroy();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
