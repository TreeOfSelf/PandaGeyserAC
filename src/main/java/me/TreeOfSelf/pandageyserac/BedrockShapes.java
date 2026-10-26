/*
 * Shapes adapted from Boar (https://github.com/opencollab-incubator/Boar), ac.boar.anticheat.collision.BedrockCollision.
 * MIT License, Copyright (c) 2026 oryxel; full text in THIRD_PARTY_LICENSES.md.
 */
package me.TreeOfSelf.pandageyserac;

import me.TreeOfSelf.bedrocksim.Box;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Axis;
import org.geysermc.geyser.level.physics.Direction;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BedrockShapes {
    static final List<Box> EMPTY = List.of();
    static final List<Box> SOLID = List.of(new Box(0, 0, 0, 1, 1, 1));
    static final List<Box> LECTERN = List.of(new Box(0, 0, 0, 1, 0.9f, 1));
    static final List<Box> CONDUIT = List.of(new Box(0.25f, 0, 0.25f, 0.75f, 0.5f, 0.75f));
    static final List<Box> BREWING_STAND = List.of(new Box(0, 0, 0, 1, 0.125f, 1), new Box(0.4375f, 0, 0.4375f, 0.5625f, 0.875f, 0.5625f));
    static final List<Box> GRINDSTONE = List.of(new Box(0.125f, 0, 0.125f, 0.875f, 1, 0.875f));
    static final List<Box> GRINDSTONE_Z = List.of(new Box(0.125f, 0.125f, 0, 0.875f, 0.875f, 1));
    static final List<Box> GRINDSTONE_X = List.of(new Box(0, 0.125f, 0.125f, 1, 0.875f, 0.875f));
    static final List<Box> CAKE = List.of(new Box(0.0625f, 0, 0.0625f, 0.9375f, 0.5f, 0.9375f));
    static final List<Box> HEAD = List.of(new Box(0.25f, 0, 0.25f, 0.75f, 0.5f, 0.75f));
    static final List<Box> CACTUS = List.of(new Box(0.0625f, 0, 0.0625f, 0.9375f, 1, 0.9375f));
    static final List<Box> END_PORTAL_FRAME = List.of(new Box(0, 0, 0, 1, 0.8125f, 1));
    static final List<Box> END_PORTAL = List.of(new Box(0, 0, 0, 1, 0.25f, 1));
    static final List<Box> TURTLE_EGG = List.of(new Box(0.2f, 0, 0.2f, 0.8f, 0.45f, 0.8f));
    static final List<Box> LANTERN = List.of(new Box(0.3125f, 0, 0.3125f, 0.6875f, 0.5f, 0.6875f));
    static final List<Box> HANGING_LANTERN = List.of(new Box(0.3125f, 0.125f, 0.3125f, 0.6875f, 0.625f, 0.6875f));
    static final List<Box> ANVIL_X = List.of(new Box(0, 0, 0.125f, 1, 1, 0.875f));
    static final List<Box> ANVIL_Z = List.of(new Box(0.125f, 0, 0, 0.875f, 1, 1));
    static final List<Box> CAULDRON = List.of(new Box(0, 0, 0, 1, 0.3125f, 1), new Box(0, 0, 0, 0.125f, 1, 1), new Box(0, 0, 0, 1, 1, 0.125f),
            new Box(0.875f, 0, 0, 1, 1, 1), new Box(0, 0, 0.875f, 1, 1, 1));
    // trapdoors and doors are 0.1825 thick on Bedrock (Java 0.1875)
    static final List<Box> THIN_EAST = List.of(new Box(0, 0, 0, 0.1825f, 1, 1));
    static final List<Box> THIN_WEST = List.of(new Box(0.8175f, 0, 0, 1, 1, 1));
    static final List<Box> THIN_SOUTH = List.of(new Box(0, 0, 0, 1, 1, 0.1825f));
    static final List<Box> THIN_NORTH = List.of(new Box(0, 0, 0.8175f, 1, 1, 1));
    static final List<Box> THIN_BOTTOM = List.of(new Box(0, 0, 0, 1, 0.1825f, 1));
    static final List<Box> THIN_TOP = List.of(new Box(0, 0.8175f, 0, 1, 1, 1));

    static final Set<String> PLAYER_DEPENDENT = Set.of();

    private static final Map<String, List<Box>> TABLE = loadTable();

    private static Map<String, List<Box>> loadTable() {
        Map<String, List<Box>> t = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(BedrockShapes.class.getResourceAsStream("/bedrock_shapes.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] f = line.split("\\|", -1);
                List<Box> boxes = new ArrayList<>();
                for (String b : f[2].isEmpty() ? new String[0] : f[2].split(";")) {
                    String[] c = b.split(" ");
                    boxes.add(new Box(Float.parseFloat(c[0]), Float.parseFloat(c[1]), Float.parseFloat(c[2]), Float.parseFloat(c[3]), Float.parseFloat(c[4]), Float.parseFloat(c[5])));
                }
                t.put(f[0] + "|" + f[1], List.copyOf(boxes));
            }
        } catch (Exception e) {
            throw new IllegalStateException("bedrock_shapes.txt", e);
        }
        return t;
    }

    private static String tableKey(String name, BlockState state) {
        String s = state.toString();
        int i = s.indexOf('[');
        if (i < 0) return name + "|";
        String[] props = s.substring(i + 1, s.length() - 1).split(",");
        Arrays.sort(props);
        return name + "|" + String.join(",", props);
    }

    static List<Box> shape(String name, BlockState state, List<Box> java) {
        List<Box> table = TABLE.get(tableKey(name, state));
        if (table != null) return table;
        switch (name) {
            case "sea_lantern", "dragon_egg" -> { return SOLID; }
            case "end_portal_frame" -> { return END_PORTAL_FRAME; }
            case "end_portal" -> { return END_PORTAL; }
            case "sea_pickle" -> { return EMPTY; }
            case "turtle_egg" -> { return TURTLE_EGG; }
            case "lectern" -> { return LECTERN; }
            case "conduit" -> { return CONDUIT; }
            case "cactus" -> { return CACTUS; }
            case "brewing_stand" -> { return BREWING_STAND; }
            case "piglin_head" -> { return HEAD; }
            case "piglin_wall_head" -> {
                return switch (state.getValue(Properties.HORIZONTAL_FACING)) {
                    case SOUTH -> List.of(new Box(0.25f, 0.25f, 0, 0.75f, 0.75f, 0.5f));
                    case WEST -> List.of(new Box(0.5f, 0.25f, 0.25f, 1, 0.75f, 0.75f));
                    case EAST -> List.of(new Box(0, 0.25f, 0.25f, 0.5f, 0.75f, 0.75f));
                    default -> List.of(new Box(0.25f, 0.25f, 0.5f, 0.75f, 0.75f, 1));
                };
            }
            case "grindstone" -> {
                if (!"wall".equals(state.getValue(Properties.ATTACH_FACE))) return GRINDSTONE;
                return state.getValue(Properties.HORIZONTAL_FACING).getAxis() == Axis.Z ? GRINDSTONE_Z : GRINDSTONE_X;
            }
            case "bell" -> {
                if (!"floor".equals(state.getValue(Properties.BELL_ATTACHMENT))) return null;
                return java.stream().map(b -> new Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY() - 0.1875f, b.maxZ())).toList();
            }
            default -> { }
        }
        if (name.equals("anvil") || name.equals("chipped_anvil") || name.equals("damaged_anvil"))
            return state.getValue(Properties.HORIZONTAL_FACING).getAxis() == Axis.X ? ANVIL_X : ANVIL_Z;
        if (name.equals("lantern") || name.equals("soul_lantern") || name.endsWith("copper_lantern"))
            return Boolean.TRUE.equals(state.getValue(Properties.HANGING)) ? HANGING_LANTERN : LANTERN;
        if (name.endsWith("cauldron")) return CAULDRON;
        if (name.endsWith("candle_cake")) return CAKE;
        if (name.endsWith("_trapdoor")) {
            if (!state.getValue(Properties.OPEN)) return "top".equals(state.getValue(Properties.HALF)) ? THIN_TOP : THIN_BOTTOM;
            return switch (state.getValue(Properties.HORIZONTAL_FACING)) {
                case SOUTH -> THIN_SOUTH;
                case WEST -> THIN_WEST;
                case EAST -> THIN_EAST;
                default -> THIN_NORTH;
            };
        }
        if (name.endsWith("_door")) {
            Direction facing = state.getValue(Properties.HORIZONTAL_FACING);
            boolean closed = !state.getValue(Properties.OPEN);
            boolean right = "right".equals(state.getValue(Properties.DOOR_HINGE));
            return switch (facing) {
                case SOUTH -> closed ? THIN_SOUTH : (right ? THIN_EAST : THIN_WEST);
                case WEST -> closed ? THIN_WEST : (right ? THIN_SOUTH : THIN_NORTH);
                case NORTH -> closed ? THIN_NORTH : (right ? THIN_WEST : THIN_EAST);
                default -> closed ? THIN_EAST : (right ? THIN_NORTH : THIN_SOUTH);
            };
        }
        return null;
    }
}
