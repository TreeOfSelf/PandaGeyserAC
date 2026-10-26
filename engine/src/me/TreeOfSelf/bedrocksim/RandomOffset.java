package me.TreeOfSelf.bedrocksim;

public final class RandomOffset {
    public record Spec(float minX, float maxX, int stepsX, float minY, float maxY, int stepsY, float minZ, float maxZ, int stepsZ) { }

    private long lo, hi;

    private RandomOffset(long seed) {
        long l = seed ^ 0x6a09e667f3bcc909L, h = l + 0x9e3779b97f4a7c15L;
        lo = mix(l);
        hi = mix(h);
        if ((lo | hi) == 0) { lo = 0x9e3779b97f4a7c15L; hi = 0x6a09e667f3bcc909L; }
    }

    private static long mix(long s) {
        s = (s ^ (s >>> 30)) * 0xbf58476d1ce4e5b9L;
        s = (s ^ (s >>> 27)) * 0x94d049bb133111ebL;
        return s ^ (s >>> 31);
    }

    private float nextFloat() {
        long l = lo, h = hi;
        long r = Long.rotateLeft(l + h, 17) + l;
        h ^= l;
        lo = Long.rotateLeft(l, 49) ^ h ^ (h << 21);
        hi = Long.rotateLeft(h, 28);
        return (r >>> 40) * 5.9604645E-8f;
    }

    private static float axis(float f, float min, float max, int steps) {
        if (min >= max) return min;
        if (steps == 1) return (min + max) * 0.5f;
        if (steps == 0) return min + (max - min) * f;
        return min + (float) Math.floor(f * steps) * ((max - min) / (steps - 1));
    }

    public static float[] at(Spec s, int x, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L;
        l = l * l * 42317861L + l * 11L;
        RandomOffset r = new RandomOffset((int) (l >>> 16));
        float fx = r.nextFloat(), fy = r.nextFloat(), fz = r.nextFloat();
        return new float[]{axis(fx, s.minX, s.maxX, s.stepsX), axis(fy, s.minY, s.maxY, s.stepsY), axis(fz, s.minZ, s.maxZ, s.stepsZ)};
    }
}
