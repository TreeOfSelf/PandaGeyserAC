package me.TreeOfSelf.pandageyserac;

import me.TreeOfSelf.bedrocksim.PlayerState;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class Reach {
    static final float MAX_REACH = Float.parseFloat(System.getProperty("pandageyserac.reach", "3.3"));
    static final float GROW = 0.1f;
    static final double AIM_MARGIN = 0.3;
    private static final int ARC_STEPS = 16;
    static final int LERP_PLAYER = 3, LERP_OTHER = 6;
    static final long RECENT_NANOS = 1_000_000_000L;
    static final float EYE_SNEAK = 1.27f, EYE_PRONE = 0.4f;

    private static final class Lerp {
        float x, y, z, px, py, pz, tx, ty, tz;
        int steps;

        Lerp(float x, float y, float z) {
            this.x = px = tx = x; this.y = py = ty = y; this.z = pz = tz = z;
        }

        void target(float[] p, int steps) {
            tx = p[0]; ty = p[1]; tz = p[2];
            this.steps = steps;
        }

        void tick() {
            px = x; py = y; pz = z;
            if (steps <= 0) return;
            x += (tx - x) / steps; y += (ty - y) / steps; z += (tz - z) / steps;
            steps--;
        }
    }

    private record Move(float[] pos, boolean teleport, long pingBefore) { }

    private static final class Tracked {
        final boolean player;
        final String type;
        float offset, width, height, scale = 1;
        final Lerp late, early;
        float[] sent;
        final ArrayDeque<Move> pending = new ArrayDeque<>();
        final ArrayDeque<float[]> recent = new ArrayDeque<>();
        final ArrayDeque<Long> recentTimes = new ArrayDeque<>();
        int earlyCount;
        boolean riding;

        Tracked(boolean player, String type, float offset, float x, float y, float z, EntityDataMap meta) {
            this.player = player;
            this.type = type;
            this.offset = offset;
            width = player ? 0.6f : 1;
            height = player ? 1.8f : 1;
            size(meta, false);
            late = new Lerp(x, y, z);
            early = new Lerp(x, y, z);
            sent = new float[] {x, y, z};
        }

        void size(EntityDataMap meta, boolean grow) {
            if (meta == null) return;
            Float w = meta.get(EntityDataTypes.WIDTH), h = meta.get(EntityDataTypes.HEIGHT), sc = meta.get(EntityDataTypes.SCALE);
            if (w != null) width = grow ? Math.max(width, w) : w;
            if (h != null) height = grow ? Math.max(height, h) : h;
            if (sc != null) scale = grow ? Math.max(scale, sc) : sc;
        }

        int steps(Move m) {
            return m.teleport ? 1 : player ? LERP_PLAYER : LERP_OTHER;
        }
    }

    private final AcPlayer player;
    private final GeyserSession session;
    private final Map<Long, Tracked> entities = new HashMap<>();
    private final Map<Long, Long> uniqueToRuntime = new HashMap<>();
    private float prevX, prevY, prevZ, curX, curY, curZ;
    private boolean prevLow, curLow;
    private boolean haveTick;
    private float yaw, pitch, prevYaw, prevPitch;
    private boolean aimJudged;
    private long attacks, flags, aimFlags;
    private float[] heldBox;
    private float[][] heldEyes;
    private String heldType;
    private double heldMiss;
    private boolean heldBlocked;

    enum Verdict { PASS, REFUSE, HOLD }

    Reach(AcPlayer player, GeyserSession session) {
        this.player = player;
        this.session = session;
    }

    private float offset(long runtimeId, float fallback) {
        try {
            Entity e = session.getEntityCache().getEntityByGeyserId(runtimeId);
            return e != null ? e.getOffset() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    Runnable onSend(BedrockPacket packet, long selfId, long pingBefore) {
        if (packet instanceof AddEntityPacket p) {
            long id = p.getRuntimeEntityId();
            float off = offset(id, 0);
            Vector3f pos = p.getPosition();
            entities.put(id, new Tracked(false, p.getIdentifier(), off, pos.getX(), pos.getY() - off, pos.getZ(), p.getMetadata()));
            uniqueToRuntime.put(p.getUniqueEntityId(), id);
            return null;
        }
        if (packet instanceof AddPlayerPacket p) {
            long id = p.getRuntimeEntityId();
            float off = offset(id, PlayerState.EYE_HEIGHT);
            Vector3f pos = p.getPosition();
            entities.put(id, new Tracked(true, "minecraft:player", off, pos.getX(), pos.getY() - off, pos.getZ(), p.getMetadata()));
            uniqueToRuntime.put(p.getUniqueEntityId(), id);
            return null;
        }
        if (packet instanceof MoveEntityAbsolutePacket p)
            return moved(p.getRuntimeEntityId(), p.getPosition().getX(), p.getPosition().getY(), p.getPosition().getZ(), true, true, true, p.isTeleported(), pingBefore);
        if (packet instanceof MoveEntityDeltaPacket p) {
            var fl = p.getFlags();
            boolean hx = fl.contains(MoveEntityDeltaPacket.Flag.HAS_X), hy = fl.contains(MoveEntityDeltaPacket.Flag.HAS_Y), hz = fl.contains(MoveEntityDeltaPacket.Flag.HAS_Z);
            if (!hx && !hy && !hz) return null;
            return moved(p.getRuntimeEntityId(), p.getX(), p.getY(), p.getZ(), hx, hy, hz, false, pingBefore);
        }
        if (packet instanceof MovePlayerPacket p && p.getRuntimeEntityId() != selfId)
            return moved(p.getRuntimeEntityId(), p.getPosition().getX(), p.getPosition().getY(), p.getPosition().getZ(), true, true, true,
                    p.getMode() != MovePlayerPacket.Mode.NORMAL && p.getMode() != MovePlayerPacket.Mode.HEAD_ROTATION, pingBefore);
        if (packet instanceof SetEntityDataPacket p) {
            Tracked t = entities.get(p.getRuntimeEntityId());
            if (t == null) return null;
            EntityDataMap meta = p.getMetadata();
            t.size(meta, true);
            return () -> t.size(meta, false);
        }
        if (packet instanceof SetEntityLinkPacket p) {
            EntityLinkData link = p.getEntityLink();
            Long id = uniqueToRuntime.get(link.getTo());
            Tracked t = id == null ? null : entities.get(id);
            if (t == null) return null;
            if (link.getType() != EntityLinkData.Type.REMOVE) {
                t.riding = true;
                return null;
            }
            return () -> t.riding = false;
        }
        if (packet instanceof RemoveEntityPacket p) {
            Long id = uniqueToRuntime.remove(p.getUniqueEntityId());
            if (id == null) return null;
            Tracked t = entities.get(id);
            return () -> entities.remove(id, t);
        }
        if (packet instanceof ChangeDimensionPacket) {
            Map<Long, Tracked> old = new HashMap<>(entities);
            uniqueToRuntime.values().removeIf(old::containsKey);
            return () -> old.forEach(entities::remove);
        }
        return null;
    }

    private Runnable moved(long id, float x, float y, float z, boolean hx, boolean hy, boolean hz, boolean teleport, long pingBefore) {
        Tracked t = entities.get(id);
        if (t == null) return null;
        if (t.player) t.offset = offset(id, t.offset);
        float[] pos = {hx ? x : t.sent[0], hy ? y - t.offset : t.sent[1], hz ? z : t.sent[2]};
        t.sent = pos;
        long now = System.nanoTime();
        t.recent.addLast(pos);
        t.recentTimes.addLast(now);
        while (now - t.recentTimes.peekFirst() > RECENT_NANOS) { t.recent.pollFirst(); t.recentTimes.pollFirst(); }
        Move m = new Move(pos, teleport, pingBefore);
        t.pending.addLast(m);
        return () -> {
            while (!t.pending.isEmpty()) {
                Move done = t.pending.pollFirst();
                if (t.earlyCount > 0) t.earlyCount--;
                else t.early.target(done.pos, t.steps(done));
                t.late.target(done.pos, t.steps(done));
                if (done == m) break;
            }
        };
    }

    void onPingAcked(long id) {
        for (Tracked t : entities.values()) {
            int i = 0;
            for (Move m : t.pending) {
                if (i++ < t.earlyCount) continue;
                if (m.pingBefore > id) break;
                t.early.target(m.pos, t.steps(m));
                t.earlyCount++;
            }
        }
    }

    void clientTick(PlayerState s, float yaw, float pitch, boolean aimJudged) {
        this.aimJudged = aimJudged;
        prevYaw = haveTick ? this.yaw : yaw; prevPitch = haveTick ? this.pitch : pitch;
        this.yaw = yaw; this.pitch = pitch;
        prevX = haveTick ? curX : s.x; prevY = haveTick ? curY : s.y; prevZ = haveTick ? curZ : s.z;
        curX = s.x; curY = s.y; curZ = s.z;
        boolean low = lowPose(s);
        prevLow = haveTick ? curLow : low; curLow = low;
        haveTick = true;
        for (Tracked t : entities.values()) { t.late.tick(); t.early.tick(); }
    }

    float drawnDistance(long id, float x, float y, float z) {
        Tracked e = entities.get(id);
        if (e == null) return Float.NaN;
        Lerp a = e.late, b = e.early;
        float minX = min(a.x, a.px, b.x, b.px), minY = min(a.y, a.py, b.y, b.py), minZ = min(a.z, a.pz, b.z, b.pz);
        float maxX = max(a.x, a.px, b.x, b.px), maxY = max(a.y, a.py, b.y, b.py), maxZ = max(a.z, a.pz, b.z, b.pz);
        long now = System.nanoTime();
        var times = e.recentTimes.iterator();
        for (float[] r : e.recent) {
            if (now - times.next() > RECENT_NANOS) continue;
            minX = Math.min(minX, r[0]); minY = Math.min(minY, r[1]); minZ = Math.min(minZ, r[2]);
            maxX = Math.max(maxX, r[0]); maxY = Math.max(maxY, r[1]); maxZ = Math.max(maxZ, r[2]);
        }
        return (float) distance(x, y, z, minX, minY, minZ, maxX, maxY, maxZ);
    }

    Verdict attack(InventoryTransactionPacket t, PlayerState s, boolean started) {
        if (t.getTransactionType() != InventoryTransactionType.ITEM_USE_ON_ENTITY || t.getActionType() > 1) return Verdict.PASS;
        boolean interact = t.getActionType() == 0;
        Tracked e = entities.get(t.getRuntimeEntityId());
        GameMode mode = session.getGameMode();
        if (e == null || !started || !haveTick || e.riding || "minecraft:ender_dragon".equals(e.type) || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return Verdict.PASS;
        attacks++;
        Lerp a = e.late, b = e.early;
        float minX = Math.min(min(a.x, a.px, b.x, b.px), Math.min(a.tx, b.tx)), maxX = Math.max(max(a.x, a.px, b.x, b.px), Math.max(a.tx, b.tx));
        float minY = Math.min(min(a.y, a.py, b.y, b.py), Math.min(a.ty, b.ty)), maxY = Math.max(max(a.y, a.py, b.y, b.py), Math.max(a.ty, b.ty));
        float minZ = Math.min(min(a.z, a.pz, b.z, b.pz), Math.min(a.tz, b.tz)), maxZ = Math.max(max(a.z, a.pz, b.z, b.pz), Math.max(a.tz, b.tz));
        float grow = GROW * e.scale, hw = e.width * e.scale / 2 + grow;
        float bx0 = minX - hw, bx1 = maxX + hw, by0 = minY - grow, by1 = maxY + e.height * e.scale + grow, bz0 = minZ - hw, bz1 = maxZ + hw;
        float[][] eyePoints = eyePath(s);
        double best = Double.MAX_VALUE;
        for (float[] ep : eyePoints) best = Math.min(best, distance(ep[0], ep[1], ep[2], bx0, by0, bz0, bx1, by1, bz1));
        boolean far = best > MAX_REACH;
        float[] box = {bx0, by0, bz0, bx1, by1, bz1};
        double miss = aimJudged ? arcMiss(eyePoints, prevYaw, prevPitch, yaw, pitch, box) : 0;
        if (PandaGeyserAC.verbose) player.log((interact ? "interact" : "attack") + " %s id %d reach %.4f ray miss %.4f look %.2f %.2f / %.2f %.2f click %s player %s region %.3f..%.3f %.3f..%.3f %.3f..%.3f pending %d early %d%s",
                e.type, t.getRuntimeEntityId(), best, miss, pitch, yaw, prevPitch, prevYaw, t.getClickPosition(), t.getPlayerPosition(),
                minX, maxX, minY, maxY, minZ, maxZ, e.pending.size(), e.earlyCount, far ? " FAR" : miss > AIM_MARGIN ? " HELD" : "");
        if (far) {
            flags++;
            PandaGeyserAC.log().info(String.format("[%s] %s reach %.3f > %.2f on %s (%d of %d attacks and interactions)%s", player.name(), interact ? "interaction" : "hit", best, MAX_REACH, e.type, flags, attacks, PandaGeyserAC.enforce ? ", cancelled" : ""));
            return PandaGeyserAC.enforce ? Verdict.REFUSE : Verdict.PASS;
        }
        boolean blocked = aimJudged && miss <= AIM_MARGIN && Sight.look(eyePoints, prevYaw, prevPitch, yaw, pitch, box, player.sight) == Sight.Result.BLOCKED;
        if (miss <= AIM_MARGIN && !blocked) return Verdict.PASS;
        heldBox = box; heldEyes = eyePoints; heldType = e.type; heldMiss = miss; heldBlocked = blocked;
        return Verdict.HOLD;
    }

    float[][] eyePath(PlayerState s) {
        float[] eyes = lowPose(s) || prevLow ? new float[] {PlayerState.EYE_HEIGHT, EYE_PRONE}
                : s.crouching || s.wasSneaking ? new float[] {PlayerState.EYE_HEIGHT, EYE_SNEAK} : new float[] {PlayerState.EYE_HEIGHT};
        final int n = 8;
        float[][] out = new float[2 * (n + 1) * eyes.length][];
        int k = 0;
        for (int seg = 0; seg < 2; seg++) {
            float sx = seg == 0 ? prevX : curX, sy = seg == 0 ? prevY : curY, sz = seg == 0 ? prevZ : curZ;
            float ex = seg == 0 ? curX : curX + s.vx, ey = seg == 0 ? curY : curY + s.vy, ez = seg == 0 ? curZ : curZ + s.vz;
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                for (float eye : eyes) out[k++] = new float[] {sx + (ex - sx) * f, sy + (ey - sy) * f + eye, sz + (ez - sz) * f};
            }
        }
        return out;
    }

    private static boolean lowPose(PlayerState s) {
        return s.swimming || s.gliding || s.spinTicks > 0;
    }

    float yaw() { return yaw; }
    float pitch() { return pitch; }
    float prevYaw() { return prevYaw; }
    float prevPitch() { return prevPitch; }
    boolean aimJudged() { return aimJudged && haveTick; }

    boolean heldAttack(float nextYaw, float nextPitch) {
        double next = arcMiss(heldEyes, yaw, pitch, nextYaw, nextPitch, heldBox);
        boolean nextBlocked = next <= AIM_MARGIN && Sight.look(heldEyes, yaw, pitch, nextYaw, nextPitch, heldBox, player.sight) == Sight.Result.BLOCKED;
        double miss = Math.min(heldMiss, next);
        boolean ok = next <= AIM_MARGIN && !nextBlocked;
        boolean wall = !ok && (heldBlocked || nextBlocked);
        if (PandaGeyserAC.verbose) player.log("held attack: turn to %.2f %.2f passes %.4f from the box%s, %s", nextPitch, nextYaw, miss, wall ? " behind a wall" : "", ok ? "passed" : "dropped");
        if (!ok) {
            aimFlags++;
            PandaGeyserAC.log().info(String.format("[%s] hit %s %s (%d of %d attacks)%s", player.name(), heldType,
                    wall ? "through a wall" : String.format("with the crosshair %.3f off its hitbox", miss), aimFlags, attacks, PandaGeyserAC.enforce ? ", hit cancelled" : ""));
        }
        heldBox = null; heldEyes = null;
        return ok || !PandaGeyserAC.enforce;
    }

    static double arcMiss(float[][] eyePoints, float y0, float p0, float y1, float p1, float[] b) {
        float dy = ((y1 - y0) % 360 + 540) % 360 - 180;
        double miss = Double.MAX_VALUE;
        for (int i = 0; i <= ARC_STEPS && miss > 0; i++) {
            float f = i / (float) ARC_STEPS, yaw = y0 + dy * f, pitch = p0 + (p1 - p0) * f;
            for (float[] e : eyePoints) {
                miss = Math.min(miss, rayMiss(e[0], e[1], e[2], yaw, pitch, b[0], b[1], b[2], b[3], b[4], b[5]));
                if (miss == 0) break;
            }
        }
        return miss;
    }

    static double rayMiss(float x, float y, float z, float yawDeg, float pitchDeg, float x0, float y0, float z0, float x1, float y1, float z1) {
        double yr = Math.toRadians(yawDeg), pr = Math.toRadians(pitchDeg);
        double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
        // the distance to the box is convex along the ray: golden section search
        double a = 0, b = 6, g = 0.6180339887;
        for (int i = 0; i < 60; i++) {
            double c = b - g * (b - a), d = a + g * (b - a);
            if (distance((float) (x + dx * c), (float) (y + dy * c), (float) (z + dz * c), x0, y0, z0, x1, y1, z1)
                    < distance((float) (x + dx * d), (float) (y + dy * d), (float) (z + dz * d), x0, y0, z0, x1, y1, z1)) b = d;
            else a = c;
        }
        double t = (a + b) / 2;
        return distance((float) (x + dx * t), (float) (y + dy * t), (float) (z + dz * t), x0, y0, z0, x1, y1, z1);
    }

    private static float min(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    static double distance(float px, float py, float pz, float x0, float y0, float z0, float x1, float y1, float z1) {
        double dx = Math.max(Math.max(x0 - px, 0), px - x1), dy = Math.max(Math.max(y0 - py, 0), py - y1), dz = Math.max(Math.max(z0 - pz, 0), pz - z1);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
