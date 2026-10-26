package me.TreeOfSelf.pandageyserac;

import me.TreeOfSelf.bedrocksim.Box;
import me.TreeOfSelf.bedrocksim.Engine;
import me.TreeOfSelf.bedrocksim.Input;
import me.TreeOfSelf.bedrocksim.PlayerState;
import me.TreeOfSelf.bedrocksim.VehicleEngine;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.data.MovementEffectType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.level.block.type.BlockState;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.MovementEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import net.kyori.adventure.key.Key;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.java.JavaRegistries;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Consumable;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.UseEffects;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Equippable;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.living.animal.horse.AbstractHorseEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.CamelEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.LlamaEntity;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;

public final class AcPlayer {
    // max distance between engine and client before a tick counts as off
    static final float THRESHOLD = 1.0E-3f;
    static final float TOUCH_THRESHOLD = THRESHOLD;
    static final Map<String, Float> SOLID_ENTITIES = Map.of("minecraft:boat", 0f, "minecraft:chest_boat", 0f, "minecraft:shulker", 1f);
    // how close a boat or shulker may come before the tick is not judged
    static final float SOLID_MARGIN = 0.6f;
    static final float UNJUDGED_MAX = 1f;
    static final float ACK_DELTA_MAX = 0.1f;
    static final int REWIND_HISTORY = 40;
    static final int FORGET_CHUNKS = 48;

    private final GeyserSession session;
    private final GeyserWorld world;
    private PlayerState s = new PlayerState();
    private int boostEither, boostInFlight;
    private int serverBoost;
    private int rocketWait = -1;
    private int clientBoostLeft;
    static final int CLIENT_BOOST_TICKS = 29;
    static final int ROCKET_WAIT = 40, BOOST_EDGE = 3;
    private int useEither, useInFlight;
    private Boolean serverUsing;
    private boolean ackedUsing;
    private int useTicks, riptideArmed, riptideWindow, riptideBoxLag;
    private Vector3f ackDelta;
    private int glideEither, glideInFlight;
    private Boolean serverGliding;
    private boolean refusedGlide;
    static final int RIPTIDE_WINDOW = 3;
    private long runtimeId = -1;
    private int linksInFlight;
    private boolean started;
    // knockback: Bedrock applies a motion stamped T before input tick T+1
    private final Map<Long, Vector3f> motions = new HashMap<>();
    private final Map<Long, float[]> solidEntities = new ConcurrentHashMap<>();
    private final Map<Long, Long> solidUnique = new ConcurrentHashMap<>();
    private final Deque<Vector3f> teleports = new ConcurrentLinkedDeque<>();
    private volatile boolean loadingDimension;
    private List<ClientWorld.Change> lateBlocks = List.of();
    private long swiftSneakSeen = Long.MIN_VALUE / 2;
    private static final int SWIFT_SNEAK_GRACE = 20;
    private static final boolean DEBUG_BLOCKS = Boolean.getBoolean("pandageyserac.debugblocks");
    private static final float PLACEHOLDER_Y = 30000f;
    private Vector3f nextMotion;
    private final Deque<PendingMotion> motionsInFlight = new ConcurrentLinkedDeque<>();
    private static final class PendingMotion { final Vector3f v; volatile boolean early; PendingMotion(Vector3f v) { this.v = v; } }
    private Vector3f glideTo, glideFrom, lastClientEye;
    private int glideStep = GLIDE_STEPS;
    private static final int GLIDE_STEPS = 3;
    private long lastTeleportAck = Long.MIN_VALUE / 2;
    private static final int FROZEN_AFTER_TELEPORT = 40;
    private static final int SINK_AFTER_TELEPORT = 10;
    private long ticks, mismatches, setbacks, groundSpoofs, timerFlags, badInputs, longMoves, sprintStops;
    // timer: client time may never run ahead of real time
    static final long TICK_NANOS = 50_000_000L, CLOCK_DRIFT = 300_000_000L;
    private long timerBalance = Long.MIN_VALUE, clockFloor = Long.MIN_VALUE, firstInput;
    // offsets add up and clean ticks decay them; setback at MAX_ADVANTAGE or one tick past IMMEDIATE_SETBACK
    static final float MAX_ADVANTAGE = 1f, IMMEDIATE_SETBACK = 0.5f, MAX_ADVANTAGE_CEILING = 4f, ADVANTAGE_DECAY = 0.999f;
    private float advantage;
    static final float KNOCKBACK_SETBACK = 0.1f;
    static final int KNOCKBACK_STRICT_TICKS = 3;
    private int knockbackTicks;
    static final int WAKE_TICKS = 10;
    private int wakeTicks;
    private boolean wasSleeping;
    private Vector3f bedEye;
    private boolean awaitingCorrection;
    private float lastPitch, lastYaw;
    private Object lastMode, lastModel;

    boolean touch() { return lastMode == InputMode.TOUCH; }
    private BedrockServerSession bedrock;
    private PlayerState vs;
    private long vsId = -1;
    private Boolean mountStandingSent;
    private boolean mountStanding;
    private int standingEither;
    private float boatDx, boatDy, boatDz;
    private final Map<Long, float[]> vehicleAttributes = new ConcurrentHashMap<>();
    private Vector3f vehicleTeleport, vehicleMotion;
    private long vehicleMismatches;
    private final Reach reach;
    final Sight.Blocks sight;
    private final FastBreak fastBreak;
    private final BlockAim blockAim;
    private final Inventory inventory = new Inventory(this);
    private boolean heldClick;

    AcPlayer(GeyserSession session) {
        this.session = session;
        this.reach = new Reach(this, session);
        this.fastBreak = new FastBreak(this, session);
        this.blockAim = new BlockAim(this, session, reach);
        this.bedrock = PacketHook.bedrockSession(session);
        this.world = new GeyserWorld(new ClientWorld(session.getBlockMappings()));
        this.sight = (x, y, z) -> {
            int id = world.client.bedrockAt(x, y, z, 0);
            return id >= 0 && id != ClientWorld.UNCERTAIN && world.blocksSight(id);
        };
        s.attrMode = true;
    }

    String name() {
        return session.bedrockUsername();
    }

    Runnable onSend(BedrockPacket packet) {
        Runnable r = reach.onSend(packet, runtimeId, lastPingId), m = onSendMovement(packet), i = inventory.onSend(packet);
        if (i != null) { Runnable rm = r == null ? m : m == null ? r : () -> { r.run(); m.run(); }; return rm == null ? i : () -> { rm.run(); i.run(); }; }
        return r == null ? m : m == null ? r : () -> { r.run(); m.run(); };
    }

    private Runnable onSendMovement(BedrockPacket packet) {
        if (packet instanceof CorrectPlayerMovePredictionPacket) {
            return () -> awaitingCorrection = false;
        }
        if (packet instanceof StartGamePacket p) {
            // rewind setbacks need server-authoritative movement with rewind
            p.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER_WITH_REWIND);
            p.setRewindHistorySize(REWIND_HISTORY);
            runtimeId = p.getRuntimeEntityId();
            world.client.dimension(p.getDimensionId());
            reset(p.getPlayerPosition());
            return null;
        }
        if (packet instanceof LevelChunkPacket p) {
            if (DEBUG_BLOCKS) log("chunk sent %d %d subchunks %d request %b caching %b", p.getChunkX(), p.getChunkZ(), p.getSubChunksLength(), p.isRequestSubChunks(), p.isCachingEnabled());
            return world.client.decode(p);
        }
        if (packet instanceof UpdateBlockPacket p) {
            Vector3i pos = p.getBlockPosition();
            int layer = p.getDataLayer(), id = p.getDefinition().getRuntimeId();
            return () -> {
                world.client.setBlock(pos, layer, id);
                if (DEBUG_BLOCKS) log("block update acked %s layer %d id %d", pos, layer, id);
                if (layer == 0 && !world.client.isAir(id)) fastBreak.restored(pos);
            };
        }
        if (packet instanceof UpdateSubChunkBlocksPacket p) {
            List<BlockChangeEntry> standard = List.copyOf(p.getStandardBlocks()), extra = List.copyOf(p.getExtraBlocks());
            return () -> {
                if (DEBUG_BLOCKS) log("sub chunk update acked %d standard %d extra, first %s", standard.size(), extra.size(), standard.isEmpty() ? null : standard.get(0).getPosition());
                for (BlockChangeEntry e : standard) world.client.setBlock(e.getPosition(), 0, e.getDefinition().getRuntimeId());
                for (BlockChangeEntry e : extra) world.client.setBlock(e.getPosition(), 1, e.getDefinition().getRuntimeId());
                if (DEBUG_BLOCKS && !standard.isEmpty()) { Vector3i q = standard.get(0).getPosition(); log("readback %s sent %d now %d/%d", q, standard.get(0).getDefinition().getRuntimeId(), world.client.bedrockAt(q.getX(), q.getY(), q.getZ(), 0), world.client.bedrockAt(q.getX(), q.getY(), q.getZ(), 1)); }
            };
        }
        if (packet instanceof ChangeDimensionPacket p) {
            int dim = p.getDimension();
            loadingDimension = true;
            return () -> { world.client.dimension(dim); solidEntities.clear(); solidUnique.clear(); };
        }
        if (packet instanceof AddEntityPacket p && p.getAttributes() != null && !p.getAttributes().isEmpty()) {
            Runnable ack = vehicleAttributesAck(p.getRuntimeEntityId(), p.getAttributes());
            if (!SOLID_ENTITIES.containsKey(p.getIdentifier())) return ack;
            long id = p.getRuntimeEntityId(), unique = p.getUniqueEntityId();
            float[] e = {p.getPosition().getX(), p.getPosition().getY(), p.getPosition().getZ(), SOLID_ENTITIES.get(p.getIdentifier()),
                    p.getPosition().getX(), p.getPosition().getY(), p.getPosition().getZ()};
            return () -> { if (ack != null) ack.run(); solidEntities.put(id, e); solidUnique.put(unique, id); };
        }
        if (packet instanceof UpdateAttributesPacket p && p.getRuntimeEntityId() != runtimeId) return vehicleAttributesAck(p.getRuntimeEntityId(), p.getAttributes());
        if (packet instanceof MoveEntityAbsolutePacket p && p.getRuntimeEntityId() == vsId && p.isTeleported()) {
            Vector3f pos = p.getPosition();
            log("vehicle teleport sent %s", pos);
            return () -> { vehicleTeleport = pos; awaitingCorrection = false; log("vehicle teleport acked %s", pos); };
        }
        if (packet instanceof SetEntityMotionPacket p && p.getRuntimeEntityId() == vsId) {
            Vector3f motion = p.getMotion();
            log("vehicle motion sent %s", motion);
            return () -> { vehicleMotion = motion; log("vehicle motion acked %s", motion); };
        }
        if (packet instanceof AddEntityPacket p && SOLID_ENTITIES.containsKey(p.getIdentifier())) {
            long id = p.getRuntimeEntityId(), unique = p.getUniqueEntityId();
            float[] e = {p.getPosition().getX(), p.getPosition().getY(), p.getPosition().getZ(), SOLID_ENTITIES.get(p.getIdentifier()),
                    p.getPosition().getX(), p.getPosition().getY(), p.getPosition().getZ()};
            return () -> { solidEntities.put(id, e); solidUnique.put(unique, id); };
        }
        if (packet instanceof MoveEntityAbsolutePacket p && solidEntities.containsKey(p.getRuntimeEntityId())) {
            long id = p.getRuntimeEntityId();
            Vector3f pos = p.getPosition();
            return () -> { float[] e = solidEntities.get(id); if (e != null) { keepPrevious(e); e[0] = pos.getX(); e[1] = pos.getY(); e[2] = pos.getZ(); } };
        }
        if (packet instanceof MoveEntityDeltaPacket p && solidEntities.containsKey(p.getRuntimeEntityId())) {
            long id = p.getRuntimeEntityId();
            Set<MoveEntityDeltaPacket.Flag> fl = p.getFlags();
            boolean hx = fl.contains(MoveEntityDeltaPacket.Flag.HAS_X), hy = fl.contains(MoveEntityDeltaPacket.Flag.HAS_Y), hz = fl.contains(MoveEntityDeltaPacket.Flag.HAS_Z);
            float x = p.getX(), y = p.getY(), z = p.getZ();
            return () -> { float[] e = solidEntities.get(id); if (e != null) { keepPrevious(e); if (hx) e[0] = x; if (hy) e[1] = y; if (hz) e[2] = z; } };
        }
        if (packet instanceof RemoveEntityPacket p && solidUnique.containsKey(p.getUniqueEntityId())) {
            long unique = p.getUniqueEntityId();
            return () -> { Long id = solidUnique.remove(unique); if (id != null) solidEntities.remove(id); };
        }
        if (packet instanceof SetEntityLinkPacket p && p.getEntityLink().getTo() == runtimeId) {
            boolean remove = p.getEntityLink().getType() == EntityLinkData.Type.REMOVE;
            log("entity link sent %s", p.getEntityLink().getType());
            linksInFlight++;
            return () -> { linksInFlight--; log("entity link acked remove %b", remove); };
        }
        if (packet instanceof MovePlayerPacket p && p.getRuntimeEntityId() == runtimeId) {
            teleports.addLast(p.getPosition());
            log("teleport %s %s", p.getMode(), p.getPosition());
            if (p.getMode() != MovePlayerPacket.Mode.NORMAL) return null;
            Vector3f to = p.getPosition();
            return () -> { glideTo = to; glideFrom = lastClientEye; glideStep = 0; };
        }
        if (packet instanceof SetEntityMotionPacket p && p.getRuntimeEntityId() == runtimeId) {
            Vector3f motion = p.getMotion();
            long stamp = p.getTick();
            log("motion sent stamp %d %s", stamp, motion);
            if (stamp > 0) return () -> { motions.put(stamp + 1, motion); log("motion acked %s", motion); };
            PendingMotion pm = new PendingMotion(motion);
            motionsInFlight.addLast(pm);
            return () -> {
                motionsInFlight.remove(pm);
                if (pm.early) log("motion acked %s (taken early)", motion);
                else { nextMotion = motion; log("motion acked %s", motion); }
            };
        }
        if (packet instanceof MovementEffectPacket p && p.getEntityRuntimeId() == runtimeId && p.getEffectType() == MovementEffectType.GLIDE_BOOST) {
            int duration = p.getDuration();
            // an invalid tick makes the client apply it on arrival
            p.setTick(Integer.MIN_VALUE);
            log("glide boost sent duration %d", duration);
            boostInFlight++;
            return () -> {
                boostInFlight--;
                // a boost lasts duration/2 ticks; 0 ends it
                serverBoost = duration / 2;
                boostEither = BOOST_EDGE;
                if (duration > 0) rocketWait = -1;
                log("glide boost acked duration %d", duration);
            };
        }
        if (packet instanceof SetEntityDataPacket p && p.getRuntimeEntityId() == vsId && p.getMetadata().getFlags() != null) {
            boolean standing = Boolean.TRUE.equals(p.getMetadata().getFlags().get(EntityFlag.STANDING));
            if (mountStandingSent == null || mountStandingSent != standing) {
                mountStandingSent = standing;
                log("mount standing sent %b", standing);
                return () -> {
                    mountStanding = standing;
                    standingEither = 2;
                    log("mount standing acked %b", standing);
                };
            }
        }
        if (packet instanceof SetEntityDataPacket p && p.getRuntimeEntityId() == runtimeId && p.getMetadata().getFlags() != null) {
            boolean using = Boolean.TRUE.equals(p.getMetadata().getFlags().get(EntityFlag.USING_ITEM));
            boolean gliding = Boolean.TRUE.equals(p.getMetadata().getFlags().get(EntityFlag.GLIDING));
            Runnable acks = null;
            if (serverUsing == null || serverUsing != using) {
                serverUsing = using;
                log("using item sent %b", using);
                useInFlight++;
                acks = () -> {
                    useInFlight--;
                    s.usingItem = using;
                    ackedUsing = using;
                    useEither = BOOST_EDGE;
                    log("using item acked %b", using);
                };
            }
            if (serverGliding == null || serverGliding != gliding) {
                serverGliding = gliding;
                log("gliding sent %b", gliding);
                glideInFlight++;
                Runnable prev = acks;
                acks = () -> {
                    if (prev != null) prev.run();
                    glideInFlight--;
                    if (!gliding && s.gliding) { s.gliding = false; log("gliding acked false, glide ended"); }
                    glideEither = BOOST_EDGE;
                };
            } else if (gliding) {
                log("gliding re-sent true");
                glideInFlight++;
                Runnable prev = acks;
                acks = () -> {
                    if (prev != null) prev.run();
                    glideInFlight--;
                    if (!s.gliding && !s.onGround) { s.gliding = true; log("gliding re-sent true acked, glide resumed"); }
                    glideEither = BOOST_EDGE;
                };
            }
            return acks;
        }
        if (packet instanceof MobEffectPacket p && p.getRuntimeEntityId() == runtimeId) {
            // Bedrock Haste mines x1.2^level faster than Java, so it is shown lower
            if (p.getEffectId() == 3 && p.getEvent() != MobEffectPacket.Event.REMOVE) {
                int shown = FastBreak.shownHaste(p.getAmplifier() + 1);
                if (shown != p.getAmplifier() + 1) {
                    log("haste %d shown as %d", p.getAmplifier() + 1, shown);
                    if (shown == 0) { p.setEvent(MobEffectPacket.Event.REMOVE); p.setAmplifier(0); }
                    else p.setAmplifier(shown - 1);
                }
            }
            int amp = p.getEvent() == MobEffectPacket.Event.REMOVE ? -1 : p.getAmplifier();
            int id = p.getEffectId();
            log("effect sent %s id %d amp %d", p.getEvent(), id, p.getAmplifier());
            return () -> {
                switch (id) {
                    case 1 -> s.speedAmp = amp;
                    case 2 -> s.slownessAmp = amp;
                    case 8 -> s.jumpBoostAmp = amp;
                    case 24 -> s.levitationAmp = amp;
                    case 27 -> s.slowFallingAmp = amp;
                    case 15 -> s.blindnessAmp = amp;
                    default -> { }
                }
                log("effect acked id %d amp %d", id, amp);
            };
        }
        if (packet instanceof UpdateAttributesPacket p && p.getRuntimeEntityId() == runtimeId) {
            Runnable acks = null;
            for (AttributeData a : p.getAttributes()) {
                Runnable ack;
                if (a.getName().equals("minecraft:movement")) {
                    float def = a.getDefaultValue(), value = a.getValue();
                    log("attribute sent movement value %s default %s modifiers %s", value, def, a.getModifiers());
                    ack = () -> { s.serverMovementAttribute(def, value); log("attribute acked movement %s", value); };
                } else if (a.getName().equals("minecraft:player.hunger")) {
                    float food = a.getValue();
                    log("attribute sent food %s", food);
                    ack = () -> { s.food = food; log("attribute acked food %s", food); };
                } else continue;
                Runnable prev = acks;
                acks = prev == null ? ack : () -> { prev.run(); ack.run(); };
            }
            return acks;
        }
        return null;
    }

    private boolean timedOut;

    void timedOut() {
        if (timedOut || !PandaGeyserAC.enforce) return;
        timedOut = true;
        PandaGeyserAC.log().info(String.format("[%s] disconnected: a ping went unanswered for %d s", name(), PacketHook.PING_TIMEOUT / 1_000_000_000L));
        session.disconnect("Timed out");
    }

    long lastPingId;

    void onPingAcked(long id) {
        reach.onPingAcked(id);
    }

    // faces 0-5: down, up, north, south, west, east
    private static final Vector3i[] FACES = { Vector3i.from(0, -1, 0), Vector3i.from(0, 1, 0), Vector3i.from(0, 0, -1), Vector3i.from(0, 0, 1), Vector3i.from(-1, 0, 0), Vector3i.from(1, 0, 0) };
    // a placed block counts this many inputs before the server confirms it
    private static final int PREDICT_INPUTS = 40;
    private static final int CLOSED_ON_PLAYER_INPUTS = 20;

    private void closedOnPlayer() {
        if (lateBlocks.isEmpty()) return;
        me.TreeOfSelf.bedrocksim.Box b = s.box();
        for (ClientWorld.Change c : lateBlocks) {
            if (c.layer() != 0) continue;
            Vector3i pos = c.pos();
            for (me.TreeOfSelf.bedrocksim.Box k : world.collisionBoxes(pos.getX(), pos.getY(), pos.getZ()))
                if (k.minX() < b.maxX() && k.maxX() > b.minX() && k.minY() < b.maxY() && k.maxY() > b.minY() && k.minZ() < b.maxZ() && k.maxZ() > b.minZ()) {
                    world.client.predict(pos, ClientWorld.UNCERTAIN, (int) ticks + CLOSED_ON_PLAYER_INPUTS);
                    log("tick %d block at %s closed into the player: not judged for %d inputs", (int) ticks, pos, CLOSED_ON_PLAYER_INPUTS);
                    break;
                }
        }
    }

    private void predictPlacement(Vector3i clicked, int face, ItemData item, ItemUseTransaction.PredictedResult result) {
        if (item == null || face < 0 || face > 5 || result == ItemUseTransaction.PredictedResult.FAILURE) return;
        var def = session.getItemMappings().getMapping(item).getBedrockBlockDefinition();
        if (def == null) return;
        Vector3i at = clicked.add(FACES[face]);
        int there = world.client.bedrockAt(at.getX(), at.getY(), at.getZ(), 0);
        if (there < 0 || !world.client.isAir(there) && !world.client.isWater(there)) return;
        boolean full = world.isFullCube(def.getRuntimeId());
        world.client.predict(at, full ? def.getRuntimeId() : ClientWorld.UNCERTAIN, (int) ticks + PREDICT_INPUTS);
        log("placed %s at %s (%s)", BlockState.of(world.client.javaId(def.getRuntimeId())).block().javaIdentifier().value(), at, full ? "full block" : "shape unknown");
    }

    void onClock(long pingSentNanos) {
        clockFloor = Math.max(clockFloor, pingSentNanos - CLOCK_DRIFT);
    }

    Reach.Verdict refuse(BedrockPacket packet) {
        if (!inventory.allow(packet)) return Reach.Verdict.REFUSE;
        if (!(packet instanceof InventoryTransactionPacket t)) return Reach.Verdict.PASS;
        String bad = t.getTransactionType() == InventoryTransactionType.ITEM_USE_ON_ENTITY && t.getRuntimeEntityId() == runtimeId ? "targets the player itself"
                : t.getTransactionType() == InventoryTransactionType.ITEM_USE && t.getActionType() == 0 && (t.getBlockFace() < 0 || t.getBlockFace() > 5) ? "block face " + t.getBlockFace() : null;
        if (bad != null) {
            badInputs++;
            PandaGeyserAC.log().info(String.format("[%s] bad packet refused: %s action %d %s (%d times)%s", name(), t.getTransactionType(), t.getActionType(), bad, badInputs, PandaGeyserAC.enforce ? "" : " (log only)"));
            if (PandaGeyserAC.enforce) return Reach.Verdict.REFUSE;
        }
        if (t.getTransactionType() == InventoryTransactionType.ITEM_USE_ON_ENTITY && started && usingItemConfirmed()) {
            PandaGeyserAC.log().info(String.format("[%s] %s refused: while using an item (%d use ticks)%s", name(), t.getActionType() == 1 ? "attack" : "entity interaction", useTicks,
                    PandaGeyserAC.enforce ? "" : " (log only)"));
            if (PandaGeyserAC.enforce) return Reach.Verdict.REFUSE;
        }
        Reach.Verdict v = blockAim.click(t, s, started);
        if (v != Reach.Verdict.PASS) { heldClick = v == Reach.Verdict.HOLD; return v; }
        v = reach.attack(t, s, started);
        if (v == Reach.Verdict.HOLD) heldClick = false;
        return v;
    }

    boolean heldAttack(PlayerAuthInputPacket p) {
        float yaw = p.getRotation().getY(), pitch = p.getRotation().getX();
        return heldClick ? blockAim.heldClick(yaw, pitch) : reach.heldAttack(yaw, pitch);
    }

    boolean heldAttackTimedOut() {
        return heldClick ? blockAim.heldClick(lastYaw, lastPitch) : reach.heldAttack(lastYaw, lastPitch);
    }

    void onReceive(BedrockPacket packet) {
        inventory.onReceive(packet);
        if (packet instanceof InventoryTransactionPacket t && t.getTransactionType() == InventoryTransactionType.ITEM_USE && t.getActionType() == 1) { rocketUse(); useEither = BOOST_EDGE; }
        if (packet instanceof InventoryTransactionPacket t && t.getTransactionType() == InventoryTransactionType.ITEM_RELEASE && s.usingItem) { riptideRelease(); s.usingItem = false; useEither = BOOST_EDGE; log("item released"); }
        if (packet instanceof MobEquipmentPacket e && e.getRuntimeEntityId() == runtimeId && s.usingItem) { s.usingItem = false; useEither = BOOST_EDGE; log("hotbar switch ends item use"); }
        if (packet instanceof InventoryTransactionPacket t && t.getTransactionType() == InventoryTransactionType.ITEM_USE && t.getActionType() == 0)
            predictPlacement(t.getBlockPosition(), t.getBlockFace(), t.getItemInHand(), t.getClientInteractPrediction());
        if (packet instanceof InventoryTransactionPacket t && t.getTransactionType() == InventoryTransactionType.ITEM_USE && t.getActionType() != 1)
            log("block use %d pos %s face %d click %s player %s trigger %s prediction %s | eye %.4f %.4f %.4f look %.2f %.2f %s %s", t.getActionType(), t.getBlockPosition(), t.getBlockFace(),
                    t.getClickPosition(), t.getPlayerPosition(), t.getTriggerType(), t.getClientInteractPrediction(), s.x, s.y + PlayerState.EYE_HEIGHT, s.z, lastPitch, lastYaw, lastMode, lastModel);
        if (!(packet instanceof PlayerAuthInputPacket p)) return;
        Vector2f rawMove = p.getMotion();
        longMove(p);
        lastPitch = p.getRotation().getX(); lastYaw = p.getRotation().getY(); lastMode = p.getInputMode(); lastModel = p.getInputInteractionModel();
        if (p.getPlayerActions() != null) for (var a : p.getPlayerActions())
            log("tick %d block action %s pos %s face %d | eye %.4f %.4f %.4f look %.2f %.2f %s %s", p.getTick(), a.getAction(), a.getBlockPosition(), a.getFace(), s.x, s.y + PlayerState.EYE_HEIGHT, s.z, lastPitch, lastYaw, lastMode, lastModel);
        if (p.getItemUseTransaction() != null && p.getItemUseTransaction().getActionType() != 1) {
            var t = p.getItemUseTransaction();
            if (t.getActionType() == 0) predictPlacement(t.getBlockPosition(), t.getBlockFace(), t.getItemInHand(), t.getClientInteractPrediction());
            log("tick %d input block use %d pos %s face %d click %s player %s trigger %s prediction %s | eye %.4f %.4f %.4f look %.2f %.2f %s %s", p.getTick(), t.getActionType(), t.getBlockPosition(), t.getBlockFace(),
                    t.getClickPosition(), t.getPlayerPosition(), t.getTriggerType(), t.getClientInteractPrediction(), s.x, s.y + PlayerState.EYE_HEIGHT, s.z, lastPitch, lastYaw, lastMode, lastModel);
        }
        reach.clientTick(s, lastYaw, lastPitch, lastMode == InputMode.MOUSE || lastMode == InputMode.GAMEPAD);
        blockAim.input(p, s, started);
        fastBreak.input(p);
        if (p.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS) && p.getPlayerActions() != null)
            for (var a : p.getPlayerActions())
                if (a.getAction() == PlayerActionType.BLOCK_PREDICT_DESTROY) world.client.predictAir(a.getBlockPosition(), (int) ticks + PREDICT_INPUTS);
        world.client.expirePredicted((int) ticks);
        lateBlocks = world.client.takeRecent();
        closedOnPlayer();
        if (p.getItemUseTransaction() != null && p.getItemUseTransaction().getActionType() == 1) rocketUse();
        Set<PlayerAuthInputData> f = p.getInputData();
        Vector3f eye = p.getPosition();
        ticks++;
        long now = System.nanoTime();
        if (timerBalance == Long.MIN_VALUE) { timerBalance = now - CLOCK_DRIFT; firstInput = now; }
        long balanceBefore = timerBalance;
        timerBalance = Math.max(timerBalance, clockFloor) + TICK_NANOS;
        boolean fast = timerBalance > now;
        if (fast) {
            timerBalance -= TICK_NANOS;
            timerFlags++;
            PandaGeyserAC.log().info(String.format("[%s] timer: input at tick %d came %.1f ms early (%d times; input %d, %.1f ms after the first, balance before %.1f ms, clock floor %.1f ms)", name(), p.getTick(), (timerBalance + TICK_NANOS - now) / 1e6, timerFlags,
                    ticks, (now - firstInput) / 1e6, (balanceBefore - now) / 1e6, clockFloor == Long.MIN_VALUE ? Double.NaN : (clockFloor - now) / 1e6));
        }
        // NaN, infinity or a pitch past 90 can't come from a real client
        boolean bad = !finite(rawMove.getX(), rawMove.getY(), p.getRotation().getX(), p.getRotation().getY(),
                eye.getX(), eye.getY(), eye.getZ(), p.getDelta().getX(), p.getDelta().getY(), p.getDelta().getZ())
                || Math.abs(p.getRotation().getX()) > 90.001f;
        if (bad) {
            badInputs++;
            PandaGeyserAC.log().info(String.format("[%s] bad input at tick %d: move %s rot %s pos %s delta %s (%d times)", name(), p.getTick(), p.getMotion(), p.getRotation(), eye, p.getDelta(), badInputs));
            if (!started || exempt()) return;
            if (awaitingCorrection || !PandaGeyserAC.enforce) {
                if (PandaGeyserAC.enforce) forwardEngine(p);
                return;
            }
            setback(p.getTick());
            forwardEngine(p);
            return;
        }
        if (loadingDimension && started && eye.getY() > PLACEHOLDER_Y) {
            if (PandaGeyserAC.verbose) log("tick %d loading dimension", p.getTick());
            if (PandaGeyserAC.enforce) forwardEngine(p);
            return;
        }
        Vector3f m = motions.remove(p.getTick());
        if (nextMotion != null) { m = nextMotion; nextMotion = null; }
        if (m != null) { s.vx = m.getX(); s.vy = m.getY(); s.vz = m.getZ(); knockbackTicks = KNOCKBACK_STRICT_TICKS; }
        else if (knockbackTicks > 0) knockbackTicks--;
        boolean sleeping = session.getPlayerEntity().getFlag(EntityFlag.SLEEPING);
        if (sleeping) bedEye = eye;
        if (wasSleeping && !sleeping) wakeTicks = WAKE_TICKS;
        wasSleeping = sleeping;
        if (wakeTicks > 0) {
            wakeTicks--;
            if (bedEye == null || Math.abs(eye.getX() - bedEye.getX()) > 2 || Math.abs(eye.getZ() - bedEye.getZ()) > 2
                    || eye.getY() - bedEye.getY() > 2) wakeTicks = 0;
        }
        if (started && exempt()) {
            if (PandaGeyserAC.verbose) log("tick %d exempt (%s)", p.getTick(), exemptReason());
            logRiding(p, f);
            if (PandaGeyserAC.enforce && session.getPlayerEntity().getVehicle() instanceof CamelEntity) f.remove(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
            if (PandaGeyserAC.enforce && linksInFlight > 0) f.remove(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
            int kind = vehicleKind(f);
            Engine.setEntities(List.of());
            if (kind != 0) vehicleTick(p, f, kind, fast);
            else vs = null;
            follow(p);
            s.onGround = f.contains(PlayerAuthInputData.VERTICAL_COLLISION) && p.getDelta().getY() <= 0;
            s.sprinting = f.contains(PlayerAuthInputData.SPRINTING);
            s.gliding = false;
            s.swimming = false;
            if (f.contains(PlayerAuthInputData.HANDLE_TELEPORT)) { teleports.clear(); loadingDimension = false; }
            return;
        }
        vs = null;
        if (!started) {
            log("tick %d %s first, reset to %s", p.getTick(), f, eye);
            if (PandaGeyserAC.verbose) {
                int bx = (int) Math.floor(eye.getX()), by = (int) Math.floor(eye.getY() - PlayerState.EYE_HEIGHT), bz = (int) Math.floor(eye.getZ());
                log("blocks at the feet: below %s at %s loaded %b raw %d %d", world.type(bx, by - 1, bz).name(), world.type(bx, by, bz).name(), world.client.isLoaded(bx, bz), world.client.bedrockAt(bx, by - 1, bz, 0), world.client.bedrockAt(bx, by, bz, 0));
            }
            reset(eye);
            started = true;
            Entity v = session.getPlayerEntity().getVehicle();
            if (v != null && f.contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE) && PandaGeyserAC.enforce) {
                Vector3f at = v.position();
                p.setPosition(Vector3f.from(at.getX(), at.getY() + v.getOffset(), at.getZ()));
            }
            // the client skips the move on the tick that acknowledges a teleport
            if (f.contains(PlayerAuthInputData.HANDLE_TELEPORT)) { teleports.clear(); loadingDimension = false; Engine.teleportAckTick(s, world); }
            return;
        }

        if (fast && PandaGeyserAC.enforce && !awaitingCorrection) {
            setback(p.getTick());
            forwardEngine(p);
            return;
        }
        world.touchedMissingChunk = false;
        world.touchedUncertain = false;
        Engine.setEntities(entityBoxes(false));
        entityAlts = null;
        entityCap = null;
        float startX = s.x, startY = s.y, startZ = s.z;
        if (ticks % 200 == 0) world.client.forget((int) Math.floor(s.x) >> 4, (int) Math.floor(s.z) >> 4, FORGET_CHUNKS);
        if (f.contains(PlayerAuthInputData.HANDLE_TELEPORT) && !teleports.isEmpty()) {
            lastTeleportAck = ticks;
            loadingDimension = false;
            Vector3f target = null;
            for (Vector3f t : teleports) if (matchesState(at(t), eye)) { target = t; break; }
            if (target == null) target = teleports.peekFirst();
            while (teleports.pollFirst() != target) { }
            reset(target);
            s.onGround = false;
            if (s.jumpDelay > 0) s.jumpDelay--;
            if (m != null) { s.vx = m.getX(); s.vy = m.getY(); s.vz = m.getZ(); }
            Vector3f d = p.getDelta();
            ackDelta = m == null && d.length() > 0 && d.length() <= ACK_DELTA_MAX ? d : null;
            Engine.teleportAckTick(s, world);
        } else {
            boolean glideStart = f.contains(PlayerAuthInputData.START_GLIDING);
            if (glideStart && !s.gliding && !hasGlider()) {
                glideStart = false;
                refusedGlide = true;
                PandaGeyserAC.log().info(String.format("[%s] glide start without a usable glider at tick %d", name(), p.getTick()));
            }
            Vector2f move = footMove(p, f);
            Input in = new Input(move.getX(), move.getY(), p.getRotation().getY(), p.getRotation().getX(),
                    f.contains(PlayerAuthInputData.JUMPING), f.contains(PlayerAuthInputData.SPRINT_DOWN),
                    f.contains(PlayerAuthInputData.START_SPRINTING), f.contains(PlayerAuthInputData.STOP_SPRINTING), f.contains(PlayerAuthInputData.SNEAKING),
                    f.contains(PlayerAuthInputData.START_SWIMMING), f.contains(PlayerAuthInputData.STOP_SWIMMING),
                    glideStart, f.contains(PlayerAuthInputData.STOP_GLIDING),
                    f.contains(PlayerAuthInputData.ASCEND_BLOCK), f.contains(PlayerAuthInputData.DESCEND_BLOCK));
            s.glideBoost = serverBoost;
            s.depthStrider = enchantLevel(session.getPlayerInventory().getItem(8), "depth_strider");
            int swift = enchantLevel(session.getPlayerInventory().getItem(7), "swift_sneak");
            if (swift > 0) swiftSneakSeen = ticks;
            boolean swiftRecent = swift > 0 || ticks - swiftSneakSeen <= SWIFT_SNEAK_GRACE;
            float sentLen = p.getMotion().length();
            if (p.getInputMode() == InputMode.MOUSE && f.contains(PlayerAuthInputData.SNEAKING) && sentLen > 0)
                s.swiftSneak = swiftRecent && sentLen > 0.35f ? 1 : 0;
            else s.swiftSneak = swift;
            s.soulSpeed = enchantLevel(session.getPlayerInventory().getItem(8), "soul_speed");
            s.leatherBoots = session.getPlayerInventory().getItem(8).asItem() == Items.LEATHER_BOOTS;
            s.freezeImmune = false;
            for (int slot = 5; slot <= 8; slot++) {
                Item armor = session.getPlayerInventory().getItem(slot).asItem();
                if (armor == Items.LEATHER_HELMET || armor == Items.LEATHER_CHESTPLATE || armor == Items.LEATHER_LEGGINGS || armor == Items.LEATHER_BOOTS) s.freezeImmune = true;
            }
            UseEffects ue = session.getPlayerInventory().getItemInHand().getComponent(DataComponentTypes.USE_EFFECTS);
            s.useNoSlow = ue != null && ue.speedMultiplier() >= 1f && ue.canSprint();
            if (ackedUsing && !s.usingItem && useEither == 0 && useInFlight == 0) { s.usingItem = true; log("tick %d server says using item", p.getTick()); }
            if (f.contains(PlayerAuthInputData.START_USING_ITEM) && !s.usingItem) { s.usingItem = true; if (!ackedUsing) useEither = BOOST_EDGE; log("tick %d start using item", p.getTick()); }
            if (f.contains(PlayerAuthInputData.START_SPIN_ATTACK)) {
                if (riptideArmed > 0) { s.riptide = riptideArmed; riptideBoxLag = 2; log("tick %d riptide %d", p.getTick(), riptideArmed); }
                else log("tick %d spin attack without a riptide release", p.getTick());
                riptideArmed = 0;
            }
            boolean boostOpen = boostEither > 0 || boostInFlight > 0 || rocketWait >= 0 || clientBoostLeft > 0, useOpen = useEither > 0 || useInFlight > 0,
                    glideOpen = glideEither > 0 || glideInFlight > 0;
            PlayerState before = boostOpen || useOpen || glideOpen ? s.copy() : null;
            PlayerState swimFlipAlt = s.swimming || s.inWater ? s.copy() : null;
            PlayerState levGlideAlt = s.gliding && s.levitationAmp >= 0 ? s.copy() : null;
            PlayerState lavaEdgeAlt = Engine.lavaEdge(s, world) ? s.copy() : null;
            PlayerState boxLagAlt = riptideBoxLag > 0 ? s.copy() : null;
            PlayerState ackDeltaAlt = null;
            if (ackDelta != null) {
                ackDeltaAlt = s.copy();
                ackDeltaAlt.vx = ackDelta.getX(); ackDeltaAlt.vy = ackDelta.getY(); ackDeltaAlt.vz = ackDelta.getZ();
                ackDelta = null;
            }
            PlayerState blockLateAlt = lateBlocks.isEmpty() ? null : s.copy();
            float moveLen = (float) Math.sqrt(move.getX() * move.getX() + move.getY() * move.getY());
            float sneakCut = Engine.sneakMultiplier(s);
            boolean analogCut = p.getInputMode() != InputMode.MOUSE && moveLen > 0 && moveLen <= sneakCut + 1e-4f;
            boolean padCut = analogCut && (in.sneaking() || f.contains(PlayerAuthInputData.STOP_SNEAKING));
            PlayerState padCutAlt = padCut ? s.copy() : null;
            Input sent = in;
            if (padCut)
                in = new Input(move.getX() / sneakCut, move.getY() / sneakCut, in.yaw(), in.pitch(), in.jumping(), in.sprintHeld(), in.startSprinting(), in.stopSprinting(),
                        in.sneaking(), in.startSwimming(), in.stopSwimming(), in.startGliding(), in.stopGliding(), in.ascend(), in.descend());
            boolean sprintRefused = in.startSprinting() && !s.sprinting && !Engine.maySprint(s);
            PlayerState motionAlt = motionsInFlight.isEmpty() ? null : s.copy();
            PlayerState noJumpAlt = in.jumping() && !f.contains(PlayerAuthInputData.START_JUMPING) ? s.copy() : null;
            PlayerState entityAlt = Engine.entities().isEmpty() ? null : s.copy();
            Engine.tick(s, in, world);
            if (noJumpAlt != null && s.jumpDelay > noJumpAlt.jumpDelay && !matches(eye)) {
                int jd = noJumpAlt.jumpDelay;
                noJumpAlt.jumpDelay = 2;
                Engine.tick(noJumpAlt, in, world);
                if (matchesState(noJumpAlt, eye)) { noJumpAlt.jumpDelay = 0; log("tick %d no jump yet (client's jump delay, engine's was %d)", p.getTick(), jd); s = noJumpAlt; }
            }
            if (boxLagAlt != null && !matches(eye)) {
                boxLagAlt.boxLag = true;
                Engine.tick(boxLagAlt, in, world);
                boxLagAlt.boxLag = false;
                if (matchesState(boxLagAlt, eye)) { log("tick %d riptide with last tick's box", p.getTick()); s = boxLagAlt; }
            }
            if (riptideBoxLag > 0) riptideBoxLag--;
            if (ackDeltaAlt != null && !matches(eye)) {
                Engine.tick(ackDeltaAlt, in, world);
                if (matchesState(ackDeltaAlt, eye)) { log("tick %d velocity from the teleport ack's delta", p.getTick()); s = ackDeltaAlt; }
            }
            if (motionAlt != null && !matches(eye)) {
                PendingMotion[] pending = motionsInFlight.toArray(new PendingMotion[0]);
                for (int i = pending.length - 1; i >= 0; i--) {
                    if (pending[i].early) continue;
                    PlayerState alt = motionAlt.copy();
                    alt.vx = pending[i].v.getX(); alt.vy = pending[i].v.getY(); alt.vz = pending[i].v.getZ();
                    Engine.tick(alt, in, world);
                    if (matchesState(alt, eye)) {
                        for (int j = 0; j <= i; j++) pending[j].early = true;
                        knockbackTicks = KNOCKBACK_STRICT_TICKS;
                        log("tick %d motion %s taken before its ack", p.getTick(), pending[i].v);
                        s = alt;
                        break;
                    }
                }
            }
            if (padCutAlt != null && !matches(eye)) {
                Engine.tick(padCutAlt, sent, world);
                if (matchesState(padCutAlt, eye)) { log("tick %d sneak move vector taken as sent", p.getTick()); s = padCutAlt; }
            }
            if (sprintRefused) {
                log("tick %d sprint start refused (food %s, blindness %d)", p.getTick(), s.food, s.blindnessAmp);
                if (PandaGeyserAC.enforce) f.remove(PlayerAuthInputData.START_SPRINTING);
            }
            if (!s.sprinting && session.isSprinting() && !f.contains(PlayerAuthInputData.STOP_SPRINTING) && !f.contains(PlayerAuthInputData.START_SPRINTING)) {
                sprintStops++;
                log("tick %d Java sprinting, engine not: stop sent (%d)", p.getTick(), sprintStops);
                if (PandaGeyserAC.enforce) f.add(PlayerAuthInputData.STOP_SPRINTING);
            }
            if (swimFlipAlt != null && !matches(eye)) {
                PlayerState pick = null, closest = null;
                String how = null, closestHow = null;
                float closestOff = dist(s, eye);
                outer:
                for (int hold = 0; hold <= (in.jumping() ? 1 : 0); hold++)
                    for (int swim = 0; swim <= 1; swim++)
                        for (int sprint = 0; sprint <= 1; sprint++) {
                            if (hold == 0 && swim == 0 && sprint == 0) continue;
                            PlayerState alt = swimFlipAlt.copy();
                            alt.swimHold = hold == 1;
                            if (swim == 1) alt.forceSwim = s.swimming ? 0 : 1;
                            if (sprint == 1) {
                                if (!alt.sprinting && !Engine.maySprint(alt)) continue;
                                alt.sprinting = !alt.sprinting;
                            }
                            Engine.tick(alt, in, world);
                            String h = (hold == 1 ? " hold" : "") + (swim == 1 ? " swim " + alt.swimming : "") + (sprint == 1 ? " sprint " + alt.sprinting : "");
                            if (matchesState(alt, eye) && Math.abs(alt.vy - p.getDelta().getY()) < 1e-4f) {
                                pick = alt;
                                how = h;
                                break outer;
                            }
                            float d = dist(alt, eye);
                            if (d < closestOff) { closest = alt; closestOff = d; closestHow = h; }
                        }
                if (pick != null) { log("tick %d in water:%s matches", p.getTick(), how); s = pick; }
                else if (closest != null) { log("tick %d in water:%s nearest (%.5f)", p.getTick(), closestHow, closestOff); s = closest; }
            }
            if (lavaEdgeAlt != null && !matches(eye)) {
                lavaEdgeAlt.lavaFlip = true;
                Engine.tick(lavaEdgeAlt, in, world);
                if (matchesState(lavaEdgeAlt, eye)) { log("tick %d other side of a lava edge", p.getTick()); s = lavaEdgeAlt; }
                else if (PandaGeyserAC.verbose) log("tick %d other side of a lava edge doesn't match either: %.6f %.6f %.6f v %.6f %.6f %.6f", p.getTick(), lavaEdgeAlt.x, lavaEdgeAlt.eyeY, lavaEdgeAlt.z, lavaEdgeAlt.vx, lavaEdgeAlt.vy, lavaEdgeAlt.vz);
            }
            if (blockLateAlt != null && !matches(eye)) {
                ClientWorld.setRecent(lateBlocks, true);
                try { Engine.tick(blockLateAlt, in, world); } finally { ClientWorld.setRecent(lateBlocks, false); }
                if (matchesState(blockLateAlt, eye)) { log("tick %d block change used a tick late (%d blocks)", p.getTick(), lateBlocks.size()); s = blockLateAlt; }
            }
            if (levGlideAlt != null && (!matches(eye) || velocityOff(s, p.getDelta()))) {
                levGlideAlt.glideHold = true;
                Engine.tick(levGlideAlt, in, world);
                if (matchesState(levGlideAlt, eye) && !velocityOff(levGlideAlt, p.getDelta())) { log("tick %d glided on through a new levitation", p.getTick()); s = levGlideAlt; }
            }
            if (before != null && !matches(eye)) {
                for (int flip = 1; flip <= 7; flip++) {
                    boolean fb = (flip & 1) != 0, fu = (flip & 2) != 0, fg = (flip & 4) != 0;
                    if (fb && !boostOpen || fu && !useOpen || fg && !glideOpen) continue;
                    PlayerState alt = before.copy();
                    if (fb) alt.glideBoost = before.glideBoost > 0 ? 0 : 1;
                    if (fu) alt.usingItem = !before.usingItem;
                    if (fg) { if (before.gliding) continue; alt.gliding = true; }
                    Engine.tick(alt, in, world);
                    if (matchesState(alt, eye)) {
                        log("tick %d%s%s%s matches", p.getTick(), fb ? " boost " + (alt.glideBoost > 0 ? "on" : "off") : "", fu ? " using " + alt.usingItem : "", fg ? " still gliding" : "");
                        s = alt;
                        break;
                    }
                }
            }
            if (entityAlt != null && !matches(eye)) {
                List<Box> current = Engine.entities();
                PlayerState free = entityAlt.copy(), prev = entityAlt.copy();
                try {
                    Engine.setEntities(List.of());
                    Engine.tick(free, in, world);
                    Engine.setEntities(entityBoxes(true));
                    Engine.tick(prev, in, world);
                } finally { Engine.setEntities(current); }
                if (matchesState(prev, eye)) { log("tick %d solid entities where they were / opened", p.getTick()); s = prev; }
                else if (matchesState(free, eye)) { log("tick %d no solid entity collision", p.getTick()); s = free; }
                else entityAlts = new PlayerState[]{free, prev};
            }
        }
        useTicks = s.usingItem ? useTicks + 1 : 0;
        if (riptideWindow > 0 && --riptideWindow == 0) riptideArmed = 0;
        if (serverBoost > 0) serverBoost--;
        s.glideBoost = serverBoost;
        if (boostEither > 0) boostEither--;
        if (clientBoostLeft > 0) clientBoostLeft--;
        if (useEither > 0) useEither--;
        if (glideEither > 0) glideEither--;
        if (rocketWait >= 0 && rocketWait-- == 0) log("no boost came for the rocket");

        float dx = eye.getX() - s.x, dy = eye.getY() - s.eyeY, dz = eye.getZ() - s.z;
        // past 1024 one float step is 1.2e-4
        float tol = Math.max(p.getInputMode() == InputMode.TOUCH ? TOUCH_THRESHOLD : THRESHOLD, 3 * Math.ulp(Math.max(Math.abs(eye.getX()), Math.abs(eye.getZ()))));
        // written so NaN counts as off
        boolean off = !(Math.abs(dx) <= tol && Math.abs(dy) <= tol && Math.abs(dz) <= tol);
        if (PandaGeyserAC.verbose)
            log("tick %d %s move %s rot %s client %s delta %s | engine %.6f %.6f %.6f v %.6f %.6f %.6f ground %b sprint %b jd %d%s",
                    p.getTick(), f, p.getMotion(), p.getRotation(), eye, p.getDelta(), s.x, s.eyeY, s.z, s.vx, s.vy, s.vz, s.onGround, s.sprinting, s.jumpDelay, off ? " OFF" : "");
        if (off && PandaGeyserAC.verbose) {
            StringBuilder b = new StringBuilder();
            int fy = (int) Math.floor(startY - 0.01f);
            for (int bx = (int) Math.floor(startX - 0.3f); bx <= (int) Math.floor(startX + 0.3f); bx++)
                for (int bz = (int) Math.floor(startZ - 0.3f); bz <= (int) Math.floor(startZ + 0.3f); bz++)
                    for (int by = fy - 1; by <= fy + 1; by++) {
                        var t = world.type(bx, by, bz);
                        if (!t.shapes().isEmpty()) b.append(String.format(" %d,%d,%d=%s%s", bx, by, bz, t.name(), t.shapes()));
                    }
            StringBuilder ents = new StringBuilder();
            for (Map.Entry<Long, float[]> en : solidEntities.entrySet()) ents.append(String.format(" %d=%.2f,%.2f,%.2f/%s", en.getKey(), en.getValue()[0], en.getValue()[1], en.getValue()[2], en.getValue()[3] == 0 ? "boat" : "shulker"));
            log("tick %d OFF from %.4f %.4f %.4f; under:%s; solid entities:%s; vsId %d vs %b vehicle %s", p.getTick(), startX, startY, startZ, b, ents, vsId, vs != null, session.getPlayerEntity().getVehicle());
        }
        lastClientEye = eye;
        boolean near = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) <= UNJUDGED_MAX;
        boolean nearSolid = near && nearSolidEntity(startX, startY, startZ, eye);
        boolean frozen = off && near && ticks - lastTeleportAck <= FROZEN_AFTER_TELEPORT && eye.getX() == startX && eye.getZ() == startZ
                && Math.abs(eye.getY() - PlayerState.EYE_HEIGHT - startY) < 1e-4f && p.getDelta().getX() == 0 && p.getDelta().getZ() == 0
                && p.getDelta().getY() <= 0;
        boolean settling = off && near && ticks - lastTeleportAck <= SINK_AFTER_TELEPORT && eye.getX() == startX && eye.getZ() == startZ
                && eye.getY() - PlayerState.EYE_HEIGHT <= startY + 1e-4f;
        if (frozen || settling) {
            if (PandaGeyserAC.verbose) log("tick %d not judged (%s after a teleport)", p.getTick(), frozen ? "holding still" : "sinking straight down");
            follow(p);
            return;
        }
        if (glideStep < GLIDE_STEPS && glideFrom != null) {
            glideStep++;
            Vector3f at = glideFrom.add(glideTo.sub(glideFrom).mul(glideStep / (float) GLIDE_STEPS));
            if (off && at.distance(eye) <= 2e-3f) {
                if (PandaGeyserAC.verbose) log("tick %d gliding to a NORMAL move (%d/%d)", p.getTick(), glideStep, GLIDE_STEPS);
                follow(p);
                return;
            }
        }
        boolean unknownBlocks = near && (world.touchedMissingChunk || world.touchedUncertain);
        if (off && nearSolid && !unknownBlocks && !entityBound(p, startX, startY, startZ))
            log("tick %d beyond what a boat or shulker explains", p.getTick());
        else if (off && (unknownBlocks || nearSolid)) {
            off = false;
            if (PandaGeyserAC.verbose) log("tick %d not judged (%s)", p.getTick(), world.touchedMissingChunk ? "chunk not loaded on the client" : world.touchedUncertain ? "player-dependent block" : "boat or shulker, within bounds");
            follow(p);
            if (entityCap != null && !unknownBlocks) {
                float vh = hyp(s.vx, s.vz);
                if (vh > entityCap[0]) { s.vx *= entityCap[0] / vh; s.vz *= entityCap[0] / vh; }
                s.vy = Math.min(s.vy, entityCap[1]);
            }
            if (nearSolid) {
                s.onGround = f.contains(PlayerAuthInputData.VERTICAL_COLLISION) && p.getDelta().getY() <= 0;
                liftOntoJavaBoat(p);
            }
            return;
        }
        if (awaitingCorrection) {
            if (PandaGeyserAC.verbose) log("tick %d awaiting correction ack", p.getTick());
            if (PandaGeyserAC.enforce) forwardEngine(p);
            return;
        }
        if (!off) {
            if (nearSolid) liftOntoJavaBoat(p);
            refusedGlide = false;
            advantage *= ADVANTAGE_DECAY;
            groundTruth(p, !f.contains(PlayerAuthInputData.HANDLE_TELEPORT));
            return;
        }
        float offset = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        mismatches++;
        advantage = Math.min(advantage + offset, MAX_ADVANTAGE_CEILING);
        PandaGeyserAC.log().info(String.format("[%s] offset %.5f (%.5f %.5f %.5f) at tick %d, advantage %.3f (%d of %d ticks)", name(), offset, dx, dy, dz, p.getTick(), advantage, mismatches, ticks));
        if (PandaGeyserAC.enforce && (advantage >= MAX_ADVANTAGE || offset >= (knockbackTicks > 0 ? KNOCKBACK_SETBACK : IMMEDIATE_SETBACK) || refusedGlide)) {
            setback(p.getTick());
            forwardEngine(p);
            return;
        }
        follow(p);
    }

    private static float segmentDistance(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f ab = b.sub(a);
        float len2 = ab.lengthSquared();
        float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, p.sub(a).dot(ab) / len2));
        return p.distance(a.add(ab.mul(t)));
    }

    private void follow(PlayerAuthInputPacket p) {
        Vector3f eye = p.getPosition();
        s.x = eye.getX(); s.eyeY = eye.getY(); s.y = eye.getY() - PlayerState.EYE_HEIGHT; s.z = eye.getZ();
        s.pbox = null;
        s.vx = p.getDelta().getX(); s.vy = p.getDelta().getY(); s.vz = p.getDelta().getZ();
    }

    // NoFall: Geyser takes onGround straight from the client, so it gets the engine's
    private void groundTruth(PlayerAuthInputPacket p, boolean judged) {
        Set<PlayerAuthInputData> f = p.getInputData();
        boolean claimed = f.contains(PlayerAuthInputData.VERTICAL_COLLISION);
        if (judged && claimed != s.verticalCollision) {
            if (claimed) {
                groundSpoofs++;
                PandaGeyserAC.log().info(String.format("[%s] vertical collision claimed in the air at tick %d (%d times)", name(), p.getTick(), groundSpoofs));
            } else if (PandaGeyserAC.verbose) log("tick %d vertical collision not claimed", p.getTick());
        }
        if (!PandaGeyserAC.enforce) return;
        if (s.verticalCollision) f.add(PlayerAuthInputData.VERTICAL_COLLISION);
        else f.remove(PlayerAuthInputData.VERTICAL_COLLISION);
        p.setDelta(Vector3f.from(s.vx, s.vy, s.vz));
    }

    private static final int MULTI_ACTION_FINISH_SLACK = 5;

    boolean usingItemConfirmed() {
        if (!s.usingItem || !ackedUsing) return false;
        return useTicks < Math.min(finishTicks(session.getPlayerInventory().getItemInHand()), finishTicks(session.getPlayerInventory().getOffhand())) - MULTI_ACTION_FINISH_SLACK;
    }

    private static int finishTicks(GeyserItemStack item) {
        Consumable c = item == null || item.isEmpty() ? null : item.getComponent(DataComponentTypes.CONSUMABLE);
        return c == null ? Integer.MAX_VALUE : Math.round(c.consumeSeconds() * 20);
    }

    private void riptideRelease() {
        GeyserItemStack held = session.getPlayerInventory().getItemInHand();
        if (held.asItem() != Items.TRIDENT || useTicks < 10) return;
        int level = enchantLevel(held, "riptide");
        if (level <= 0 || !(s.inWater || session.isRaining() && inRain())) return;
        riptideArmed = Math.min(level, 255);
        riptideWindow = RIPTIDE_WINDOW;
        log("riptide %d armed (%d use ticks)", level, useTicks);
    }

    private boolean inRain() {
        int x = (int) Math.floor(s.x), z = (int) Math.floor(s.z);
        return skyAbove(x, (int) Math.floor(s.y), z) || skyAbove(x, (int) Math.floor(s.box().maxY()), z);
    }

    private boolean skyAbove(int x, int y, int z) {
        int top = session.getBedrockDimension().maxY();
        for (int by = y + 1; by <= top; by++) {
            me.TreeOfSelf.bedrocksim.BlockType t = world.type(x, by, z);
            if (!t.shapes().isEmpty() || t.fluid() != null) return false;
        }
        return true;
    }

    private int enchantLevel(GeyserItemStack item, String name) {
        if (item == null || item.isEmpty()) return 0;
        ItemEnchantments ench = item.getComponent(DataComponentTypes.ENCHANTMENTS);
        if (ench == null) return 0;
        int id = session.javaRegistries().registry(JavaRegistries.ENCHANTMENT).getId(Key.key("minecraft", name)).orElse(-1);
        return ench.getEnchantments().getOrDefault(id, 0);
    }

    private boolean hasGlider() {
        for (Map.Entry<EquipmentSlot, GeyserItemStack> e : session.getPlayerInventory().getEquipment().entrySet()) {
            GeyserItemStack item = e.getValue();
            if (item.getComponent(DataComponentTypes.GLIDER) != null) {
                Equippable eq = item.getComponent(DataComponentTypes.EQUIPPABLE);
                if (eq != null && eq.slot() == e.getKey() && !item.nextDamageWillBreak()) return true;
            }
            if (e.getKey() == EquipmentSlot.CHESTPLATE && !item.is(Items.ELYTRA)) return false;
        }
        return false;
    }

    private void rocketUse() {
        if (!s.gliding || session.getPlayerInventory().getItemInHand().asItem() != Items.FIREWORK_ROCKET) return;
        rocketWait = ROCKET_WAIT;
        clientBoostLeft = CLIENT_BOOST_TICKS;
        log("rocket used");
    }

    private void forwardEngine(PlayerAuthInputPacket p) {
        p.setPosition(Vector3f.from(s.x, s.eyeY, s.z));
        groundTruth(p, false);
    }

    private void setback(long tick) {
        CorrectPlayerMovePredictionPacket c = new CorrectPlayerMovePredictionPacket();
        c.setPosition(Vector3f.from(s.x, s.eyeY, s.z));
        c.setDelta(Vector3f.from(s.vx, s.vy, s.vz));
        c.setOnGround(s.onGround);
        c.setTick(tick);
        c.setPredictionType(PredictionType.PLAYER);
        c.setVehicleRotation(Vector2f.ZERO);
        awaitingCorrection = true;
        advantage = 0;
        setbacks++;
        PandaGeyserAC.log().info(String.format("[%s] setback to %.4f %.4f %.4f at tick %d (%d setbacks)", name(), s.x, s.eyeY, s.z, tick, setbacks));
        bedrock.sendPacketImmediately(c);
    }

    private boolean matches(Vector3f eye) {
        return matchesState(s, eye);
    }

    private static float dist(PlayerState st, Vector3f eye) {
        float dx = eye.getX() - st.x, dy = eye.getY() - st.eyeY, dz = eye.getZ() - st.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean matchesState(PlayerState st, Vector3f eye) {
        float tol = Math.max(THRESHOLD, 3 * Math.ulp(Math.max(Math.abs(eye.getX()), Math.abs(eye.getZ()))));
        return Math.abs(eye.getX() - st.x) <= tol && Math.abs(eye.getY() - st.eyeY) <= tol && Math.abs(eye.getZ() - st.z) <= tol;
    }

    private static PlayerState at(Vector3f eye) {
        PlayerState st = new PlayerState();
        st.x = eye.getX(); st.eyeY = eye.getY(); st.z = eye.getZ();
        return st;
    }

    private static int boatKeys(PlayerAuthInputPacket p, Set<PlayerAuthInputData> f) {
        int k = (f.contains(PlayerAuthInputData.UP) ? PlayerState.KEY_W : 0) | (f.contains(PlayerAuthInputData.DOWN) ? PlayerState.KEY_S : 0)
                | (f.contains(PlayerAuthInputData.LEFT) ? PlayerState.KEY_A : 0) | (f.contains(PlayerAuthInputData.RIGHT) ? PlayerState.KEY_D : 0);
        if (k != 0 || p.getInputMode() == InputMode.MOUSE) return k;
        boolean pl = f.contains(PlayerAuthInputData.PADDLE_LEFT), pr = f.contains(PlayerAuthInputData.PADDLE_RIGHT);
        if (pl || pr) return pl && pr ? PlayerState.KEY_W : pl ? PlayerState.KEY_D : PlayerState.KEY_A;
        Vector2f m = p.getMotion();
        return (m.getY() > 0 ? PlayerState.KEY_W : m.getY() < 0 ? PlayerState.KEY_S : 0) | (m.getX() > 0 ? PlayerState.KEY_A : m.getX() < 0 ? PlayerState.KEY_D : 0);
    }

    private static Vector2f footMove(PlayerAuthInputPacket p, Set<PlayerAuthInputData> f) {
        if (p.getInputMode() != InputMode.MOUSE) return p.getMotion();
        float x = (f.contains(PlayerAuthInputData.LEFT) || f.contains(PlayerAuthInputData.UP_LEFT) || f.contains(PlayerAuthInputData.DOWN_LEFT) ? 1 : 0)
                - (f.contains(PlayerAuthInputData.RIGHT) || f.contains(PlayerAuthInputData.UP_RIGHT) || f.contains(PlayerAuthInputData.DOWN_RIGHT) ? 1 : 0);
        float z = (f.contains(PlayerAuthInputData.UP) || f.contains(PlayerAuthInputData.UP_LEFT) || f.contains(PlayerAuthInputData.UP_RIGHT) ? 1 : 0)
                - (f.contains(PlayerAuthInputData.DOWN) || f.contains(PlayerAuthInputData.DOWN_LEFT) || f.contains(PlayerAuthInputData.DOWN_RIGHT) ? 1 : 0);
        return Vector2f.from(x, z);
    }

    private void longMove(PlayerAuthInputPacket p) {
        Vector2f m = p.getMotion();
        if (!finite(m.getX(), m.getY())) { p.setMotion(Vector2f.ZERO); return; }
        float len = m.length();
        if (len <= 1.0001f) return;
        longMoves++;
        if (longMoves == 1 || longMoves % 100 == 0) log("move vector %s longer than 1, cut (%d times)", m, longMoves);
        p.setMotion(m.div(len));
    }

    private static boolean finite(float... values) {
        for (float v : values) if (!Float.isFinite(v)) return false;
        return true;
    }

    private PlayerState[] entityAlts;

    private static void keepPrevious(float[] e) {
        e[4] = e[0]; e[5] = e[1]; e[6] = e[2];
    }

    private List<Box> entityBoxes(boolean alt) {
        if (solidEntities.isEmpty()) return List.of();
        boolean riding = vs != null && session.getPlayerEntity().getVehicle() != null;
        List<Box> out = new ArrayList<>(solidEntities.size());
        for (Map.Entry<Long, float[]> en : solidEntities.entrySet()) {
            if (riding && en.getKey() == vsId) continue;
            float[] e = en.getValue();
            float x = alt ? e[4] : e[0], y = alt ? e[5] : e[1], z = alt ? e[6] : e[2];
            if (e[3] == 0) out.add(new Box(x - 0.8f, y - 0.375f, z - 0.8f, x + 0.8f, y - 0.375f + 0.6f, z + 0.8f));
            else out.add(new Box(x - 0.5f, y, z - 0.5f, x + 0.5f, y + (alt ? 2f : SHULKER_TOP), z + 0.5f));
        }
        return out;
    }

    private boolean entityBound(PlayerAuthInputPacket p, float sx, float sy, float sz) {
        Vector3f eye = p.getPosition();
        float cx = eye.getX(), cy = eye.getY() - PlayerState.EYE_HEIGHT, cz = eye.getZ();
        float hMax = hyp(s.x - sx, s.z - sz), yMax = s.y, yMin = Math.min(sy, s.y), vhMax = hyp(s.vx, s.vz), vyMax = s.vy;
        boolean ground = s.verticalCollision && s.vy <= 0;
        if (entityAlts != null) for (PlayerState a : entityAlts) {
            hMax = Math.max(hMax, hyp(a.x - sx, a.z - sz));
            yMax = Math.max(yMax, a.y); yMin = Math.min(yMin, a.y);
            vhMax = Math.max(vhMax, hyp(a.vx, a.vz)); vyMax = Math.max(vyMax, a.vy);
            ground |= a.verticalCollision && a.vy <= 0;
        }
        float h = hyp(cx - sx, cz - sz);
        if (!(h <= hMax + ENTITY_SLACK && cy <= yMax + ENTITY_SLACK && cy >= yMin - ENTITY_SLACK)) {
            log("tick %d entity bound: moved %.4f of %.4f, feet %.4f in %.4f..%.4f", p.getTick(), h, hMax, cy, yMin, yMax);
            return false;
        }
        Set<PlayerAuthInputData> f = p.getInputData();
        if (f.contains(PlayerAuthInputData.VERTICAL_COLLISION) && !ground && !onEntityTop(cx, cy, cz)) {
            groundSpoofs++;
            PandaGeyserAC.log().info(String.format("[%s] vertical collision claimed next to a boat or shulker at tick %d (%d times)", name(), p.getTick(), groundSpoofs));
            if (PandaGeyserAC.enforce) f.remove(PlayerAuthInputData.VERTICAL_COLLISION);
        }
        entityCap = new float[]{vhMax, vyMax};
        return true;
    }

    static final float SHULKER_TOP = 0.98f;
    private float[] entityCap;
    static final float ENTITY_SLACK = 0.02f;

    private boolean onEntityTop(float x, float feet, float z) {
        for (Box b : Engine.entities())
            if (Math.abs(feet - b.maxY()) <= ENTITY_SLACK && x + 0.3f > b.minX() && x - 0.3f < b.maxX() && z + 0.3f > b.minZ() && z - 0.3f < b.maxZ()) return true;
        if (entityAlts != null) for (Box b : entityBoxes(true))
            if (Math.abs(feet - b.maxY()) <= ENTITY_SLACK && x + 0.3f > b.minX() && x - 0.3f < b.maxX() && z + 0.3f > b.minZ() && z - 0.3f < b.maxZ()) return true;
        return false;
    }

    private static float hyp(float a, float b) {
        return (float) Math.sqrt(a * a + b * b);
    }

    private boolean nearSolidEntity(float startX, float startY, float startZ, Vector3f eye) {
        if (solidEntities.isEmpty()) return false;
        float ey = eye.getY() - PlayerState.EYE_HEIGHT;
        float minX = Math.min(startX, Math.min(s.x, eye.getX())) - 0.3f - SOLID_MARGIN, maxX = Math.max(startX, Math.max(s.x, eye.getX())) + 0.3f + SOLID_MARGIN;
        float minZ = Math.min(startZ, Math.min(s.z, eye.getZ())) - 0.3f - SOLID_MARGIN, maxZ = Math.max(startZ, Math.max(s.z, eye.getZ())) + 0.3f + SOLID_MARGIN;
        float minY = Math.min(startY, Math.min(s.y, ey)) - SOLID_MARGIN, maxY = Math.max(startY, Math.max(s.y, ey)) + 1.8f + SOLID_MARGIN;
        for (Map.Entry<Long, float[]> en : solidEntities.entrySet()) {
            if (en.getKey() == vsId && vs != null && session.getPlayerEntity().getVehicle() != null) continue;
            float[] e = en.getValue();
            boolean boat = e[3] == 0;
            float half = boat ? 0.8f : 0.5f;
            float bottom = boat ? e[1] - 0.375f : e[1], top = boat ? e[1] + 0.6f : e[1] + 2f;
            if (e[0] + half > minX && e[0] - half < maxX && e[2] + half > minZ && e[2] - half < maxZ && top > minY && bottom < maxY) return true;
        }
        return false;
    }

    private void liftOntoJavaBoat(PlayerAuthInputPacket p) {
        Vector3f eye = p.getPosition();
        float x = eye.getX(), z = eye.getZ(), feet = eye.getY() - PlayerState.EYE_HEIGHT, top = Float.NEGATIVE_INFINITY;
        for (Map.Entry<Long, float[]> en : solidEntities.entrySet()) {
            float[] e = en.getValue();
            if (en.getKey() == vsId && vs != null && session.getPlayerEntity().getVehicle() != null) continue;
            boolean boat = e[3] == 0;
            // Geyser sends a boat at the Java position + 0.375
            float half = boat ? 0.6875f : 0.5f, bottom = boat ? e[1] - 0.375f : e[1], t = bottom + (boat ? 0.5625f : 1f);
            if (Math.abs(x - e[0]) < half + 0.3f && Math.abs(z - e[2]) < half + 0.3f && feet < t && feet >= t - BOAT_TOP_BAND) { top = Math.max(top, t); continue; }
            if (boat || feet >= t || feet + PlayerState.HEIGHT <= bottom) continue;
            float ox = half + 0.3f - Math.abs(x - e[0]), oz = half + 0.3f - Math.abs(z - e[2]);
            if (ox > 0 && oz > 0 && Math.min(ox, oz) <= SIDE_NUDGE) {
                if (ox <= oz) x = Math.nextUp(Math.abs(x - e[0]) + ox) * Math.signum(x - e[0]) + e[0];
                else z = Math.nextUp(Math.abs(z - e[2]) + oz) * Math.signum(z - e[2]) + e[2];
            }
        }
        if (top == Float.NEGATIVE_INFINITY && x == eye.getX() && z == eye.getZ()) return;
        // a hair above, so the float -> double conversion never lands back inside
        float y = top == Float.NEGATIVE_INFINITY ? eye.getY() : Math.nextUp(top + 1e-4f) + PlayerState.EYE_HEIGHT;
        if (PandaGeyserAC.verbose) log("fitted to a Java boat/shulker: %s -> %.4f %.4f %.4f", eye, x, y, z);
        p.setPosition(Vector3f.from(x, y, z));
    }

    static final float SIDE_NUDGE = 0.01f;
    static final float BOAT_TOP_BAND = 0.15f;

    private int vehicleKind(Set<PlayerAuthInputData> f) {
        Entity v = session.getPlayerEntity().getVehicle();
        if (v == null || linksInFlight > 0 || !f.contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) return 0;
        if (v instanceof CamelEntity) return 0;
        if (v instanceof AbstractHorseEntity && !(v instanceof LlamaEntity)) return PlayerState.HORSE;
        if (v instanceof BoatEntity && (v.getPassengers().size() == 1 || session.getPlayerEntity().isRidingInFront())) return PlayerState.BOAT;
        return 0;
    }

    private Runnable vehicleAttributesAck(long id, List<AttributeData> attributes) {
        float speed = Float.NaN, jump = Float.NaN;
        for (AttributeData a : attributes) {
            if (a.getName().equals("minecraft:movement")) speed = a.getValue();
            else if (a.getName().equals("minecraft:horse.jump_strength")) jump = a.getValue();
        }
        if (Float.isNaN(speed) && Float.isNaN(jump)) return null;
        float sp = speed, j = jump;
        if (PandaGeyserAC.verbose) log("vehicle attributes sent for %d: speed %s jump %s", id, sp, j);
        return () -> {
            if (PandaGeyserAC.verbose) log("vehicle attributes acked for %d: speed %s jump %s", id, sp, j);
            float[] at = vehicleAttributes.computeIfAbsent(id, k -> new float[]{Float.NaN, Float.NaN});
            if (!Float.isNaN(sp)) at[0] = sp;
            if (!Float.isNaN(j)) at[1] = j;
        };
    }

    private boolean prevVehicleJump;

    private void vehicleTick(PlayerAuthInputPacket p, Set<PlayerAuthInputData> f, int kind, boolean fast) {
        boolean lastVehicleJumpHeld = prevVehicleJump;
        prevVehicleJump = f.contains(PlayerAuthInputData.JUMPING);
        Entity vehicle = session.getPlayerEntity().getVehicle();
        float offset = vehicle.getOffset();
        Vector3f pos = p.getPosition();
        float cx = pos.getX(), cy = pos.getY() - offset, cz = pos.getZ();
        Vector2f rot = p.getVehicleRotation();
        boolean first = vs == null || vsId != vehicle.geyserId() || vs.vehicle != kind;
        if (first) {
            vs = new PlayerState();
            vs.vehicle = kind;
            vsId = vehicle.geyserId();
            Vector3f at = vehicle.position();
            vs.x = at.getX(); vs.y = at.getY(); vs.z = at.getZ(); vs.eyeY = vs.y + PlayerState.EYE_HEIGHT;
            vs.vehicleYaw = rot != null ? rot.getY() : vehicle.getYaw();
            vs.onGround = vehicle.isOnGround() || VehicleEngine.supported(world, vs.box());
            vsFresh = true;
            mountStanding = vehicle.getFlag(EntityFlag.STANDING);
            mountStandingSent = mountStanding;
            standingEither = 2;
            vsUnjudged = false;
            boatDx = boatDy = boatDz = 0;
            vehicleTeleport = vehicleMotion = null;
            settleTicks = -1;
            log("vehicle %s kind %d offset %s start %s, client %s", vehicle.getClass().getSimpleName(), kind, offset, at, pos);
        }
        float[] at = vehicleAttributes.get(vsId);
        vs.vehicleWidth = vehicle.getBoundingBoxWidth();
        vs.vehicleHeight = vehicle.getBoundingBoxHeight();
        vs.vehicleStep = 1f;
        if (at != null && !Float.isNaN(at[0])) vs.vehicleSpeed = at[0];
        if (at != null && !Float.isNaN(at[1])) vs.vehicleJumpStrength = at[1];
        if (vehicleTeleport != null) {
            Vector3f t = vehicleTeleport;
            vehicleTeleport = null;
            vs.x = t.getX(); vs.y = t.getY() - offset; vs.z = t.getZ(); vs.eyeY = vs.y + PlayerState.EYE_HEIGHT;
            vs.pbox = null;
            vs.vx = vs.vy = vs.vz = 0;
            vs.vehicleTurn = 0;
            boatDx = boatDy = boatDz = 0;
            settleTicks = 0;
            settleSim = vs.copy();
            settleSim.vehiclePaddle = settleSim.vehiclePaddleR = -1;
        }
        if (vehicleMotion != null) {
            Vector3f m = vehicleMotion;
            vehicleMotion = null;
            vs.vx = boatDx = m.getX(); vs.vy = boatDy = m.getY(); vs.vz = boatDz = m.getZ();
        }
        if (awaitingCorrection) {
            if (PandaGeyserAC.verbose) log("tick %d vehicle awaiting correction ack", p.getTick());
            if (PandaGeyserAC.enforce) forwardVehicle(p, offset);
            return;
        }
        if (settleTicks >= 0 && !first) {
            settle(p, f, kind, offset, cx, cy, cz, rot);
            lastVehicleYaw = rot != null ? rot.getY() : Float.NaN;
            return;
        }
        if (fast && PandaGeyserAC.enforce) {
            vehicleSetback(p.getTick(), offset);
            forwardVehicle(p, offset);
            return;
        }
        world.touchedMissingChunk = false;
        world.touchedUncertain = false;
        float sx = vs.x, sy = vs.y, sz = vs.z;
        float off;
        PlayerState before = vs.copy();
        if (kind == PlayerState.HORSE) {
            if (!first && (VehicleEngine.touchesLava(vs, world) || VehicleEngine.touchesPowderSnow(vs, world))) {
                if (PandaGeyserAC.verbose) log("tick %d vehicle not judged (lava or powder snow) client %.6f %.6f %.6f delta %s jump %b move %s rot %s vrot %s",
                        p.getTick(), cx, cy, cz, p.getDelta(), f.contains(PlayerAuthInputData.JUMPING), p.getMotion(), p.getRotation(), rot);
                vsUnjudged = true;
                float mx = cx - vs.x, mz = cz - vs.z;
                followVehicle(cx, cy, cz, rot, p.getDelta());
                Vector3f d = p.getDelta();
                if (d != null) {
                    float h = (float) Math.sqrt(mx * mx + mz * mz), vh = (float) Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
                    float k = vh > h / 0.9f ? h / 0.9f / vh : 1;
                    vs.vx = d.getX() * k; vs.vy = d.getY(); vs.vz = d.getZ() * k;
                }
                vs.onGround = VehicleEngine.supported(world, vs.box());
                vs.vehicleJumping = false;
                vs.vehicleJumpScale = 0;
                if (f.contains(PlayerAuthInputData.JUMPING) && vs.onGround) vs.vehicleJumpTicks = vs.vehicleJumpTicks < 0 ? 0 : vs.vehicleJumpTicks + 1;
                else vs.vehicleJumpTicks = -1;
                return;
            }
            Input in = new Input(p.getMotion().getX(), p.getMotion().getY(), p.getRotation().getY(), p.getRotation().getX(),
                    f.contains(PlayerAuthInputData.JUMPING), false, false, false, false);
            if (!first) {
                vs.horseStanding = false;
                PlayerState start = vs.copy();
                Engine.tick(vs, in, world);
                if ((mountStanding || standingEither > 0) && horseOff(vs, cx, cy, cz) > 0) {
                    PlayerState o = start.copy();
                    o.horseStanding = true;
                    Engine.tick(o, in, world);
                    o.horseStanding = false;
                    if (horseOff(o, cx, cy, cz) == 0) vs = o;
                }
                if (vsFresh && start.onGround && horseOff(vs, cx, cy, cz) > 0) {
                    PlayerState air = start.copy();
                    air.onGround = false;
                    Engine.tick(air, in, world);
                    air.onGround = VehicleEngine.supported(world, air.box());
                    if (horseOff(air, cx, cy, cz) == 0) vs = air;
                }
                if (start.onGround && vs.vehicleJumping && horseOff(vs, cx, cy, cz) > 0) {
                    float full = start.vehicleJumpStrength * Engine.jumpFactor(start, world);
                    if (full > 0) {
                        PlayerState j = start.copy();
                        j.vehicleJumpScaleOverride = Math.max(0.4f, Math.min(1f, (cy - start.y) / full));
                        Engine.tick(j, in, world);
                        if (horseOff(j, cx, cy, cz) == 0) vs = j;
                    }
                }
                if (PandaGeyserAC.verbose && cy > start.y + 0.05f && horseOff(vs, cx, cy, cz) > 0)
                    log("tick %d horse rose: ground %b jumping %b lastHeld %b held %b", p.getTick(), start.onGround, vs.vehicleJumping, lastVehicleJumpHeld, f.contains(PlayerAuthInputData.JUMPING));
                if (start.onGround && !vs.vehicleJumping && lastVehicleJumpHeld && !f.contains(PlayerAuthInputData.JUMPING) && cy > start.y + 0.05f && horseOff(vs, cx, cy, cz) > 0) {
                    float full = start.vehicleJumpStrength * Engine.jumpFactor(start, world);
                    if (full > 0) {
                        PlayerState j = start.copy();
                        j.vehicleJumpScale = 0.4f;
                        j.vehicleJumping = false;
                        j.vehicleJumpScaleOverride = Math.max(0.4f, Math.min(1f, (cy - start.y) / full));
                        Engine.tick(j, in, world);
                        if (PandaGeyserAC.verbose) log("tick %d release jump tried: scale %.4f -> %.6f %.6f %.6f off %.6f", p.getTick(), j.vehicleJumpScaleOverride, j.x, j.y, j.z, horseOff(j, cx, cy, cz));
                        if (horseOff(j, cx, cy, cz) == 0) {
                            if (PandaGeyserAC.verbose) log("tick %d horse jumped on a release the engine had no charge for", p.getTick());
                            vs = j;
                        }
                    }
                }
                if (start.onGround && !start.vehicleJumping && vs.vehicleJumping && horseOff(vs, cx, cy, cz) > 0) {
                    PlayerState n = start.copy();
                    n.vehicleJumpScale = 0;
                    n.vehicleJumpTicks = -1;
                    Engine.tick(n, in, world);
                    if (horseOff(n, cx, cy, cz) == 0) {
                        if (PandaGeyserAC.verbose) log("tick %d horse did not jump on the release", p.getTick());
                        vs = n;
                    }
                }
                if (vsUnjudged && horseOff(vs, cx, cy, cz) > 0) {
                    for (int v = 1; v <= 3; v++) {
                        PlayerState n = start.copy();
                        if ((v & 1) != 0) { n.vehicleJumpScale = 0; n.vehicleJumpTicks = -1; }
                        if ((v & 2) != 0) { n.vx = 0; n.vz = 0; if (n.vy > 0) n.vy = 0; }
                        Engine.tick(n, in, world);
                        if (horseOff(n, cx, cy, cz) == 0) {
                            if (PandaGeyserAC.verbose) log("tick %d horse out of fluid or powder snow:%s%s", p.getTick(),
                                    (v & 1) != 0 ? " no jump" : "", (v & 2) != 0 ? " from rest" : "");
                            vs = n;
                            break;
                        }
                    }
                }
                vsUnjudged = false;
                vsFresh = false;
                if (standingEither > 0) standingEither--;
            }
            off = horseOff(vs, cx, cy, cz);
            if (off == 0 && rot != null && Math.abs(VehicleEngine.wrap(rot.getY() - vs.vehicleYaw)) < 1e-3f) vs.vehicleYaw = rot.getY();
        } else {
            vs.boatKeys = boatKeys(p, f);
            boolean analog = p.getInputMode() != InputMode.MOUSE;
            boatPred = null;
            if (first) {
                off = (float) Math.sqrt((cx - vs.x) * (cx - vs.x) + (cy - vs.y) * (cy - vs.y) + (cz - vs.z) * (cz - vs.z));
                boatCarry = Float.POSITIVE_INFINITY;
                Vector3f d = p.getDelta();
                PlayerState rest = null;
                float best = Float.POSITIVE_INFINITY;
                int pads = VehicleEngine.boatPaddles(vs.boatKeys);
                for (int pulse = 0; pulse <= 3; pulse++) {
                    if ((pulse & ~pads) != 0) continue;
                    PlayerState r = vs.copy();
                    r.vx = r.vy = r.vz = 0;
                    r.vehicleTurn = 0;
                    r.vehiclePaddle = r.vehiclePaddleR = -1;
                    r.boatPulse = pulse;
                    VehicleEngine.tick(r, null, world);
                    float e = d == null ? 0 : Math.abs(r.vx - d.getX()) + Math.abs(r.vz - d.getZ());
                    if (e < best) { best = e; rest = r; }
                }
                firstTurn = rest.vehicleTurn;
                firstPaddle = rest.vehiclePaddle;
                firstPaddleR = rest.vehiclePaddleR;
                firstVx = rest.vx;
                firstVz = rest.vz;
            } else if (analog) {
                int k = vs.boatKeys, fwd = k & (PlayerState.KEY_W | PlayerState.KEY_S), side = k & (PlayerState.KEY_A | PlayerState.KEY_D);
                off = Float.POSITIVE_INFINITY;
                boatCarry = 0;
                for (int keys : new int[]{k, fwd, side}) {
                    off = Math.min(off, VehicleEngine.boatExcess(vs, boatDx, boatDy, boatDz, cx - vs.x, cy - vs.y, cz - vs.z, keys, world));
                    boatCarry = Math.max(boatCarry, VehicleEngine.boatSpeedLimit(vs, boatDx, boatDz, keys, world));
                }
            } else {
                off = VehicleEngine.boatExcess(vs, boatDx, boatDy, boatDz, cx - vs.x, cy - vs.y, cz - vs.z, vs.boatKeys, world);
                boatCarry = VehicleEngine.boatSpeedLimit(vs, boatDx, boatDz, vs.boatKeys, world);
                float tol = Math.max(THRESHOLD, 3 * Math.ulp(Math.max(Math.abs(cx), Math.abs(cz))));
                boatPred = vs.copy();
                VehicleEngine.tick(boatPred, null, world);
                if (!boatMatches(boatPred, cx, cz, rot, tol)) {
                    int pads = VehicleEngine.boatPaddles(vs.boatKeys);
                    for (int pulse = 0; pulse <= 3; pulse++) {
                        if ((pulse & ~pads) != 0) continue;
                        PlayerState alt = vs.copy();
                        alt.boatPulse = pulse;
                        VehicleEngine.tick(alt, null, world);
                        if (boatMatches(alt, cx, cz, rot, tol)) {
                            if (PandaGeyserAC.verbose) log("tick %d boat strokes %d (not by count)", p.getTick(), pulse);
                            boatPred = alt;
                            break;
                        }
                    }
                }
                float hx = cx - boatPred.x, hz = cz - boatPred.z;
                if (!(Math.abs(hx) <= tol && Math.abs(hz) <= tol)) off += (float) Math.sqrt(hx * hx + hz * hz);
            }
        }
        if (first && off > 0.5f) {
            float drawn = reach.drawnDistance(vsId, cx, cy, cz);
            log("vehicle first tick %.4f from where the client draws it", drawn);
            if (drawn <= 0.5f) off = 0;
        }
        if (first && off <= 0.5f) off = 0;
        if (PandaGeyserAC.verbose)
            log("tick %d vehicle %s move %s rot %s keys %d%s%s jump %b client %.6f %.6f %.6f vrot %s delta %s | engine %.6f %.6f %.6f v %.6f %.6f %.6f yaw %.4f ground %b pred yaw %s%s",
                    p.getTick(), kind == PlayerState.HORSE ? "horse" : "boat", p.getMotion(), p.getRotation(), vs.boatKeys, f.contains(PlayerAuthInputData.PADDLE_LEFT) ? " PL" : "", f.contains(PlayerAuthInputData.PADDLE_RIGHT) ? " PR" : "", f.contains(PlayerAuthInputData.JUMPING), cx, cy, cz, rot, p.getDelta(),
                    vs.x, vs.y, vs.z, vs.vx, vs.vy, vs.vz, vs.vehicleYaw, vs.onGround, boatPred != null ? String.format("%.4f", boatPred.vehicleYaw) : "-", off > 0 ? " OFF " + off : "");
        if (!first && off <= UNJUDGED_MAX && (world.touchedMissingChunk || world.touchedUncertain || nearSolidEntity(sx, sy, sz, Vector3f.from(cx, cy + PlayerState.EYE_HEIGHT, cz)))) {
            if (PandaGeyserAC.verbose) log("tick %d vehicle not judged", p.getTick());
            followVehicle(cx, cy, cz, rot, p.getDelta());
            return;
        }
        if (off == 0) {
            advantage *= ADVANTAGE_DECAY;
            if (kind == PlayerState.BOAT || first) followVehicle(cx, cy, cz, rot, p.getDelta());
            if (kind == PlayerState.HORSE && first) {
                Vector3f d = p.getDelta();
                if (d != null) { vs.vx = d.getX(); vs.vy = d.getY(); vs.vz = d.getZ(); }
                vs.onGround = VehicleEngine.supported(world, vs.box());
            }
            if (kind == PlayerState.BOAT && first) {
                vs.vehicleTurn = firstTurn; vs.vehiclePaddle = firstPaddle; vs.vehiclePaddleR = firstPaddleR;
                vs.vx = boatDx = firstVx; vs.vz = boatDz = firstVz;
            }
            return;
        }
        vehicleMismatches++;
        mismatches++;
        advantage = Math.min(advantage + off, MAX_ADVANTAGE_CEILING);
        PandaGeyserAC.log().info(String.format("[%s] %s offset %.5f (client %.4f %.4f %.4f, engine %.4f %.4f %.4f) at tick %d, advantage %.3f (%d vehicle mismatches)", name(),
                kind == PlayerState.HORSE ? "horse" : "boat", off, cx, cy, cz, vs.x, vs.y, vs.z, p.getTick(), advantage, vehicleMismatches));
        if (PandaGeyserAC.enforce && (advantage >= MAX_ADVANTAGE || off >= IMMEDIATE_SETBACK || first)) {
            if (kind == PlayerState.BOAT && !first) { vs = before; vs.vx = vs.vy = vs.vz = 0; vs.vehicleTurn = 0; boatDx = boatDy = boatDz = 0; }
            boatPred = null;
            vehicleSetback(p.getTick(), offset);
            forwardVehicle(p, offset);
            return;
        }
        followVehicle(cx, cy, cz, rot, p.getDelta());
    }

    private void settle(PlayerAuthInputPacket p, Set<PlayerAuthInputData> f, int kind, float offset, float cx, float cy, float cz, Vector2f rot) {
        if (kind == PlayerState.BOAT) {
            settleSim.boatKeys = boatKeys(p, f);
            VehicleEngine.tick(settleSim, null, world);
        } else {
            Engine.tick(settleSim, new Input(p.getMotion().getX(), p.getMotion().getY(), p.getRotation().getY(), p.getRotation().getX(),
                    f.contains(PlayerAuthInputData.JUMPING), false, false, false, false), world);
        }
        settleTicks++;
        if (kind != PlayerState.BOAT) {
            if (f.contains(PlayerAuthInputData.JUMPING) && settleSim.onGround) vs.vehicleJumpTicks = vs.vehicleJumpTicks < 0 ? 0 : vs.vehicleJumpTicks + 1;
            else vs.vehicleJumpTicks = -1;
            vs.vehicleJumping = false;
            vs.vehicleJumpScale = 0;
        }
        if (PandaGeyserAC.verbose) log("tick %d vehicle settling %d client %.6f %.6f %.6f vrot %s delta %s", p.getTick(), settleTicks, cx, cy, cz, rot, p.getDelta());
        float far = (float) Math.sqrt((cx - vs.x) * (cx - vs.x) + (cz - vs.z) * (cz - vs.z));
        float up = cy - Math.max(vs.y, settleSim.y);
        if (PandaGeyserAC.enforce && (far > SETTLE_NEAR || up > SETTLE_NEAR)) {
            if (++settleResends % 100 == 0)
                PandaGeyserAC.log().info(String.format("[%s] vehicle still %.3f from the setback after %d teleports", name(), far, settleResends));
            resendVehicleTeleport(offset);
            forwardVehicle(p, offset);
            return;
        }
        if (settleTicks < SETTLE_TICKS) {
            if (PandaGeyserAC.enforce) forwardVehicle(p, offset);
            return;
        }
        settleTicks = -1;
        settleResends = 0;
        vsUnjudged = true;
        float reach = (float) Math.sqrt((settleSim.x - vs.x) * (settleSim.x - vs.x) + (settleSim.z - vs.z) * (settleSim.z - vs.z)) + SETTLE_SLACK;
        settleGain = Math.max(0, settleGain - SETTLE_GAIN_DECAY * (ticks - lastSettle));
        lastSettle = ticks;
        settleGain += Math.max(0, cy - Math.max(vs.y, settleSim.y) - SETTLE_SLACK) + Math.max(0, far - reach);
        if (settleGain > SETTLE_GAIN_MAX) {
            PandaGeyserAC.log().info(String.format("[%s] vehicle settled %.3f off again (%.2f gained over setbacks): kept where it could be", name(), far, settleGain));
            settleGain = SETTLE_GAIN_MAX;
            vs.x = settleSim.x; vs.y = settleSim.y; vs.z = settleSim.z; vs.eyeY = settleSim.y + PlayerState.EYE_HEIGHT;
            vs.vx = settleSim.vx; vs.vy = settleSim.vy; vs.vz = settleSim.vz;
            vs.pbox = null;
            vs.onGround = settleSim.onGround;
            if (PandaGeyserAC.enforce) forwardVehicle(p, offset);
            return;
        }
        Vector3f d = p.getDelta();
        boatDx = cx - vs.x; boatDy = cy - vs.y; boatDz = cz - vs.z;
        vs.x = cx; vs.y = cy; vs.z = cz; vs.eyeY = cy + PlayerState.EYE_HEIGHT;
        vs.pbox = null;
        if (d != null) {
            float lim = kind == PlayerState.BOAT ? VehicleEngine.boatSpeedLimit(settleSim, 0, 0, settleSim.boatKeys, world) : Float.POSITIVE_INFINITY;
            float h = (float) Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
            float k = h > lim && kind == PlayerState.BOAT ? Math.max(lim, (float) Math.sqrt(settleSim.vx * settleSim.vx + settleSim.vz * settleSim.vz)) / h : 1;
            vs.vx = boatDx = d.getX() * k; vs.vy = boatDy = d.getY(); vs.vz = boatDz = d.getZ() * k;
        }
        if (rot != null) {
            vs.vehicleTurn = Float.isNaN(lastVehicleYaw) ? 0 : VehicleEngine.wrap(rot.getY() - lastVehicleYaw);
            vs.vehicleYaw = rot.getY();
        }
        vs.vehiclePaddle = vs.vehiclePaddleR = -1;
        vs.onGround = settleSim.onGround;
        if (PandaGeyserAC.verbose) log("tick %d vehicle settled %.3f from the setback (may be %.3f)", p.getTick(), far, reach);
        if (PandaGeyserAC.enforce) forwardVehicle(p, offset);
    }

    static final int SETTLE_TICKS = 10;
    static final float SETTLE_SLACK = 0.1f;
    static final float SETTLE_NEAR = 1f;
    static final float SETTLE_GAIN_MAX = 1f, SETTLE_GAIN_DECAY = 0.01f;
    private float settleGain;
    private long lastSettle;
    private int settleTicks = -1, settleResends;
    private PlayerState settleSim;
    private float lastVehicleYaw = Float.NaN;
    private static final float BOAT_YAW_SYNC = 0.5f;

    private static float horseOff(PlayerState s, float cx, float cy, float cz) {
        float dx = cx - s.x, dy = cy - s.y, dz = cz - s.z;
        float tol = Math.max(THRESHOLD, 3 * Math.ulp(Math.max(Math.abs(cx), Math.abs(cz))));
        if (Math.abs(dx) <= tol && Math.abs(dy) <= tol && Math.abs(dz) <= tol) return 0;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void followVehicle(float x, float y, float z, Vector2f rot, Vector3f delta) {
        boatDx = x - vs.x; boatDy = y - vs.y; boatDz = z - vs.z;
        float h = (float) Math.sqrt(boatDx * boatDx + boatDz * boatDz);
        if (vs.vehicle == PlayerState.BOAT && h > boatCarry) { boatDx *= boatCarry / h; boatDz *= boatCarry / h; }
        boatCarry = Float.POSITIVE_INFINITY;
        vs.x = x; vs.y = y; vs.z = z; vs.eyeY = y + PlayerState.EYE_HEIGHT;
        vs.pbox = null;
        if (rot != null) vs.vehicleYaw = rot.getY();
        lastVehicleYaw = rot != null ? rot.getY() : Float.NaN;
        if (vs.vehicle == PlayerState.BOAT) {
            vs.vx = boatDx; vs.vy = boatDy; vs.vz = boatDz;
            if (delta != null) vs.vy = delta.getY();
            if (boatPred != null) {
                vs.onGround = boatPred.onGround;
                vs.vx = boatPred.vx; vs.vz = boatPred.vz;
                vs.vehicleTurn = boatPred.vehicleTurn;
                vs.vehiclePaddle = boatPred.vehiclePaddle;
                vs.vehiclePaddleR = boatPred.vehiclePaddleR;
                if (rot == null || Math.abs(VehicleEngine.wrap(rot.getY() - boatPred.vehicleYaw)) >= BOAT_YAW_SYNC) vs.vehicleYaw = boatPred.vehicleYaw;
                boatPred = null;
            }
        }
    }

    private void forwardVehicle(PlayerAuthInputPacket p, float offset) {
        p.setPosition(Vector3f.from(vs.x, vs.y + offset, vs.z));
        Vector2f rot = p.getVehicleRotation();
        p.setVehicleRotation(Vector2f.from(rot != null ? rot.getX() : 0, vs.vehicleYaw));
    }

    private void vehicleSetback(long tick, float offset) {
        // the yaw goes out as a byte (256 steps)
        vs.vehicleYaw = Math.round(vs.vehicleYaw / 1.40625f) * 1.40625f;
        MoveEntityAbsolutePacket m = new MoveEntityAbsolutePacket();
        m.setRuntimeEntityId(vsId);
        m.setPosition(Vector3f.from(vs.x, vs.y + offset, vs.z));
        m.setRotation(Vector3f.from(0, vs.vehicleYaw, vs.vehicleYaw));
        m.setOnGround(vs.onGround);
        m.setTeleported(true);
        m.setForceMove(true);
        vs.vx = vs.vy = vs.vz = 0;
        vs.vehicleTurn = 0;
        boatDx = boatDy = boatDz = 0;
        awaitingCorrection = true;
        advantage = 0;
        setbacks++;
        settleResends = 0;
        PandaGeyserAC.log().info(String.format("[%s] vehicle setback to %.4f %.4f %.4f at tick %d (%d setbacks)", name(), vs.x, vs.y, vs.z, tick, setbacks));
        bedrock.sendPacketImmediately(m);
    }

    private void resendVehicleTeleport(float offset) {
        MoveEntityAbsolutePacket m = new MoveEntityAbsolutePacket();
        m.setRuntimeEntityId(vsId);
        m.setPosition(Vector3f.from(vs.x, vs.y + offset, vs.z));
        m.setRotation(Vector3f.from(0, vs.vehicleYaw, vs.vehicleYaw));
        m.setOnGround(vs.onGround);
        m.setTeleported(true);
        m.setForceMove(true);
        awaitingCorrection = true;
        bedrock.sendPacketImmediately(m);
    }

    private String lastRiding;
    private boolean vsFresh;
    private boolean vsUnjudged;
    private float firstTurn, firstVx, firstVz;
    private int firstPaddle, firstPaddleR;

    private static boolean boatMatches(PlayerState b, float cx, float cz, Vector2f rot, float tol) {
        return Math.abs(cx - b.x) <= tol && Math.abs(cz - b.z) <= tol
                && (rot == null || Math.abs(VehicleEngine.wrap(rot.getY() - b.vehicleYaw)) < 1e-3f);
    }
    private float boatCarry = Float.POSITIVE_INFINITY;
    private PlayerState boatPred;

    private void logRiding(PlayerAuthInputPacket p, Set<PlayerAuthInputData> f) {
        var v = session.getPlayerEntity().getVehicle();
        String now = v == null ? null : v.getClass().getSimpleName() + " predicted " + f.contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
        if (now != null && !now.equals(lastRiding)) {
            PandaGeyserAC.log().info(String.format("[%s] riding %s at tick %d (vehicle rotation %s, predicted id %d)", name(), now, p.getTick(), p.getVehicleRotation(), p.getPredictedVehicle()));
        }
        lastRiding = now;
    }

    private boolean exempt() {
        return exemptReason() != null;
    }

    private String exemptReason() {
        GameMode mode = session.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return "game mode";
        if (session.isFlying()) return "flying";
        SessionPlayerEntity e = session.getPlayerEntity();
        if (e.getVehicle() != null || linksInFlight > 0) return "riding";
        if (e.getHealth() <= 0) return "dead";
        if (e.getFlag(EntityFlag.SLEEPING)) return "sleeping";
        if (wakeTicks > 0) return "waking";
        return null;
    }

    private void reset(Vector3f eye) {
        s.x = eye.getX(); s.eyeY = eye.getY(); s.y = eye.getY() - PlayerState.EYE_HEIGHT; s.z = eye.getZ();
        s.pbox = null;
        s.vx = s.vy = s.vz = 0;
        s.onGround = false;
    }

    private static boolean velocityOff(PlayerState st, Vector3f d) {
        return d == null || Math.abs(st.vx - d.getX()) > 1e-3f || Math.abs(st.vy - d.getY()) > 1e-3f || Math.abs(st.vz - d.getZ()) > 1e-3f;
    }

    void log(String fmt, Object... args) {
        if (PandaGeyserAC.verbose) PandaGeyserAC.moveLog(name() + " " + String.format(fmt, args));
    }
}
