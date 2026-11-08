package io.hmg.wallpaperapp;

import java.util.*;

/**
 * Small deterministic layout math, independent of Android so resize cases can be checked.
 */
final class WidgetPlacement {
    static float[][] pack(float width, float height, float gap, float[][] sizes) {
        if (width <= 0 || height <= 0)
            return null;
        for (int columns = Math.min(3, sizes.length); columns >= 1; columns--) {
            int rows = (sizes.length + columns - 1) / columns;
            float[] widths = new float[columns], heights = new float[rows];
            for (int i = 0; i < sizes.length; i++) {
                widths[i % columns] = Math.max(widths[i % columns], sizes[i][0]);
                heights[i / columns] = Math.max(heights[i / columns], sizes[i][1]);
            }
            float usedW = gap * (columns - 1), usedH = gap * (rows - 1);
            for (float v : widths)
                usedW += v;
            for (float v : heights)
                usedH += v;
            if (usedW > width + .01f || usedH > height + .01f)
                continue;
            float[][] out = new float[sizes.length][4];
            float y = 0;
            for (int row = 0; row < rows; row++) {
                float x = (width - usedW) / 2;
                for (int col = 0; col < columns; col++) {
                    int i = row * columns + col;
                    if (i < sizes.length)
                        out[i] = new float[]{x, y, sizes[i][0], sizes[i][1]};
                    x += widths[col] + gap;
                }
                y += heights[row] + gap;
            }
            return out;
        }
        if (sizes.length == 0)
            return new float[0][4];
        Integer[] order = new Integer[sizes.length];
        for (int i = 0; i < order.length; i++)
            order[i] = i;
        Arrays.sort(order, (a, b) -> Float.compare(sizes[b][0] * sizes[b][1], sizes[a][0] * sizes[a][1]));
        float[][] out = new float[sizes.length][4];
        // shortcut: bounded search keeps the seven-widget UI responsive; expand off-thread if widget counts grow.
        return place(width, height, gap, sizes, order, 0, out, new int[]{20000}) ? out : null;
    }

    private static boolean place(float width, float height, float gap, float[][] sizes, Integer[] order, int depth,
                                 float[][] out, int[] budget) {
        if (depth == order.length)
            return true;
        int index = order[depth];
        float w = sizes[index][0], h = sizes[index][1];
        if (w > width || h > height)
            return false;
        TreeSet<Float> xs = new TreeSet<>(), ys = new TreeSet<>();
        xs.add(0f);
        ys.add(0f);
        for (int i = 0; i < depth; i++) {
            float[] r = out[order[i]];
            xs.add(r[0] + r[2] + gap);
            ys.add(r[1] + r[3] + gap);
        }
        for (float y : ys)
            for (float x : xs) {
                if (--budget[0] < 0)
                    return false;
                if (x + w > width + .01f || y + h > height + .01f)
                    continue;
                boolean clear = true;
                for (int i = 0; i < depth; i++)
                    if (overlaps(x, y, w, h, out[order[i]], gap)) {
                        clear = false;
                        break;
                    }
                if (!clear)
                    continue;
                out[index] = new float[]{x, y, w, h};
                if (place(width, height, gap, sizes, order, depth + 1, out, budget))
                    return true;
            }
        return false;
    }

    static float[][] grid(float width, float height, float gap, float[][] sizes) {
        if (width <= 0 || height <= 0)
            return null;
        int columns = width >= 1500 ? 3 : width >= 950 ? 2 : 1;
        for (int attempt = 0; attempt <= 4; attempt++) {
            float factor = 1 - attempt * .1f, cell = (width - gap * (columns - 1)) / columns, y = 0, rowHeight = 0;
            float[][] out = new float[sizes.length][4];
            for (int i = 0; i < sizes.length; i++) {
                int column = i % columns;
                if (column == 0 && i > 0) {
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                float w = Math.max(sizes[i][2], Math.min(cell, sizes[i][0] * factor));
                if (w > cell)
                    return null;
                float h = Math.max(sizes[i][3], sizes[i][1] * w / sizes[i][0]);
                out[i] = new float[]{column * (cell + gap) + (cell - w) / 2, y, w, h};
                rowHeight = Math.max(rowHeight, h);
            }
            if (y + rowHeight <= height)
                return out;
        }
        return null;
    }

    static boolean overlaps(float x, float y, float w, float h, float[] rect, float gap) {
        return x < rect[0] + rect[2] + gap && x + w + gap > rect[0] && y < rect[1] + rect[3] + gap &&
                y + h + gap > rect[1];
    }

    static float[] vacant(float width, float height, float w, float h, float x, float y, List<float[]> occupied,
                          float gap) {
        if (w > width || h > height)
            return null;
        ArrayList<Float> xs = new ArrayList<>(), ys = new ArrayList<>();
        xs.add(Math.max(0, Math.min(width - w, x)));
        xs.add(0f);
        xs.add(width - w);
        ys.add(Math.max(0, Math.min(height - h, y)));
        ys.add(0f);
        ys.add(height - h);
        for (float[] r : occupied) {
            xs.add(r[0] + r[2] + gap);
            xs.add(r[0] - w - gap);
            ys.add(r[1] + r[3] + gap);
            ys.add(r[1] - h - gap);
        }
        float[] best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (float a : xs)
            for (float b : ys) {
                if (a < 0 || b < 0 || a + w > width + .01f || b + h > height + .01f)
                    continue;
                boolean clear = true;
                for (float[] r : occupied)
                    if (overlaps(a, b, w, h, r, gap)) {
                        clear = false;
                        break;
                    }
                if (clear) {
                    double d = (a - x) * (a - x) + (b - y) * (b - y);
                    if (d < distance) {
                        distance = d;
                        best = new float[]{a, b};
                    }
                }
            }
        return best;
    }
}
