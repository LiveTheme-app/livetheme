package io.hmg.wallpaperapp;

import android.content.Context;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.json.JSONObject;

/**
 * Offline background renderer: the WebWallGL scene, or a creator's web theme in a sandbox.
 * No addJavascriptInterface, no network (only the app's own virtual host is served), no file/content access.
 * A web theme talks to the app only through one MessagePort, which the injected runtime keeps away from theme code.
 */
final class SceneBackground extends WebView {
    interface Replaced {
        void with(SceneBackground next);
    }

    /**
     * A theme request ({action,value}); the owner decides and replies {ok,reason}.
     */
    interface Requests {
        void request(JSONObject message, java.util.function.Consumer<JSONObject> reply);
    }

    /**
     * Renderer deaths are counted across instances: a second death within this window shows the still preview instead of retrying.
     */
    private static long lastGone;
    private static final long RETRY_WINDOW_MS = 120_000;
    static final String HOST = "https://appassets.androidplatform.net";
    /**
     * Files a web theme may contain; anything else is neither imported nor served.
     */
    static final List<String> WEB_TYPES = Arrays.asList("html", "js", "css", "json", "png", "jpg", "jpeg", "webp",
            "gif", "svg", "mp4", "woff2");
    private static final String CSP = "default-src 'self' blob: data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; media-src 'self' blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
    private File folder;
    private boolean requestedPlaying = true, restricted, still, web;
    private int fps = 30;
    private Replaced replaced = next -> {
    };
    private Requests requests = (message, reply) -> reply.accept(new JSONObject());
    private WebMessagePort port;
    private JSONObject lastData;
    private final PowerManager.OnThermalStatusChangedListener thermal = status -> post(this::applyFps);

    SceneBackground(Context c) {
        super(c);
        setBackgroundColor(0xff13242c);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setAllowFileAccess(false);
        getSettings().setAllowContentAccess(false);
        getSettings().setMediaPlaybackRequiresUserGesture(true);
        getSettings().setGeolocationEnabled(false);
        getSettings().setSupportMultipleWindows(false);
        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("LiveThemeScene", m.message());
                if (m.message().equals("SCENE_READY"))
                    post(() -> {
                        applyFps();
                        playback(requestedPlaying);
                    });
                return true;
            }
        });
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                replace(detail.didCrash());
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (web && !still && url != null && url.startsWith(HOST + "/theme/"))
                    openChannel();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String path = r.getUrl().getPath();
                if (!"https".equals(r.getUrl().getScheme()) ||
                        !"appassets.androidplatform.net".equals(r.getUrl().getHost()) || path == null ||
                        path.contains("..") || path.indexOf('\0') >= 0)
                    return missing();
                try {
                    InputStream data;
                    Map<String, String> headers = Collections.emptyMap();
                    if (path.startsWith("/local/wallpaper/")) {
                        File base = folder;
                        if (base == null)
                            return missing();
                        File file = new File(base, path.substring(17));
                        if (!file.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator))
                            return missing();
                        data = new FileInputStream(file);
                    } else if (path.equals("/local/index.html") || path.equals("/local/webwallgl.global.min.js"))
                        data = getContext().getAssets().open("engine/" + path.substring(7));
                    else if (path.equals("/local/still.html"))
                        data = new ByteArrayInputStream(STILL.getBytes(StandardCharsets.UTF_8));
                    else if (web && path.startsWith("/theme/")) {
                        File base = folder;
                        if (base == null)
                            return missing();
                        String name = path.substring(7);
                        File file = new File(base, name);
                        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                        if (!WEB_TYPES.contains(ext) || !file.isFile() ||
                                !file.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator))
                            return missing();
                        headers = Collections.singletonMap("Content-Security-Policy", CSP);
                        data = name.equals("index.html") ? withRuntime(file) : new FileInputStream(file);
                    } else
                        return missing();
                    return new WebResourceResponse(mime(path), "UTF-8", 200, "OK", headers, data);
                } catch (IOException e) {
                    return missing();
                }
            }
        });
    }

    private static String mime(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".js") ? "text/javascript" : p.endsWith(".html") ? "text/html" :
                p.endsWith(".css") ? "text/css" : p.endsWith(".json") ? "application/json" :
                        p.endsWith(".png") ? "image/png" : p.endsWith(".jpg") || p.endsWith(".jpeg") ? "image/jpeg" :
                                p.endsWith(".webp") ? "image/webp" : p.endsWith(".gif") ? "image/gif" :
                                        p.endsWith(".svg") ? "image/svg+xml" : p.endsWith(".mp4") ? "video/mp4" :
                                                p.endsWith(".woff2") ? "font/woff2" : "application/octet-stream";
    }

    /**
     * The runtime runs before any theme code, so the frame budget and the port stay under the app's control.
     */
    private InputStream withRuntime(File index) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("<script>".getBytes(StandardCharsets.UTF_8));
        try (InputStream in = getContext().getAssets().open("engine/livetheme-runtime.js")) {
            CreatorTheme.copy(in, out, 256 * 1024);
        }
        out.write("</script>\n".getBytes(StandardCharsets.UTF_8));
        try (InputStream in = new FileInputStream(index)) {
            CreatorTheme.copy(in, out, 4L * 1024 * 1024);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * The theme's own preview picture, or the plain backdrop colour when it has none; no script, no animation.
     */
    private static final String STILL = "<!doctype html><meta charset=utf-8><style>html,body{margin:0;width:100%;height:100%;background:#13242c}img{width:100%;height:100%;object-fit:cover}</style><img src='wallpaper/preview.jpg' onerror='this.remove()'>";

    private WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", Collections.emptyMap(),
                new ByteArrayInputStream(new byte[0]));
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    void setOnReplaced(Replaced listener) {
        replaced = listener;
    }

    void setRequests(Requests handler) {
        requests = handler;
    }

    boolean isWeb() {
        return web && !still;
    }

    void load(File f) {
        closeChannel();
        folder = f;
        web = false;
        still = false;
        loadUrl(HOST + "/local/index.html");
    }

    /**
     * A creator web theme from creator-themes/<id>/web.
     */
    void loadWeb(File dir) {
        closeChannel();
        folder = dir;
        web = true;
        still = false;
        loadUrl(HOST + "/theme/index.html");
    }

    private void openChannel() {
        closeChannel();
        WebMessagePort[] pair = createWebMessageChannel();
        port = pair[0];
        port.setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
            @Override
            public void onMessage(WebMessagePort source, WebMessage message) {
                JSONObject msg;
                try {
                    msg = new JSONObject(message.getData());
                } catch (Exception e) {
                    return;
                }
                if (!"request".equals(msg.optString("type")))
                    return;
                int id = msg.optInt("id");
                post(() -> requests.request(msg, reply -> {
                    try {
                        send(reply.put("type", "response").put("id", id));
                    } catch (org.json.JSONException ignored) {
                    }
                }));
            }
        });
        postWebMessage(new WebMessage("livetheme:init", new WebMessagePort[]{pair[1]}), Uri.parse(HOST));
        applyFps();
        sendPaused();
        if (lastData != null)
            sendData(lastData);
    }

    private void closeChannel() {
        if (port != null) {
            try {
                port.close();
            } catch (RuntimeException ignored) {
            }
            port = null;
        }
    }

    private void send(JSONObject message) {
        if (port != null)
            try {
                port.postMessage(new WebMessage(message.toString()));
            } catch (RuntimeException e) {
                Log.w("LiveThemeScene", "web theme message failed: " + e);
            }
    }

    /**
     * Read-only car/app values for a web theme.
     */
    void sendData(JSONObject data) {
        lastData = data;
        try {
            send(new JSONObject().put("type", "data").put("data", data));
        } catch (org.json.JSONException ignored) {
        }
    }

    void sendTouch(float x, float y) {
        try {
            send(new JSONObject().put("type", "touch").put("x", x).put("y", y));
        } catch (org.json.JSONException ignored) {
        }
    }

    /**
     * Themes that replace widgets (clock, music…) keep updating their numbers while driving: motion stops, the runtime drops to 1 fps. Other themes freeze as before.
     */
    boolean infoWhileDriving;

    private void sendPaused() {
        try {
            send(new JSONObject().put("type", "paused").put("value", !(requestedPlaying && !restricted))
                    .put("reason", restricted ? "driving" : requestedPlaying ? JSONObject.NULL : "user")
                    .put("info", restricted && infoWhileDriving));
        } catch (org.json.JSONException ignored) {
        }
    }

    void setInfoWhileDriving(boolean value) {
        if (infoWhileDriving == value)
            return;
        infoWhileDriving = value;
        if (web)
            sendPaused();
    }

    void playback(boolean active) {
        requestedPlaying = active;
        if (web) {
            sendPaused();
            return;
        }
        evaluateJavascript("window.engine && window.engine." + (active && !restricted ? "resume()" : "pause()"), null);
    }

    /**
     * While the car requires distraction optimisation the scene holds its last frame.
     */
    void setRestricted(boolean value) {
        if (restricted == value)
            return;
        restricted = value;
        playback(requestedPlaying);
    }

    /**
     * The app owns the frame budget, whatever a theme asks for: 30 fps, 24 in the narrow split, 15 under power saving or thermal pressure.
     */
    static int fpsCap(boolean narrow, boolean powerSave, int thermalStatus) {
        return powerSave || thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ? 15 : narrow ? 24 : 30;
    }

    void applyFps() {
        PowerManager power = getContext().getSystemService(PowerManager.class);
        int max = getContext().getSystemService(android.view.WindowManager.class).getMaximumWindowMetrics().getBounds()
                .width();
        int next = fpsCap(getWidth() > 0 && getWidth() < max * .5f, power != null && power.isPowerSaveMode(),
                power == null ? PowerManager.THERMAL_STATUS_NONE : power.getCurrentThermalStatus());
        if (next != fps)
            Log.i("LiveThemeScene", "FPS_CAP " + next);
        fps = next;
        if (web) {
            try {
                send(new JSONObject().put("type", "fps").put("cap", fps));
            } catch (org.json.JSONException ignored) {
            }
        } else
            evaluateJavascript("window.engine && window.engine.setFps(" + fps + ")", null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyFps();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PowerManager power = getContext().getSystemService(PowerManager.class);
        if (power != null)
            power.addThermalStatusListener(getContext().getMainExecutor(), thermal);
    }

    @Override
    protected void onDetachedFromWindow() {
        PowerManager power = getContext().getSystemService(PowerManager.class);
        if (power != null)
            power.removeThermalStatusListener(thermal);
        super.onDetachedFromWindow();
    }

    @Override
    public void destroy() {
        closeChannel();
        super.destroy();
    }

    /**
     * A dead renderer cannot be reused: swap in a fresh view at the same place so the app keeps running.
     */
    private void replace(boolean crashed) {
        android.view.ViewGroup parent = (android.view.ViewGroup) getParent();
        long now = SystemClock.elapsedRealtime();
        boolean retry = !still && now - lastGone > RETRY_WINDOW_MS;
        lastGone = now;
        Log.w("LiveThemeScene", "RENDERER_GONE crashed=" + crashed + " next=" + (retry ? "reload" : "still"));
        SceneBackground next = new SceneBackground(getContext());
        next.replaced = replaced;
        next.requests = requests;
        next.lastData = lastData;
        next.requestedPlaying = requestedPlaying;
        next.restricted = restricted;
        next.infoWhileDriving = infoWhileDriving;
        next.setVisibility(getVisibility());
        if (parent != null) {
            int index = parent.indexOfChild(this);
            android.view.ViewGroup.LayoutParams bounds = getLayoutParams();
            parent.removeView(this);
            parent.addView(next, index, bounds);
        }
        if (folder != null) {
            if (retry) {
                if (web)
                    next.loadWeb(folder);
                else
                    next.load(folder);
            } else {
                next.folder = folder;
                next.web = web;
                next.still = true;
                next.loadUrl(HOST + "/local/still.html");
            }
        }
        replaced.with(next);
        destroy();
    }

    /**
     * Debug builds only: lets the renderer-loss path be exercised on the emulator.
     */
    void debugCrashRenderer() {
        if (BuildConfig.DEBUG)
            loadUrl("chrome://crash");
    }
}
