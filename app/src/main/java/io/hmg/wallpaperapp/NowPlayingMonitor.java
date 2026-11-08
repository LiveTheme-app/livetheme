package io.hmg.wallpaperapp;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Observes music metadata while visible; does not capture audio or read notifications.
 */
final class NowPlayingMonitor {
    interface Listener {
        void update(boolean allowed, String title, String artist, Bitmap art, boolean playing, boolean canPrevious,
                    boolean canNext);
    }

    private final Context context;
    private final Listener listener;
    private final MediaSessionManager manager;
    private final ComponentName access;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private boolean watching;
    private MediaController target;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::bindSessions;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publish();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publish();
        }

        @Override
        public void onSessionDestroyed() {
            reload();
        }
    };

    NowPlayingMonitor(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        manager = context.getSystemService(MediaSessionManager.class);
        access = new ComponentName(context, SongAccessService.class);
    }

    boolean openPlayer() {
        if (target == null)
            return false;
        android.app.PendingIntent action = target.getSessionActivity();
        if (action == null)
            return false;
        try {
            action.send();
            return true;
        } catch (android.app.PendingIntent.CanceledException | SecurityException error) {
            return false;
        }
    }

    boolean allowed() {
        return context.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(access);
    }

    void start() {
        if (watching)
            return;
        if (!allowed()) {
            listener.update(false, "", "", null, false, false, false);
            return;
        }
        try {
            watching = true;
            manager.addOnActiveSessionsChangedListener(sessionsListener, access, handler);
            reload();
        } catch (SecurityException error) {
            stop();
            listener.update(false, "", "", null, false, false, false);
        }
    }

    void stop() {
        if (watching)
            manager.removeOnActiveSessionsChangedListener(sessionsListener);
        watching = false;
        clearControllers();
    }

    private void clearControllers() {
        target = null;
        for (MediaController c : controllers)
            c.unregisterCallback(callback);
        controllers.clear();
    }

    private void reload() {
        if (!watching)
            return;
        try {
            bindSessions(manager.getActiveSessions(access));
        } catch (SecurityException error) {
            stop();
            listener.update(false, "", "", null, false, false, false);
        }
    }

    private void bindSessions(List<MediaController> sessions) {
        if (!watching)
            return;
        clearControllers();
        if (sessions != null)
            for (MediaController c : sessions) {
                if (c.getPackageName().equals(context.getPackageName()))
                    continue;
                controllers.add(c);
                c.registerCallback(callback, handler);
            }
        publish();
    }

    private int rank(MediaController c) {
        PlaybackState state = c.getPlaybackState();
        if (state == null)
            return 0;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
                return 3;
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
                return 2;
            case PlaybackState.STATE_PAUSED:
                return 1;
            default:
                return 0;
        }
    }

    private void publish() {
        if (!watching)
            return;
        if (!allowed()) {
            stop();
            listener.update(false, "", "", null, false, false, false);
            return;
        }
        MediaController selected = null;
        int highest = 0;
        for (MediaController c : controllers) {
            int score = rank(c);
            if (score > highest) {
                selected = c;
                highest = score;
            }
        }
        target = selected;
        if (selected == null) {
            listener.update(true, "", "", null, false, false, false);
            return;
        }
        MediaMetadata metadata = selected.getMetadata();
        String title = "알 수 없는 곡", artist = "";
        Bitmap art = null;
        if (metadata != null) {
            title = text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title.isEmpty())
                title = text(metadata, MediaMetadata.METADATA_KEY_TITLE);
            if (title.isEmpty())
                title = "알 수 없는 곡";
            artist = text(metadata, MediaMetadata.METADATA_KEY_ARTIST);
            if (artist.isEmpty())
                artist = text(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null)
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null)
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (art != null && Math.max(art.getWidth(), art.getHeight()) > 512) {
                float ratio = 512f / Math.max(art.getWidth(), art.getHeight());
                art = Bitmap.createScaledBitmap(art, Math.max(1, Math.round(art.getWidth() * ratio)),
                        Math.max(1, Math.round(art.getHeight() * ratio)), true);
            }
        }
        listener.update(true, title, artist, art, highest == 3, canSkip(false), canSkip(true));
    }

    private boolean canSkip(boolean next) {
        if (!watching || target == null || !allowed())
            return false;
        PlaybackState state = target.getPlaybackState();
        long action = next ? PlaybackState.ACTION_SKIP_TO_NEXT : PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        return state != null && (state.getActions() & action) != 0;
    }

    boolean canToggle() {
        if (!watching || target == null || !allowed())
            return false;
        PlaybackState state = target.getPlaybackState();
        return state != null && (state.getActions() &
                (PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE)) != 0;
    }

    /**
     * Play or pause the watched session; false when the music app does not allow it.
     */
    boolean toggle() {
        try {
            if (!canToggle())
                return false;
            PlaybackState state = target.getPlaybackState();
            if (state.getState() == PlaybackState.STATE_PLAYING)
                target.getTransportControls().pause();
            else
                target.getTransportControls().play();
            return true;
        } catch (SecurityException | IllegalStateException error) {
            reload();
            return false;
        }
    }

    boolean skip(boolean next) {
        try {
            if (!canSkip(next))
                return false;
            if (next)
                target.getTransportControls().skipToNext();
            else
                target.getTransportControls().skipToPrevious();
            return true;
        } catch (SecurityException | IllegalStateException error) {
            reload();
            return false;
        }
    }

    private String text(MediaMetadata metadata, String key) {
        CharSequence value = metadata.getText(key);
        if (value == null)
            return "";
        String result = value.toString().trim();
        return result.substring(0, Math.min(256, result.length()));
    }
}
