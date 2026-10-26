package me.TreeOfSelf.pandageyserac;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.TreeOfSelf.bedrocksim.BlockType;
import me.TreeOfSelf.bedrocksim.BlockWorld;
import me.TreeOfSelf.bedrocksim.Box;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.translator.collision.BlockCollision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GeyserWorld implements BlockWorld {
    private static final Int2ObjectOpenHashMap<BlockType> TYPES = new Int2ObjectOpenHashMap<>();

    private static final Map<String, String> ENGINE_NAMES = Map.ofEntries(
            Map.entry("ice", "ice"), Map.entry("packed_ice", "packed_ice"), Map.entry("blue_ice", "blue_ice"), Map.entry("frosted_ice", "ice"),
            Map.entry("slime_block", "slime"), Map.entry("honey_block", "honey"), Map.entry("soul_sand", "soul_sand"),
            Map.entry("cobweb", "cobweb"), Map.entry("sweet_berry_bush", "sweet_berry_bush"), Map.entry("powder_snow", "powder_snow"),
            Map.entry("magma_block", "magma"), Map.entry("scaffolding", "scaffolding"), Map.entry("chest", "chest"), Map.entry("trapped_chest", "chest"), Map.entry("ender_chest", "chest"));

    final ClientWorld client;
    boolean touchedMissingChunk;
    boolean touchedUncertain;
    static final String UNCERTAIN = "uncertain:";

    public GeyserWorld(ClientWorld client) {
        this.client = client;
    }

    @Override
    public BlockType type(int x, int y, int z) {
        int b0 = client.bedrockAt(x, y, z, 0);
        if (b0 < 0) {
            if (b0 == ClientWorld.UNCERTAIN) touchedUncertain = true;
            else touchedMissingChunk = true;
            return BlockType.AIR;
        }
        boolean waterlogged = client.isWater(client.bedrockAt(x, y, z, 1));
        int javaId = client.javaId(b0);
        ConnectedShapes.Kind kind = kindOf(javaId);
        if (kind == ConnectedShapes.Kind.BARS || kind == ConnectedShapes.Kind.WALL) return connectedType(x, y, z, javaId, kind, waterlogged);
        if (kind == ConnectedShapes.Kind.FENCE || kind == ConnectedShapes.Kind.STAIRS) {
            int before = javaId;
            javaId = connectedState(x, y, z, javaId, kind);
            if (DEBUG_SHAPES.add(javaId)) PandaGeyserAC.log().info("connected " + BlockState.of(before) + " -> " + BlockState.of(javaId) + " boxes " + shapes(javaId));
        }
        if (kind == ConnectedShapes.Kind.NONE) javaId = pistonState(x, y, z, javaId);
        int key = javaId << 1 | (waterlogged ? 1 : 0);
        BlockType t;
        synchronized (TYPES) {
            t = TYPES.get(key);
            if (t == null) TYPES.put(key, t = convert(key >> 1, waterlogged));
        }
        if (t.name().startsWith(UNCERTAIN)) touchedUncertain = true;
        return t;
    }

    private static final java.util.Set<Integer> DEBUG_SHAPES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Int2ObjectOpenHashMap<ConnectedShapes.Kind> KINDS = new Int2ObjectOpenHashMap<>();
    private static final Map<String, BlockType> CONNECTED = new java.util.HashMap<>();
    private final it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap fullCache = new it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap();

    private static ConnectedShapes.Kind kindOf(int javaId) {
        synchronized (KINDS) {
            ConnectedShapes.Kind k = KINDS.get(javaId);
            if (k == null) KINDS.put(javaId, k = ConnectedShapes.kind(BlockState.of(javaId).block().javaIdentifier().value()));
            return k;
        }
    }

    private ConnectedShapes.Neighbour neighbour(int x, int y, int z) {
        int b = client.bedrockAt(x, y, z, 0);
        if (b < 0) return null;
        int j = client.javaId(b);
        boolean full;
        if (fullCache.containsKey(b)) full = fullCache.get(b);
        else fullCache.put(b, full = isFullCube(b));
        BlockState st = BlockState.of(j);
        return new ConnectedShapes.Neighbour(st, st.block().javaIdentifier().value(), kindOf(j), full);
    }

    private int pistonState(int x, int y, int z, int javaId) {
        BlockState st = BlockState.of(javaId);
        String name = st.block().javaIdentifier().value();
        if (!name.equals("piston") && !name.equals("sticky_piston")) return javaId;
        org.cloudburstmc.math.vector.Vector3i d = st.getValue(Properties.FACING).getUnitVector();
        int b = client.bedrockAt(x + d.getX(), y + d.getY(), z + d.getZ(), 0);
        boolean head = b >= 0 && BlockState.of(client.javaId(b)).block().javaIdentifier().value().equals("piston_head");
        return st.withValue(Properties.EXTENDED, head).javaId();
    }

    private int connectedState(int x, int y, int z, int javaId, ConnectedShapes.Kind kind) {
        BlockState st = BlockState.of(javaId);
        if (kind == ConnectedShapes.Kind.STAIRS) return ConnectedShapes.stairsState(st, x, y, z, this::neighbour);
        String name = st.block().javaIdentifier().value();
        return ConnectedShapes.fenceState(st, ConnectedShapes.fenceAttach(name, neighbour(x, y, z - 1), Direction.SOUTH),
                ConnectedShapes.fenceAttach(name, neighbour(x + 1, y, z), Direction.WEST), ConnectedShapes.fenceAttach(name, neighbour(x, y, z + 1), Direction.NORTH),
                ConnectedShapes.fenceAttach(name, neighbour(x - 1, y, z), Direction.EAST));
    }

    private BlockType connectedType(int x, int y, int z, int javaId, ConnectedShapes.Kind kind, boolean waterlogged) {
        String name = BlockState.of(javaId).block().javaIdentifier().value();
        boolean n, e, s, w, post = false;
        if (kind == ConnectedShapes.Kind.BARS) {
            n = ConnectedShapes.barsAttach(neighbour(x, y, z - 1)); e = ConnectedShapes.barsAttach(neighbour(x + 1, y, z));
            s = ConnectedShapes.barsAttach(neighbour(x, y, z + 1)); w = ConnectedShapes.barsAttach(neighbour(x - 1, y, z));
        } else {
            n = ConnectedShapes.wallAttach(neighbour(x, y, z - 1), Direction.SOUTH); e = ConnectedShapes.wallAttach(neighbour(x + 1, y, z), Direction.WEST);
            s = ConnectedShapes.wallAttach(neighbour(x, y, z + 1), Direction.NORTH); w = ConnectedShapes.wallAttach(neighbour(x - 1, y, z), Direction.EAST);
            ConnectedShapes.Neighbour above = neighbour(x, y + 1, z);
            post = above != null && (above.sturdy() || above.kind() == ConnectedShapes.Kind.WALL);
        }
        String key = name + (n ? "n" : "") + (e ? "e" : "") + (s ? "s" : "") + (w ? "w" : "") + (post ? "p" : "") + (waterlogged ? "~" : "");
        synchronized (CONNECTED) {
            BlockType t = CONNECTED.get(key);
            if (t == null) {
                var shapes = kind == ConnectedShapes.Kind.BARS ? ConnectedShapes.barsShape(n, e, s, w) : ConnectedShapes.wallShape(n, e, s, w, post);
                CONNECTED.put(key, t = new BlockType(name, shapes, 0.6f, 1f, 1f, false, 0, waterlogged ? "water" : null, 0));
            }
            return t;
        }
    }

    static BlockType convert(int javaId, boolean waterloggedOnClient) {
        BlockState state = BlockState.of(javaId);
        String name = state.block().javaIdentifier().value();
        if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")) return BlockType.AIR;
        if (name.equals("water") || name.equals("lava")) {
            int level = state.getValue(Properties.LEVEL);
            return BlockType.get(level == 0 ? name : name + Math.min(level, 8));
        }
        if (name.equals("bubble_column")) return BlockType.get(state.getValue(Properties.DRAG) ? "bubble_down" : "bubble_up");
        if (name.equals("bamboo")) return BlockType.get(state.getValue(Properties.AGE_1) == 1 ? "bamboo_thick" : "bamboo");
        if (name.equals("pointed_dripstone")) {
            String thickness = state.getValue(Properties.SPELEOTHEM_THICKNESS);
            if (thickness.equals("tip")) thickness = state.getValue(Properties.VERTICAL_DIRECTION) == org.geysermc.geyser.level.physics.Direction.UP ? "tip_up" : "tip_down";
            return BlockType.get("dripstone_" + thickness);
        }
        String engineName = ENGINE_NAMES.get(name);
        if (engineName != null) return BlockType.get(engineName);
        if (name.endsWith("_bed")) return BlockType.get("bed");
        List<Box> shapes = shapes(javaId);
        List<Box> bedrock = BedrockShapes.shape(name, state, shapes);
        if (bedrock != null) shapes = bedrock;
        if (BedrockShapes.PLAYER_DEPENDENT.contains(name)) return new BlockType(UNCERTAIN + name, List.of(), 0.6f, 1f, 1f, false, 0);
        boolean climbable = name.equals("ladder") || name.equals("vine") || name.endsWith("_vines") || name.endsWith("_vines_plant");
        return new BlockType(name, shapes, 0.6f, 1f, 1f, climbable, 0, waterloggedOnClient ? "water" : null, 0);
    }

    private final it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap sightCache = new it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap();

    boolean blocksSight(int runtimeId) {
        if (sightCache.containsKey(runtimeId)) return sightCache.get(runtimeId);
        boolean full = isFullCube(runtimeId) && !BlockState.of(client.javaId(runtimeId)).block().javaIdentifier().value().equals("barrier");
        sightCache.put(runtimeId, full);
        return full;
    }

    boolean isFullCube(int runtimeId) {
        BlockState state = BlockState.of(client.javaId(runtimeId));
        String name = state.block().javaIdentifier().value();
        if (ENGINE_NAMES.containsKey(name) || BedrockShapes.PLAYER_DEPENDENT.contains(name)) return false;
        List<Box> shapes = shapes(state.javaId());
        List<Box> bedrock = BedrockShapes.shape(name, state, shapes);
        if (bedrock != null) shapes = bedrock;
        if (shapes.size() != 1) return false;
        Box b = shapes.get(0);
        return b.minX() == 0 && b.minY() == 0 && b.minZ() == 0 && b.maxX() == 1 && b.maxY() == 1 && b.maxZ() == 1;
    }

    static List<Box> shapes(int javaId) {
        BlockCollision collision = BlockRegistries.COLLISIONS.get(javaId);
        if (collision == null) return List.of();
        List<Box> out = new ArrayList<>();
        for (BoundingBox b : collision.getBoundingBoxes()) {
            float hx = (float) (b.getSizeX() / 2), hy = (float) (b.getSizeY() / 2), hz = (float) (b.getSizeZ() / 2);
            float mx = (float) b.getMiddleX(), my = (float) b.getMiddleY(), mz = (float) b.getMiddleZ();
            out.add(new Box(mx - hx, my - hy, mz - hz, mx + hx, my + hy, mz + hz));
        }
        return out;
    }
}
