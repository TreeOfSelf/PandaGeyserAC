package me.TreeOfSelf.bedrocksim;

import java.util.ArrayList;
import java.util.List;

public interface BlockWorld {
    BlockType type(int x, int y, int z);

    default List<Box> collisionBoxes(int x, int y, int z) {
        List<Box> local = type(x, y, z).shapes();
        if (local.isEmpty()) return List.of();
        List<Box> out = new ArrayList<>(local.size());
        RandomOffset.Spec spec = BlockType.OFFSETS.get(type(x, y, z).name());
        float ox = 0, oy = 0, oz = 0;
        if (spec != null) {
            float[] o = RandomOffset.at(spec, x, z);
            ox = o[0]; oy = o[1]; oz = o[2];
        }
        for (Box b : local) out.add(spec == null ? b.offset(x, y, z) : b.offset(ox, oy, oz).offset(x, y, z));
        return out;
    }
}
