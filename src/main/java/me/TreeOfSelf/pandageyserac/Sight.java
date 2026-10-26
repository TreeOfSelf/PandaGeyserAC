package me.TreeOfSelf.pandageyserac;

final class Sight {
    interface Blocks {
        boolean full(int x, int y, int z);
    }

    enum Result { CLEAR, BLOCKED, NO_HIT }

    private static final double SHRINK = 0.01;
    private static final int ARC_STEPS = 16;

    static Result look(float[][] eyes, float yaw0, float pitch0, float yaw1, float pitch1, float[] b, Blocks blocks, int[]... skip) {
        float dyaw = ((yaw1 - yaw0) % 360 + 540) % 360 - 180;
        boolean hit = false;
        for (int i = 0; i <= ARC_STEPS; i++) {
            float f = i / (float) ARC_STEPS;
            double yr = Math.toRadians(yaw0 + dyaw * f), pr = Math.toRadians(pitch0 + (pitch1 - pitch0) * f);
            double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
            for (float[] e : eyes) {
                double t = enter(e[0], e[1], e[2], dx, dy, dz, b[0], b[1], b[2], b[3], b[4], b[5]);
                if (t < 0) continue;
                hit = true;
                if (!blocked(e[0], e[1], e[2], dx, dy, dz, t, blocks, skip)) return Result.CLEAR;
            }
        }
        return hit ? Result.BLOCKED : Result.NO_HIT;
    }

    static double enter(double ox, double oy, double oz, double dx, double dy, double dz, double x0, double y0, double z0, double x1, double y1, double z1) {
        double tmin = 0, tmax = 8;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-12) {
                if (o[a] < lo[a] || o[a] > hi[a]) return -1;
                continue;
            }
            double t1 = (lo[a] - o[a]) / d[a], t2 = (hi[a] - o[a]) / d[a];
            if (t1 > t2) { double x = t1; t1 = t2; t2 = x; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        return tmin;
    }

    private static boolean blocked(double ox, double oy, double oz, double dx, double dy, double dz, double t, Blocks blocks, int[][] skip) {
        // 3D DDA
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tx = Math.abs(dx) < 1e-12 ? Double.MAX_VALUE : ((dx > 0 ? x + 1 : x) - ox) / dx;
        double ty = Math.abs(dy) < 1e-12 ? Double.MAX_VALUE : ((dy > 0 ? y + 1 : y) - oy) / dy;
        double tz = Math.abs(dz) < 1e-12 ? Double.MAX_VALUE : ((dz > 0 ? z + 1 : z) - oz) / dz;
        double ddx = Math.abs(1 / dx), ddy = Math.abs(1 / dy), ddz = Math.abs(1 / dz);
        for (int n = 0; n < 32; n++) {
            if (!skipped(x, y, z, skip) && blocks.full(x, y, z)) {
                double e = enter(ox, oy, oz, dx, dy, dz, x + SHRINK, y + SHRINK, z + SHRINK, x + 1 - SHRINK, y + 1 - SHRINK, z + 1 - SHRINK);
                if (e >= 0 && e < t) return true;
            }
            double next = Math.min(tx, Math.min(ty, tz));
            if (next > t) return false;
            if (tx == next) { x += sx; tx += ddx; }
            else if (ty == next) { y += sy; ty += ddy; }
            else { z += sz; tz += ddz; }
        }
        return false;
    }

    private static boolean skipped(int x, int y, int z, int[][] skip) {
        for (int[] c : skip) if (c != null && c[0] == x && c[1] == y && c[2] == z) return true;
        return false;
    }
}
