/*
 * Adapted from Boar (https://github.com/opencollab-incubator/Boar): ac.boar.geyser.anticheat.util.block.GeyserBlockUtil and
 * ac.boar.anticheat.collision.BedrockCollision.buildThinBarsShape.
 * MIT License, Copyright (c) 2026 oryxel; full text in THIRD_PARTY_LICENSES.md.
 */
package me.TreeOfSelf.pandageyserac;

import me.TreeOfSelf.bedrocksim.Box;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.registry.BlockRegistries;

import java.util.ArrayList;
import java.util.List;

final class ConnectedShapes {
    enum Kind { NONE, FENCE, BARS, WALL, STAIRS, GATE }

    private ConnectedShapes() { }

    static Kind kind(String name) {
        if (name.endsWith("_fence")) return Kind.FENCE;
        if (name.endsWith("_fence_gate")) return Kind.GATE;
        if (name.endsWith("glass_pane") || name.endsWith("_bars")) return Kind.BARS;
        if (name.endsWith("_wall") && !name.endsWith("_sign_wall")) return Kind.WALL;
        if (name.endsWith("_stairs")) return Kind.STAIRS;
        return Kind.NONE;
    }

    private static boolean exception(String name) {
        return name.endsWith("_leaves") || name.equals("barrier") || name.equals("carved_pumpkin") || name.equals("jack_o_lantern")
                || name.equals("melon") || name.equals("pumpkin") || name.endsWith("shulker_box");
    }

    record Neighbour(BlockState state, String name, Kind kind, boolean sturdy) { }

    interface Lookup {
        Neighbour at(int x, int y, int z);
    }

    static List<Box> barsShape(boolean n, boolean e, boolean s, boolean w) {
        if (!n && !e && !s && !w) return List.of(new Box(0.4375f, 0, 0.4375f, 0.5625f, 1, 0.5625f));
        List<Box> boxes = new ArrayList<>();
        if (n) boxes.add(new Box(0.4375f, 0, 0, 0.5625f, 1, 0.5f));
        if (s) boxes.add(new Box(0.4375f, 0, 0.5f, 0.5625f, 1, 1));
        if (w) boxes.add(new Box(0, 0, 0.4375f, 0.5f, 1, 0.5625f));
        if (e) boxes.add(new Box(0.5f, 0, 0.4375f, 1, 1, 0.5625f));
        return boxes;
    }

    static List<Box> wallShape(boolean n, boolean e, boolean s, boolean w, boolean postAbove) {
        boolean straight = n && s && !e && !w || e && w && !n && !s;
        boolean up = postAbove || !straight;
        float minX = 1f, minZ = 1f, maxX = 0f, maxZ = 0f;
        if (up) { minX = Math.min(minX, 0.25f); maxX = Math.max(maxX, 0.75f); minZ = Math.min(minZ, 0.25f); maxZ = Math.max(maxZ, 0.75f); }
        if (n) { minX = Math.min(minX, 0.3125f); maxX = Math.max(maxX, 0.6875f); minZ = 0f; }
        if (s) { minX = Math.min(minX, 0.3125f); maxX = Math.max(maxX, 0.6875f); maxZ = 1f; }
        if (w) { minZ = Math.min(minZ, 0.3125f); maxZ = Math.max(maxZ, 0.6875f); minX = 0f; }
        if (e) { minZ = Math.min(minZ, 0.3125f); maxZ = Math.max(maxZ, 0.6875f); maxX = 1f; }
        return List.of(new Box(minX, 0f, minZ, maxX, 1.5f, maxZ));
    }

    static boolean barsAttach(Neighbour nb) {
        return nb != null && (!exception(nb.name) && nb.sturdy || nb.kind == Kind.BARS || nb.kind == Kind.WALL);
    }

    static boolean wallAttach(Neighbour nb, Direction toward) {
        return nb != null && (nb.kind == Kind.WALL || nb.kind == Kind.BARS || gateAlong(nb, toward) || !exception(nb.name) && nb.sturdy);
    }

    static boolean fenceAttach(String self, Neighbour nb, Direction toward) {
        if (nb == null) return false;
        boolean sameFence = nb.kind == Kind.FENCE && nb.name.equals("nether_brick_fence") == self.equals("nether_brick_fence");
        return !exception(nb.name) && nb.sturdy || sameFence || gateAlong(nb, toward);
    }

    private static boolean gateAlong(Neighbour nb, Direction toward) {
        if (nb.kind != Kind.GATE) return false;
        Direction facing = nb.state.getValue(Properties.HORIZONTAL_FACING);
        return facing != null && clockwise(facing).getAxis() == toward.getAxis();
    }

    private static Direction clockwise(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> d;
        };
    }

    private static Direction counterClockwise(Direction d) {
        return switch (d) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> d;
        };
    }

    static int fenceState(BlockState state, boolean n, boolean e, boolean s, boolean w) {
        String id = state.toString().replaceAll("north=(true|false)", "north=" + n).replaceAll("east=(true|false)", "east=" + e)
                .replaceAll("south=(true|false)", "south=" + s).replaceAll("west=(true|false)", "west=" + w).replace("waterlogged=true", "waterlogged=false");
        return lookup(id, state);
    }

    private static final java.util.Set<String> MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static int lookup(String id, BlockState state) {
        int found = BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.getOrDefault(id, -1);
        if (found >= 0) return found;
        if (MISSING.add(id)) PandaGeyserAC.log().warning("connected shape: no Java state " + id + " (kept " + state + ")");
        return state.javaId();
    }

    static int stairsState(BlockState state, int x, int y, int z, Lookup world) {
        Direction facing = state.getValue(Properties.HORIZONTAL_FACING);
        Object half = state.getValue(Properties.HALF);
        String shape = "straight";
        Neighbour ahead = world.at(x + facing.getUnitVector().getX(), y, z + facing.getUnitVector().getZ());
        if (stairsLike(ahead, half)) {
            Direction d2 = ahead.state.getValue(Properties.HORIZONTAL_FACING);
            if (d2.getAxis() != facing.getAxis() && differentOrientation(state, x, y, z, d2.reversed(), world))
                shape = d2 == counterClockwise(facing) ? "outer_left" : "outer_right";
        }
        if (shape.equals("straight")) {
            Direction back = facing.reversed();
            Neighbour behind = world.at(x + back.getUnitVector().getX(), y, z + back.getUnitVector().getZ());
            if (stairsLike(behind, half)) {
                Direction d3 = behind.state.getValue(Properties.HORIZONTAL_FACING);
                if (d3.getAxis() != facing.getAxis() && differentOrientation(state, x, y, z, d3, world))
                    shape = d3 == counterClockwise(facing) ? "inner_left" : "inner_right";
            }
        }
        String id = state.toString().replaceAll("shape=[a-z_]+", "shape=" + shape).replace("waterlogged=true", "waterlogged=false");
        return lookup(id, state);
    }

    private static boolean stairsLike(Neighbour nb, Object half) {
        return nb != null && nb.kind == Kind.STAIRS && java.util.Objects.equals(nb.state.getValue(Properties.HALF), half);
    }

    private static boolean differentOrientation(BlockState state, int x, int y, int z, Direction dir, Lookup world) {
        Neighbour nb = world.at(x + dir.getUnitVector().getX(), y, z + dir.getUnitVector().getZ());
        return nb == null || nb.kind != Kind.STAIRS || nb.state.getValue(Properties.HORIZONTAL_FACING) != state.getValue(Properties.HORIZONTAL_FACING)
                || !java.util.Objects.equals(nb.state.getValue(Properties.HALF), state.getValue(Properties.HALF));
    }
}
