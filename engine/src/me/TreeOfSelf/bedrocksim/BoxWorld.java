package me.TreeOfSelf.bedrocksim;

import java.util.ArrayList;
import java.util.List;

public final class BoxWorld implements BlockWorld {
    private record Region(int x1, int y1, int z1, int x2, int y2, int z2, BlockType type) {
    }

    private final List<Region> regions = new ArrayList<>();

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockType type) {
        regions.add(new Region(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), type));
    }

    public void clear() {
        regions.clear();
    }

    @Override
    public BlockType type(int x, int y, int z) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (x >= r.x1 && x <= r.x2 && y >= r.y1 && y <= r.y2 && z >= r.z1 && z <= r.z2) return r.type;
        }
        return BlockType.AIR;
    }
}
