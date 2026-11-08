package io.hmg.engineprobe;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.util.Log;

import java.io.*;
import java.util.zip.*;

import org.json.JSONObject;

/**
 * Offline recipient to exercise the production provider across an Android UID boundary.
 */
public final class ReceiveThemeActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Uri uri = getIntent().getParcelableExtra(android.content.Intent.EXTRA_STREAM);
            if (uri == null)
                throw new IOException("Missing theme URI");
            int files = 0;
            boolean video = false, layout = false;
            long total = 0;
            try (InputStream in = getContentResolver().openInputStream(uri); ZipInputStream zip = new ZipInputStream(
                    in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    files++;
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    byte[] buffer = new byte[32768];
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        total += n;
                        if (total > 80L * 1024 * 1024)
                            throw new IOException("Too large");
                        if ("live-theme.json".equals(entry.getName())) {
                            if (data.size() + n > 65536)
                                throw new IOException("Manifest too large");
                            data.write(buffer, 0, n);
                        }
                    }
                    if ("background.mp4".equals(entry.getName()))
                        video = true;
                    if ("live-theme.json".equals(entry.getName())) {
                        JSONObject theme = new JSONObject(data.toString("UTF-8"));
                        layout = theme.getJSONObject("layouts").length() > 0;
                    }
                    zip.closeEntry();
                }
            }
            if (files < 2 || !video || !layout)
                throw new IOException("Missing video or layouts");
            Log.i("CreatorRecipient", "PASS uriGrant=true video=true layouts=true bytes=" + total);
        } catch (Exception e) {
            Log.e("CreatorRecipient", "FAIL", e);
        } finally {
            finish();
        }
    }
}
