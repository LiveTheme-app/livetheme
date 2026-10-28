package io.hmg.wallpaperapp;

import android.content.Intent;
import android.service.notification.NotificationListenerService;

/** Only announces access availability; notification contents are never read. */
public final class SongAccessService extends NotificationListenerService {
    static final String ACCESS_CHANGED="io.hmg.wallpaperapp.SONG_ACCESS_CHANGED";
    @Override public void onListenerConnected(){announce();}
    @Override public void onListenerDisconnected(){announce();}
    private void announce(){sendBroadcast(new Intent(ACCESS_CHANGED).setPackage(getPackageName()));}
}
