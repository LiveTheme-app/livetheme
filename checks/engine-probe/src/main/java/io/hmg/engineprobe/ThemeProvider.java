package io.hmg.engineprobe;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.*;

public final class ThemeProvider extends ContentProvider {
    public boolean onCreate() {
        return true;
    }

    public String getType(Uri u) {
        return "application/zip";
    }

    public Cursor query(Uri u, String[] p, String s, String[] a, String sort) {
        return null;
    }

    public Uri insert(Uri u, ContentValues v) {
        throw new UnsupportedOperationException();
    }

    public int update(Uri u, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException();
    }

    public int delete(Uri u, String s, String[] a) {
        throw new UnsupportedOperationException();
    }

    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        if (!"/theme.zip".equals(u.getPath()) || !"r".equals(mode))
            throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), "theme.zip"),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
