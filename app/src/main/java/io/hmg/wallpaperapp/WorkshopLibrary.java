package io.hmg.wallpaperapp;

import android.content.Context;

import java.io.*;

/**
 * References Steam-owned local copies; no Workshop artwork is bundled in the APK or shared ZIP.
 */
final class WorkshopLibrary {
    static final String[] IDS = {"2347484937", "1929493662", "3519888361", "3028059305", "3666041758"};

    static File folder(Context c, String id) {
        if (!id.matches("[0-9]{6,14}"))
            throw new IllegalArgumentException("Workshop id");
        return new File(c.getFilesDir(), "creator-themes/workshop-" + id);
    }

    static File scene(Context c, String id) {
        File dir = folder(c, id);
        return new File(dir, "scene.json").isFile() && new File(dir, "scene.pkg").isFile() ? dir : null;
    }

    static File video(Context c, String id) {
        File video = new File(folder(c, id), "background.mp4");
        return video.isFile() ? video : null;
    }

    static void require(Context c, String id) throws IOException {
        if (scene(c, id) == null && video(c, id) == null)
            throw new IOException(
                    "이 테마의 창작마당 원본이 아직 없어요. PC의 Wallpaper Engine에서 작품 " + id + "을 내려받아 연결해주세요. 위젯 테마는 내 테마에 저장되어 있어요.");
    }

    static String url(String id) {
        if (!id.matches("[0-9]{6,14}"))
            throw new IllegalArgumentException("Workshop id");
        return "https://steamcommunity.com/sharedfiles/filedetails/?id=" + id;
    }
}
