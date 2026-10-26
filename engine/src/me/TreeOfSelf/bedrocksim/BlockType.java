package me.TreeOfSelf.bedrocksim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record BlockType(String name, List<Box> shapes, float friction, float speedFactor, float jumpFactor, boolean climbable, float bounciness, String fluid, int level) {
    public BlockType(String name, List<Box> shapes, float friction, float speedFactor, float jumpFactor, boolean climbable, float bounciness) {
        this(name, shapes, friction, speedFactor, jumpFactor, climbable, bounciness, null, 0);
    }

    public static final BlockType AIR = new BlockType("air", List.of(), 0.6f, 1f, 1f, false, 0);
    static final Box FULL = new Box(0, 0, 0, 1, 1, 1);
    static final Map<String, BlockType> TYPES = new HashMap<>();
    public static final Map<String, RandomOffset.Spec> OFFSETS = new HashMap<>();

    static void add(BlockType t) {
        TYPES.put(t.name, t);
    }

    static BlockType solid(String name, Box... shapes) {
        return new BlockType(name, List.of(shapes), 0.6f, 1f, 1f, false, 0);
    }

    static {
        add(AIR);
        add(solid("stone", FULL));
        add(solid("dirt", FULL));
        add(solid("slab", new Box(0, 0, 0, 1, 0.5f, 1)));
        add(solid("top_slab", new Box(0, 0.5f, 0, 1, 1, 1)));
        add(solid("chest", new Box(0.025f, 0, 0.025f, 0.975f, 0.95f, 0.975f)));
        add(solid("stonecutter", new Box(0, 0, 0, 1, 0.5625f, 1)));
        add(solid("enchanting_table", new Box(0, 0, 0, 1, 0.75f, 1)));
        add(solid("trapdoor", new Box(0, 0, 0, 1, 0.1825f, 1)));
        add(solid("daylight_detector", new Box(0, 0, 0, 1, 0.375f, 1)));
        add(solid("top_trapdoor", new Box(0, 0.8175f, 0, 1, 1, 1)));
        add(solid("campfire", new Box(0, 0, 0, 1, 0.4375f, 1)));
        add(solid("grindstone", new Box(0.125f, 0, 0.125f, 0.875f, 1, 0.875f)));
        add(solid("grindstone_z", new Box(0.125f, 0.125f, 0, 0.875f, 0.875f, 1)));
        add(solid("grindstone_x", new Box(0, 0.125f, 0.125f, 1, 0.875f, 0.875f)));
        add(solid("head", new Box(0.25f, 0, 0.25f, 0.75f, 0.5f, 0.75f)));
        add(solid("cake", new Box(0.0625f, 0, 0.0625f, 0.9375f, 0.5f, 0.9375f)));
        add(solid("chorus_plant", new Box(0.125f, 0.125f, 0.125f, 0.875f, 0.875f, 0.875f)));
        add(solid("brewing_stand", new Box(0, 0, 0, 1, 0.125f, 1), new Box(0.4375f, 0, 0.4375f, 0.5625f, 0.875f, 0.5625f)));
        add(solid("fence_gate_x", new Box(0, 0, 0.375f, 1, 1.5f, 0.625f)));
        add(solid("fence_post", new Box(0.375f, 0, 0.375f, 0.625f, 1.5f, 0.625f)));
        add(solid("wall_post", new Box(0.25f, 0, 0.25f, 0.75f, 1.5f, 0.75f)));
        add(solid("door_west", new Box(0, 0, 0, 0.1825f, 1, 1)));
        add(new BlockType("bed", List.of(new Box(0, 0, 0, 1, 0.5625f, 1)), 0.6f, 1f, 1f, false, 0.75f));
        // ladders are named by the side they face
        add(new BlockType("ladder_west", List.of(new Box(0.8125f, 0, 0, 1, 1, 1)), 0.6f, 1f, 1f, true, 0));
        add(new BlockType("ladder_east", List.of(new Box(0, 0, 0, 0.1875f, 1, 1)), 0.6f, 1f, 1f, true, 0));
        add(new BlockType("ladder_north", List.of(new Box(0, 0, 0.8125f, 1, 1, 1)), 0.6f, 1f, 1f, true, 0));
        add(new BlockType("ladder_south", List.of(new Box(0, 0, 0, 1, 1, 0.1875f)), 0.6f, 1f, 1f, true, 0));
        for (String f : new String[]{"water", "lava"})
            for (int l = 0; l <= 8; l++) add(new BlockType(l == 0 ? f : f + l, List.of(), 0.6f, 1f, 1f, false, 0, f, l));
        add(new BlockType("glass", List.of(FULL), 0.6f, 1f, 1f, false, 0));
        add(new BlockType("bubble_up", List.of(), 0.6f, 1f, 1f, false, 0, "water", 0));
        add(new BlockType("bubble_down", List.of(), 0.6f, 1f, 1f, false, 0, "water", 0));
        add(solid("magma", FULL));
        add(new BlockType("powder_snow", List.of(), 0.6f, 1f, 1f, false, 0));
        add(new BlockType("cobweb", List.of(), 0.6f, 1f, 1f, false, 0));
        add(new BlockType("sweet_berry_bush", List.of(), 0.6f, 1f, 1f, false, 0));
        add(new BlockType("scaffolding", List.of(new Box(0, 0.875f, 0, 1, 1, 1), new Box(0, 0, 0, 0.125f, 1, 0.125f), new Box(0.875f, 0, 0, 1, 1, 0.125f),
                new Box(0, 0, 0.875f, 0.125f, 1, 1), new Box(0.875f, 0, 0.875f, 1, 1, 1)), 0.6f, 1f, 1f, false, 0));
        add(new BlockType("vine", List.of(), 0.6f, 1f, 1f, true, 0));
        for (int h = 1; h <= 7; h++) add(solid("snow" + h, new Box(0, 0, 0, 1, h / 8f, 1)));
        add(new BlockType("ice", List.of(FULL), 0.98f, 1f, 1f, false, 0));
        add(new BlockType("packed_ice", List.of(FULL), 0.98f, 1f, 1f, false, 0));
        add(new BlockType("blue_ice", List.of(FULL), 0.989f, 1f, 1f, false, 0));
        add(new BlockType("slime", List.of(FULL), 0.8f, 1f, 1f, false, 1));
        add(new BlockType("honey", List.of(new Box(0.0625f, 0, 0.0625f, 0.9375f, 1, 0.9375f)), 0.8f, 0.4f, 0.6f, false, 0));
        // bamboo: 16 offset steps over +-0.25 on x and z
        add(solid("bamboo", new Box(0.5f, 0, 0.5f, 0.625f, 1, 0.625f)));
        OFFSETS.put("bamboo", new RandomOffset.Spec(-0.25f, 0.25f, 16, 0, 0, 0, -0.25f, 0.25f, 16));
        add(solid("bamboo_thick", new Box(0.5f, 0, 0.5f, 0.6875f, 1, 0.6875f)));
        OFFSETS.put("bamboo_thick", OFFSETS.get("bamboo"));
        RandomOffset.Spec drip = new RandomOffset.Spec(-0.125f, 0.125f, 16, 0, 0, 0, -0.125f, 0.125f, 16);
        for (String[] d : new String[][]{{"tip_merge", "5", "0", "16"}, {"tip_up", "5", "0", "11"}, {"tip_down", "5", "5", "16"}, {"frustum", "4", "0", "16"}, {"middle", "3", "0", "16"}, {"base", "2", "0", "16"}}) {
            float a = Integer.parseInt(d[1]) / 16f, y0 = Integer.parseInt(d[2]) / 16f, y1 = Integer.parseInt(d[3]) / 16f;
            add(solid("dripstone_" + d[0], new Box(a, y0, a, 1 - a, y1, 1 - a)));
            OFFSETS.put("dripstone_" + d[0], drip);
        }
        add(new BlockType("soul_sand", List.of(new Box(0, 0, 0, 1, 0.875f, 1)), 0.6f, 0.4f, 1f, false, 0));
    }

    public static BlockType get(String name) {
        BlockType t = TYPES.get(name);
        if (t == null) throw new IllegalArgumentException("unknown block " + name);
        return t;
    }
}
