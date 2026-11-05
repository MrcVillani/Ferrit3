package it.ferrite.player;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.audiofx.Visualizer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Ponte tra la pagina (window.FerriteAndroid) e i comandi multimediali di Android:
 * legge il player attivo (Spotify per primo), lo comanda e calcola le bande dell'analizzatore.
 */
public class MediaBridge {

    private static final String SPOTIFY = "com.spotify.music";
    private static final int BANDS = 32;

    private final Activity act;
    private final MediaSessionManager sessions;
    private final AudioManager audio;
    private final ComponentName listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile MediaController ctrl;
    private boolean listening = false;
    private long lastRefresh = 0;

    private Visualizer vis;
    private volatile String visError = "";
    private final float[] bands = new float[BANDS];
    private volatile long bandsAt = 0;

    private final MediaSessionManager.OnActiveSessionsChangedListener onSessions = this::pick;

    MediaBridge(Activity activity) {
        act = activity;
        sessions = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
        audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        listener = new ComponentName(activity, MediaListener.class);
    }

    /* ---------------- sessioni ---------------- */

    boolean hasAccess() {
        String flat = Settings.Secure.getString(act.getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        return flat.contains(listener.flattenToString()) || flat.contains(listener.flattenToShortString());
    }

    synchronized void refresh() {
        lastRefresh = SystemClock.uptimeMillis();
        if (!hasAccess()) {
            ctrl = null;
            return;
        }
        try {
            if (!listening) {
                sessions.addOnActiveSessionsChangedListener(onSessions, listener, main);
                listening = true;
            }
            pick(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            ctrl = null;
        }
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null && !list.isEmpty()) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
            }
            if (best == null) {
                for (MediaController c : list) {
                    if (SPOTIFY.equals(c.getPackageName())) { best = c; break; }
                }
            }
            if (best == null) best = list.get(0);
        }
        ctrl = best;
    }

    void release() {
        stopVisualizer();
        if (listening) {
            try { sessions.removeOnActiveSessionsChangedListener(onSessions); } catch (Exception ignored) { }
            listening = false;
        }
    }

    private String appLabel(String pkg) {
        if (SPOTIFY.equals(pkg)) return "Spotify";
        try {
            PackageManager pm = act.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            int dot = pkg.lastIndexOf('.');
            return dot >= 0 ? pkg.substring(dot + 1) : pkg;
        }
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString(); }

    /* ---------------- API per la pagina ---------------- */

    @JavascriptInterface
    public String getState() {
        JSONObject o = new JSONObject();
        try {
            boolean access = hasAccess();
            o.put("access", access);
            if (access && (ctrl == null || !listening) && SystemClock.uptimeMillis() - lastRefresh > 2000) {
                main.post(this::refresh);
            }
            o.put("audioPerm", act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
            o.put("visual", vis != null && SystemClock.uptimeMillis() - bandsAt < 1500);
            o.put("visualError", visError);

            int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int vol = Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);

            MediaController c = ctrl;
            if (c != null) {
                o.put("pkg", c.getPackageName());
                o.put("app", appLabel(c.getPackageName()));
                MediaMetadata m = c.getMetadata();
                if (m != null) {
                    o.put("title", str(m.getString(MediaMetadata.METADATA_KEY_TITLE)));
                    String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    if (artist == null) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                    o.put("artist", str(artist));
                    o.put("album", str(m.getString(MediaMetadata.METADATA_KEY_ALBUM)));
                    o.put("dur", m.getLong(MediaMetadata.METADATA_KEY_DURATION));
                }
                PlaybackState s = c.getPlaybackState();
                if (s != null) {
                    int st = s.getState();
                    boolean playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING;
                    long pos = s.getPosition();
                    if (st == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
                        pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
                    }
                    o.put("playing", playing);
                    o.put("pos", Math.max(0, pos));
                    JSONArray actions = new JSONArray();
                    List<PlaybackState.CustomAction> custom = s.getCustomActions();
                    if (custom != null) {
                        for (PlaybackState.CustomAction a : custom) actions.put(a.getAction() + "|" + str(a.getName()));
                    }
                    o.put("actions", actions);
                }
                MediaController.PlaybackInfo pi = c.getPlaybackInfo();
                if (pi != null && pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE && pi.getMaxVolume() > 0) {
                    vol = Math.round(pi.getCurrentVolume() * 100f / pi.getMaxVolume());
                    o.put("remote", true);
                }
            }
            o.put("vol", vol);
        } catch (Exception e) {
            try { o.put("error", e.toString()); } catch (JSONException ignored) { }
        }
        return o.toString();
    }

    @JavascriptInterface
    public String getQueue() {
        JSONArray arr = new JSONArray();
        MediaController c = ctrl;
        if (c == null) return "[]";
        try {
            List<MediaSession.QueueItem> q = c.getQueue();
            if (q == null) return "[]";
            PlaybackState s = c.getPlaybackState();
            long active = s != null ? s.getActiveQueueItemId() : -1;
            for (MediaSession.QueueItem it : q) {
                MediaDescription d = it.getDescription();
                JSONObject o = new JSONObject();
                o.put("id", it.getQueueId());
                o.put("title", str(d.getTitle()));
                o.put("artist", str(d.getSubtitle()));
                o.put("active", it.getQueueId() == active);
                arr.put(o);
                if (arr.length() >= 200) break;
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    @JavascriptInterface
    public String getBands() {
        if (vis == null || SystemClock.uptimeMillis() - bandsAt > 1000) return "";
        StringBuilder sb = new StringBuilder(BANDS * 5);
        for (int i = 0; i < BANDS; i++) {
            if (i > 0) sb.append(',');
            sb.append(Math.round(bands[i] * 1000) / 1000f);
        }
        return sb.toString();
    }

    @JavascriptInterface
    public void command(final String action, final String arg) {
        main.post(() -> run(action, arg == null ? "" : arg));
    }

    private void run(String action, String arg) {
        MediaController c = ctrl;
        try {
            switch (action) {
                case "notifSettings":
                    act.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    return;
                case "appSettings":
                    act.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + act.getPackageName())));
                    return;
                case "audioPerm":
                    act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MainActivity.REQ_AUDIO);
                    return;
                case "openPlayer": {
                    String pkg = c != null ? c.getPackageName() : SPOTIFY;
                    Intent i = act.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (i == null) i = act.getPackageManager().getLaunchIntentForPackage(SPOTIFY);
                    if (i != null) act.startActivity(i);
                    return;
                }
                case "volume":
                    setVolume(c, Integer.parseInt(arg));
                    return;
                default:
                    break;
            }
            if (c == null) return;
            MediaController.TransportControls t = c.getTransportControls();
            switch (action) {
                case "play": t.play(); break;
                case "pause": t.pause(); break;
                case "next": t.skipToNext(); break;
                case "prev": t.skipToPrevious(); break;
                case "stop": t.pause(); t.seekTo(0); break;
                case "seek": t.seekTo(Long.parseLong(arg)); break;
                case "queue": t.skipToQueueItem(Long.parseLong(arg)); break;
                case "custom": t.sendCustomAction(arg, null); break;
                default: break;
            }
        } catch (Exception ignored) {
        }
    }

    private void setVolume(MediaController c, int percent) {
        percent = Math.max(0, Math.min(100, percent));
        MediaController.PlaybackInfo pi = c != null ? c.getPlaybackInfo() : null;
        if (pi != null && pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE && pi.getMaxVolume() > 0) {
            c.setVolumeTo(Math.round(percent * pi.getMaxVolume() / 100f), 0);
        } else {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(percent * max / 100f), 0);
        }
    }

    /* ---------------- analizzatore ---------------- */

    synchronized void startVisualizer() {
        if (vis != null) return;
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Visualizer v = new Visualizer(0);
            int[] range = Visualizer.getCaptureSizeRange();
            v.setCaptureSize(Math.min(1024, range[1]));
            v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) { }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    onFft(fft, samplingRate);
                }
            }, Math.min(Visualizer.getMaxCaptureRate(), 20000), false, true);
            v.setEnabled(true);
            vis = v;
            visError = "";
        } catch (Throwable e) {
            vis = null;
            visError = e.toString();
        }
    }

    synchronized void stopVisualizer() {
        if (vis == null) return;
        try {
            vis.setEnabled(false);
            vis.release();
        } catch (Throwable ignored) {
        }
        vis = null;
    }

    private void onFft(byte[] fft, int samplingRateMilliHz) {
        int n = fft.length / 2;
        if (n < 2) return;
        float[] mag = new float[n];
        mag[0] = Math.abs(fft[0]);
        for (int k = 1; k < n; k++) {
            float re = fft[2 * k], im = fft[2 * k + 1];
            mag[k] = (float) Math.hypot(re, im);
        }
        double nyquist = samplingRateMilliHz / 2000.0;
        double binHz = nyquist / n;
        double lo = 40, hi = Math.min(16000, nyquist);
        for (int b = 0; b < BANDS; b++) {
            double f0 = lo * Math.pow(hi / lo, b / (double) BANDS);
            double f1 = lo * Math.pow(hi / lo, (b + 1) / (double) BANDS);
            int k0 = Math.max(1, (int) (f0 / binHz));
            int k1 = Math.min(n, Math.max(k0 + 1, (int) Math.ceil(f1 / binHz)));
            float m = 0;
            for (int k = k0; k < k1; k++) m = Math.max(m, mag[k]);
            double db = 20 * Math.log10(m + 1e-3);
            float v = (float) ((db - 6) / 34.0);
            bands[b] = Math.max(0f, Math.min(1f, v));
        }
        bandsAt = SystemClock.uptimeMillis();
    }
}
