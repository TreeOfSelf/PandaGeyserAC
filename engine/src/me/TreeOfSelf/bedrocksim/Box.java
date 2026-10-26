package me.TreeOfSelf.bedrocksim;

public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    public boolean contains(float x, float y, float z) {
        return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
    }

    public Box offset(float x, float y, float z) {
        return new Box(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    public Box expandTowards(float x, float y, float z) {
        return new Box(x < 0 ? minX + x : minX, y < 0 ? minY + y : minY, z < 0 ? minZ + z : minZ,
                x > 0 ? maxX + x : maxX, y > 0 ? maxY + y : maxY, z > 0 ? maxZ + z : maxZ);
    }

    public float clipY(Box other, float d) {
        if (other.maxX <= minX || other.minX >= maxX || other.maxZ <= minZ || other.minZ >= maxZ) return d;
        if (d > 0 && other.minY >= maxY) return Math.min(d, other.minY - maxY);
        if (d < 0 && other.maxY <= minY) return Math.max(d, other.maxY - minY);
        return d;
    }

    public float clipX(Box other, float d) {
        if (other.maxY <= minY || other.minY >= maxY || other.maxZ <= minZ || other.minZ >= maxZ) return d;
        if (d > 0 && other.minX >= maxX) return Math.min(d, other.minX - maxX);
        if (d < 0 && other.maxX <= minX) return Math.max(d, other.maxX - minX);
        return d;
    }

    public float clipZ(Box other, float d) {
        if (other.maxX <= minX || other.minX >= maxX || other.maxY <= minY || other.minY >= maxY) return d;
        if (d > 0 && other.minZ >= maxZ) return Math.min(d, other.minZ - maxZ);
        if (d < 0 && other.maxZ <= minZ) return Math.max(d, other.maxZ - minZ);
        return d;
    }
}
