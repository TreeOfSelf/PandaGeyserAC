package me.TreeOfSelf.pandageyserac;

import me.TreeOfSelf.bedrocksim.PlayerState;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BlockAim {
    // real client picks up to 5.66, loose on purpose
    static final double RANGE = 6.0;
    static final double TOUCH_EXTRA = 1.0;
    static final double AIM_MARGIN = 0.5;
    static final int MAX_BLOCKS_PER_INPUT = 4;
    static final double PLACE_BURST = 4, PLACE_REFILL = 0.5;
    private double placeTokens = PLACE_BURST;
    private Vector3i lastPlaceCell;
    private static final Vector3i[] FACES = { Vector3i.from(0, -1, 0), Vector3i.from(0, 1, 0), Vector3i.from(0, 0, -1), Vector3i.from(0, 0, 1), Vector3i.from(-1, 0, 0), Vector3i.from(1, 0, 0) };

    private final AcPlayer player;
    private final GeyserSession session;
    private final Reach reach;
    private InventoryTransactionPacket held;
    private float[] heldBox, heldCell;
    private float[][] heldEyes;
    private double heldMiss;
    private boolean heldBlocked;
    private int[][] heldSkip;
    private long clicks, breaks, flags;

    BlockAim(AcPlayer player, GeyserSession session, Reach reach) {
        this.player = player;
        this.session = session;
        this.reach = reach;
    }

    private boolean judged(boolean started) {
        return started && session.getPlayerEntity().getVehicle() == null && session.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean rangeJudged() {
        return session.getGameMode() != GameMode.CREATIVE;
    }

    private double range() {
        return player.touch() ? RANGE + TOUCH_EXTRA : RANGE;
    }

    Reach.Verdict click(InventoryTransactionPacket t, PlayerState s, boolean started) {
        if (t.getTransactionType() != InventoryTransactionType.ITEM_USE || t.getActionType() != 0 || !judged(started)) return Reach.Verdict.PASS;
        int face = t.getBlockFace();
        if (face < 0 || face > 5) return Reach.Verdict.PASS;
        clicks++;
        Vector3i pos = t.getBlockPosition();
        Vector3f click = t.getClickPosition();
        float[][] eyes = reach.eyePath(s);
        boolean assist = face >= 2 && click != null && click.getY() == 1.0f;
        float[] box = box(pos), cell = assist ? box(pos.add(FACES[face])) : null;
        String why = null;
        Vector3i target = pos.add(FACES[face]);
        if (session.getPlayerInventory().getItemInHand().asItem() instanceof BlockItem && !target.equals(lastPlaceCell)) {
            if (placeTokens < 1) why = String.format("fast place (%.1f placements left)", placeTokens);
            else { placeTokens--; lastPlaceCell = target; }
        }
        if (why == null && !faceVisible(eyes, pos, face) && !(assist && topVisible(eyes, pos)) && !edgeHit(eyes, reach.prevYaw(), reach.prevPitch(), reach.yaw(), reach.pitch(), pos, face))
            why = "hidden face " + face;
        else if (why == null && rangeJudged() && centreDistance(eyes, pos) > range()) why = String.format("out of range (%.3f from the eye to its centre)", centreDistance(eyes, pos));
        if (why != null) return refuseClick(t, why);
        if (!reach.aimJudged()) return Reach.Verdict.PASS;
        double miss = aimMiss(eyes, reach.prevYaw(), reach.prevPitch(), reach.yaw(), reach.pitch(), box, cell);
        int[][] skip = {cellOf(pos), assist ? cellOf(pos.add(FACES[face])) : null};
        boolean blocked = miss <= AIM_MARGIN && behindWall(eyes, reach.prevYaw(), reach.prevPitch(), reach.yaw(), reach.pitch(), box, cell, skip);
        player.log("block click %s face %d aim miss %.4f%s", pos, face, miss, blocked ? " BEHIND A WALL" : miss > AIM_MARGIN ? " HELD" : "");
        if (miss <= AIM_MARGIN && !blocked) return Reach.Verdict.PASS;
        held = t; heldBox = box; heldCell = cell; heldEyes = eyes; heldMiss = miss; heldBlocked = blocked; heldSkip = skip;
        return Reach.Verdict.HOLD;
    }

    boolean heldClick(float nextYaw, float nextPitch) {
        double next = aimMiss(heldEyes, reach.yaw(), reach.pitch(), nextYaw, nextPitch, heldBox, heldCell);
        boolean nextBlocked = next <= AIM_MARGIN && behindWall(heldEyes, reach.yaw(), reach.pitch(), nextYaw, nextPitch, heldBox, heldCell, heldSkip);
        double miss = Math.min(heldMiss, next);
        InventoryTransactionPacket t = held;
        held = null;
        boolean ok = next <= AIM_MARGIN && !nextBlocked;
        player.log("held block click: turn to %.2f %.2f passes %.4f from the block, %s", nextPitch, nextYaw, miss, ok ? "passed" : "dropped");
        if (ok) return true;
        return refuseClick(t, heldBlocked || nextBlocked ? "through a wall" : String.format("aimed %.3f off the block", miss)) == Reach.Verdict.PASS;
    }

    private boolean behindWall(float[][] eyes, float y0, float p0, float y1, float p1, float[] box, float[] cell, int[][] skip) {
        Sight.Result r = Sight.look(eyes, y0, p0, y1, p1, box, player.sight, skip);
        if (r == Sight.Result.NO_HIT && cell != null) r = Sight.look(eyes, y0, p0, y1, p1, cell, player.sight, skip);
        return r == Sight.Result.BLOCKED;
    }

    private static int[] cellOf(Vector3i p) {
        return new int[] {p.getX(), p.getY(), p.getZ()};
    }

    private Reach.Verdict refuseClick(InventoryTransactionPacket t, String why) {
        flags++;
        PandaGeyserAC.log().info(String.format("[%s] block click at %s refused: %s (%d of %d clicks and breaks)%s", player.name(), t.getBlockPosition(), why, flags, clicks + breaks,
                PandaGeyserAC.enforce ? ", block restored" : ""));
        if (!PandaGeyserAC.enforce) return Reach.Verdict.PASS;
        int face = t.getBlockFace();
        BlockUtils.restoreCorrectBlock(session, t.getBlockPosition().add(FACES[face]), t.getHotbarSlot());
        return Reach.Verdict.REFUSE;
    }

    void input(PlayerAuthInputPacket p, PlayerState s, boolean started) {
        placeTokens = Math.min(PLACE_BURST, placeTokens + PLACE_REFILL);
        List<PlayerBlockActionData> actions = p.getPlayerActions();
        if (!p.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS) || actions == null || !judged(started)) return;
        float[][] eyes = null;
        Set<Vector3i> blocks = new HashSet<>();
        for (int i = 0; i < actions.size(); i++) {
            PlayerBlockActionData a = actions.get(i);
            PlayerActionType type = a.getAction();
            if (type != PlayerActionType.START_BREAK && type != PlayerActionType.BLOCK_CONTINUE_DESTROY && type != PlayerActionType.BLOCK_PREDICT_DESTROY) continue;
            Vector3i pos = a.getBlockPosition();
            if (eyes == null) eyes = reach.eyePath(s);
            breaks++;
            String why = null;
            int face = a.getFace();
            if (blocks.add(pos) && blocks.size() > MAX_BLOCKS_PER_INPUT) why = String.format("nuker (%d blocks in one input)", blocks.size());
            else if (player.usingItemConfirmed()) why = "while using an item";
            else if (face >= 0 && face <= 5 && !faceVisible(eyes, pos, face) && !edgeHit(eyes, reach.prevYaw(), reach.prevPitch(), reach.yaw(), reach.pitch(), pos, face)) why = "hidden face " + face;
            else if (rangeJudged() && centreDistance(eyes, pos) > range()) why = String.format("out of range (%.3f from the eye to its centre)", centreDistance(eyes, pos));
            else if (reach.aimJudged()) {
                double miss = aimMiss(eyes, reach.prevYaw(), reach.prevPitch(), reach.yaw(), reach.pitch(), box(pos), null);
                if (miss > AIM_MARGIN) why = String.format("aimed %.3f off the block", miss);
                else if (behindWall(eyes, reach.prevYaw(), reach.prevPitch(), reach.yaw(), reach.pitch(), box(pos), null, new int[][] {cellOf(pos)})) why = "through a wall";
            }
            if (why == null) continue;
            flags++;
            PandaGeyserAC.log().info(String.format("[%s] %s at %s refused: %s (%d of %d clicks and breaks)%s", player.name(), type, pos, why, flags, clicks + breaks,
                    PandaGeyserAC.enforce ? ", block restored" : ""));
            if (!PandaGeyserAC.enforce) continue;
            PlayerBlockActionData abort = new PlayerBlockActionData();
            abort.setAction(PlayerActionType.ABORT_BREAK);
            abort.setBlockPosition(pos);
            abort.setFace(face);
            actions.set(i, abort);
            BlockUtils.stopBreakAndRestoreBlock(session, pos, session.getGeyser().getWorldManager().blockAt(session, pos));
        }
    }

    private static float[] box(Vector3i p) {
        return new float[] {p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1};
    }

    private static double aimMiss(float[][] eyes, float y0, float p0, float y1, float p1, float[] box, float[] cell) {
        double miss = Reach.arcMiss(eyes, y0, p0, y1, p1, box);
        return cell == null || miss == 0 ? miss : Math.min(miss, Reach.arcMiss(eyes, y0, p0, y1, p1, cell));
    }

    private static double centreDistance(float[][] eyes, Vector3i p) {
        double best = Double.MAX_VALUE;
        for (float[] e : eyes) {
            double dx = e[0] - (p.getX() + 0.5), dy = e[1] - (p.getY() + 0.5), dz = e[2] - (p.getZ() + 0.5);
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best;
    }

    private static boolean faceVisible(float[][] eyes, Vector3i pos, int face) {
        for (float[] e : eyes) {
            if (e[0] >= pos.getX() && e[0] <= pos.getX() + 1 && e[1] >= pos.getY() && e[1] <= pos.getY() + 1
                    && e[2] >= pos.getZ() && e[2] <= pos.getZ() + 1) return true;
            boolean v = switch (face) {
                case 0 -> e[1] < pos.getY() + 1e-4f;
                case 1 -> e[1] > pos.getY() + 1 - 1e-4f;
                case 2 -> e[2] < pos.getZ() + 1e-4f;
                case 3 -> e[2] > pos.getZ() + 1 - 1e-4f;
                case 4 -> e[0] < pos.getX() + 1e-4f;
                default -> e[0] > pos.getX() + 1 - 1e-4f;
            };
            if (v) return true;
        }
        return false;
    }

    private static boolean edgeHit(float[][] eyes, float yaw0, float pitch0, float yaw1, float pitch1, Vector3i pos, int face) {
        float dyaw = ((yaw1 - yaw0) % 360 + 540) % 360 - 180;
        double plane = switch (face) {
            case 0 -> pos.getY(); case 1 -> pos.getY() + 1; case 2 -> pos.getZ(); case 3 -> pos.getZ() + 1; case 4 -> pos.getX(); default -> pos.getX() + 1;
        };
        int axis = face < 2 ? 1 : face < 4 ? 2 : 0;
        for (int i = 0; i <= 16; i++) {
            float f = i / 16f;
            double yr = Math.toRadians(yaw0 + dyaw * f), pr = Math.toRadians(pitch0 + (pitch1 - pitch0) * f);
            double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
            for (float[] e : eyes) {
                double t = Sight.enter(e[0], e[1], e[2], dx, dy, dz, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                if (t < 0) continue;
                double at = (axis == 0 ? e[0] + dx * t : axis == 1 ? e[1] + dy * t : e[2] + dz * t);
                if (Math.abs(at - plane) < 1e-3) return true;
            }
        }
        return false;
    }

    private static boolean topVisible(float[][] eyes, Vector3i pos) {
        return faceVisible(eyes, pos, 1);
    }
}
