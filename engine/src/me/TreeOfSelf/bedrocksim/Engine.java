package me.TreeOfSelf.bedrocksim;

import java.util.ArrayList;
import java.util.List;

public final class Engine {
    // 1/sqrt(2) as a float: 0.707107 sprints, 0.707106 doesn't
    static final float SPRINT_FORWARD = 0.70710677f;

    static final float GRAVITY = 0.08f;
    static final float AIR_DRAG = 0.98f;
    static final float JUMP_VELOCITY = 0.42f;
    static final float SPRINT_JUMP_BOOST = 0.2f;
    static final float INPUT_SCALE = 0.98f;
    static final float AIR_SPEED = 0.02f;
    static final float SPRINT_MULTIPLIER = 1.3f;
    static final float SNEAK_MULTIPLIER = 0.3f;

    public static float sneakMultiplier(PlayerState s) {
        return s.swiftSneak > 0 ? 0.45f : SNEAK_MULTIPLIER;
    }
    // 0.35 x 0.35
    static final float USE_ITEM_MULTIPLIER = 0.122499995f;
    static final int JUMP_DELAY = 10;
    static final boolean SWIM_HOLD = Boolean.getBoolean("bs.swimhold");
    static final boolean WATER_STOP = !Boolean.getBoolean("bs.nowaterstop");
    static final boolean CLIENT_STOP = Boolean.getBoolean("bs.clientstop");
    static final boolean STEP_SUPPORT = !Boolean.getBoolean("bs.nostepsupport");
    static final float CLIMB_SPEED = 0.2f;
    static final float POWDER_CLIMB = 0.15f;
    static final float SCAFFOLD_SPEED = 0.15f;
    static final int SCAFFOLD_JUMP_DELAY = Integer.getInteger("bs.scafdelay", 10);
    static final float SOUL_SAND_SPEED = Float.parseFloat(System.getProperty("bs.soul", "0.54399"));

    static final class Collision {
        float fall, feet;
        boolean descend, boots;
        final java.util.IdentityHashMap<Box, Integer> scaffoldTops = new java.util.IdentityHashMap<>();
        List<Box> entities = List.of();

        void set(float fall, float feet, boolean descend, boolean boots) {
            this.fall = fall; this.feet = feet; this.descend = descend; this.boots = boots;
        }
    }
    static final ThreadLocal<Collision> CTX = ThreadLocal.withInitial(Collision::new);

    public static void setEntities(List<Box> boxes) {
        CTX.get().entities = boxes;
    }

    public static List<Box> entities() {
        return CTX.get().entities;
    }

    public static void teleportAckTick(PlayerState s, BlockWorld world) {
        fluidHeight(s, world, "water", WATER_PUSH);
        fluidHeight(s, world, "lava", LAVA_PUSH);
        entityInside(s, world, true);
    }

    public static void tick(PlayerState s, Input in, BlockWorld world) {
        if (s.vehicle != 0) {
            VehicleEngine.tick(s, in, world);
            return;
        }
        tickMove(s, in, world);
        if (s.glideBoost > 0) s.glideBoost--;
        // after a riptide wall hit, fluids are checked with the standing box again
        s.lagHeight = s.boxHeight();
        if (s.spinTicks > 0) s.spinTicks = s.horizontalCollision ? 0 : s.spinTicks - 1;
    }

    private static void tickMove(PlayerState s, Input in, BlockWorld world) {
        if (s.gliding && s.vy > -0.5f) s.fallDistance = 1;
        CTX.get().set(s.fallDistance, s.box().minY(), in.sneaking(), s.leatherBoots);
        // one float multiply, and a near-zero sine/cosine snaps to exactly 0
        float yawRad = in.yaw() * 0.017453292f;
        float sin = TABLE_YAW ? tableSin(yawRad) : snapTrig((float) Math.sin(yawRad));
        float cos = TABLE_YAW ? tableCos(yawRad) : snapTrig((float) Math.cos(yawRad));

        boolean swamThisTick = s.swimming;
        float waterHeight = fluidHeight(s, world, "water", WATER_PUSH);
        boolean inWater = waterHeight > 0;
        float lavaShift = 0;
        if (s.lavaFlip) lavaShift = fluidHeight(s, world, "lava", 0, 0) > 0 ? 2e-4f : -(LAVA_EPS + 2e-4f);
        s.lavaFlip = false;
        boolean inLava = fluidHeight(s, world, "lava", LAVA_PUSH, lavaShift) > 0;
        boolean surfaceBand = !inWater && !inLava && s.swimming && waterInSwimBand(s, world);
        boolean surfaceHold = surfaceBand && in.jumping();
        boolean waterHold = (SWIM_HOLD && s.swimming || s.swimHold) && inWater && in.jumping();
        s.swimHold = false;
        if (surfaceBand) inWater = true;
        s.inWater = inWater;
        boolean leftWater = s.swimming && !inWater;
        if (leftWater) {
            s.swimming = false;
            s.sprinting = false;
        }

        boolean maySprint = maySprint(s) && (inWater || in.moveZ() >= SPRINT_FORWARD);
        if (in.startSprinting() && in.stopSprinting()) s.sprinting = false;
        else if (in.startSprinting()) s.sprinting = s.sprinting || maySprint;
        else if (!leftWater && maySprint && in.sprintHeld() && !s.sprinting && !(s.horizontalCollision && (s.onGround || s.sprintEndedByHit || !AIR_RESPRINT)) && !s.lastShallowHit && !s.lastShallowHorizontal && !in.sneaking() && !s.wasSneaking) s.sprinting = true;
        if (WATER_STOP && in.stopSprinting() && !in.startSprinting() && inWater && !s.swimming) s.sprinting = false;
        if (CLIENT_STOP && in.stopSprinting() && !in.startSprinting()) s.sprinting = false;
        if (s.sprinting) s.sprintEndedByHit = false;
        if (s.food <= 6) s.sprinting = false;
        if (s.sprinting && (in.moveZ() <= 1.0E-5f || !inWater && in.moveZ() < SPRINT_FORWARD || in.sneaking() && !inWater)) s.sprinting = false;
        if (s.swimming) s.swimming = s.sprinting && inWater;
        else s.swimming = s.sprinting && underWater(s, world) && isWater(world, floor(s.x), floor(s.y), floor(s.z));
        if (s.forceSwim == 1 && inWater && maySprint) s.swimming = true;
        else if (s.forceSwim == 0) s.swimming = false;
        s.forceSwim = -1;
        if (s.riptide > 0) {
            float f = 1.5f + 0.75f * (s.riptide - 1);
            float g = in.yaw() * 0.017453292f, h = in.pitch() * 0.017453292f;
            float k = -tableSin(g) * tableCos(h), l = -tableSin(h), m = tableCos(g) * tableCos(h);
            float n = (float) Math.sqrt(k * k + l * l + m * m);
            s.vx += k * (f / n);
            s.vy += l * (f / n);
            s.vz += m * (f / n);
            // launching from the ground adds 0.08 up
            if (s.onGround) s.vy += 0.08f;
            s.spinTicks = 20;
            s.riptide = 0;
        }
        if (s.jumpDelay > 0) s.jumpDelay--;
        if (!in.jumping() && !in.ascend()) s.jumpDelay = 0;

        if (inWater && in.sneaking()) s.vy -= WATER_VERTICAL_INPUT;

        boolean inScaffold = inScaffolding(s, world, 0), overScaffold = inScaffolding(s, world, 1);
        boolean scaffoldDescend = false;
        if ((in.jumping() || in.ascend()) && inScaffold && !inWater && !inLava) {
            s.vy = SCAFFOLD_SPEED;
            s.jumpDelay = SCAFFOLD_JUMP_DELAY;
        } else if (in.jumping() && s.inPowder && s.leatherBoots && !inWater && !inLava) {
            s.vy = POWDER_CLIMB;
            s.jumpDelay = SCAFFOLD_JUMP_DELAY;
        } else if (in.jumping() && (inWater || inLava)) {
            s.vy += WATER_VERTICAL_INPUT;
        } else if (in.jumping() && s.onGround && s.jumpDelay == 0) {
            s.vy = JUMP_VELOCITY * jumpFactor(s, world) + (s.jumpBoostAmp >= 0 ? 0.1f * (s.jumpBoostAmp + 1) : 0);
            s.jumpDelay = JUMP_DELAY;
            if (s.sprinting && !(s.usingItem && !s.useNoSlow)) {
                // the boost direction uses the sine table, not float trig
                s.vx -= tableSin(yawRad) * SPRINT_JUMP_BOOST;
                s.vz += tableCos(yawRad) * SPRINT_JUMP_BOOST;
            }
        }
        if (in.sneaking() && overScaffold) {
            s.vy = -SCAFFOLD_SPEED;
            scaffoldDescend = true;
        }
        if (in.sneaking() && isPowder(world, floor(s.x), floor(s.y - 1f), floor(s.z))) s.vy = -0.15f;

        int bx = floor(s.x), bz = floor(s.z);
        float blockFriction = s.onGround ? world.type(bx, floor(s.y - 0.1f), bz).friction() : 1.0f;
        if (FREEZE_FIRST) s.ticksFrozen = s.inPowder && !s.freezeImmune ? Math.min(140, s.ticksFrozen + 1) : Math.max(0, s.ticksFrozen - 2);
        float base = s.movementSpeed;
        if (s.speedAmp >= 0) base *= 1f + 0.2f * (s.speedAmp + 1);
        if (s.slownessAmp >= 0) base *= 1f - 0.15f * (s.slownessAmp + 1);
        if (s.ticksFrozen > 0) base -= 0.05f * s.ticksFrozen / 140f;
        if (!FREEZE_FIRST) s.ticksFrozen = s.inPowder && !s.freezeImmune ? Math.min(140, s.ticksFrozen + 1) : Math.max(0, s.ticksFrozen - 2);
        boolean sprintMove = s.sprinting && !(s.usingItem && !s.useNoSlow);
        float speed = base * (sprintMove ? SPRINT_MULTIPLIER : 1f);
        if (s.attrMode) {
            if (in.startSprinting() && in.stopSprinting()) { s.attrSprintModifier = false; s.attrValue = s.attrBase; s.attrSprinting = false; }
            if (in.startSprinting() && !in.stopSprinting() && s.sprinting && s.attrSprinting && !s.attrSprintModifier) { s.attrSprintModifier = true; s.attrValue = s.attrBase + s.attrBase * 0.3f; }
            if (s.sprinting != s.attrSprinting) {
                s.attrSprinting = s.sprinting;
                if (s.sprinting && !s.attrSprintModifier) { s.attrSprintModifier = true; s.attrValue = s.attrBase + s.attrBase * 0.3f; }
                else if (!s.sprinting && s.attrSprintModifier) { s.attrSprintModifier = false; s.attrValue = s.attrBase; }
            }
            speed = s.attrValue;
        }
        float strider = inWater ? Math.min(s.depthStrider, 3) / 3f * (s.onGround || NO_STRIDER_HALF ? 1f : 0.5f) : 0f;
        float striderSpeed = speed;
        if (s.onGround && s.soulSpeed == 0 && world.type(bx, floor(s.y - 0.001f), bz).name().equals("soul_sand")) speed *= SOUL_SAND_SPEED;
        float accel = inWater && strider > 0 ? WATER_SPEED + (striderSpeed - WATER_SPEED) * strider : inWater || inLava ? WATER_SPEED : s.onGround ? speed * (0.21600002f / (blockFriction * blockFriction * blockFriction))
                : (sprintMove ? AIR_SPEED + 0.006f : AIR_SPEED);
        float mx = in.moveX(), mz = in.moveZ();
        float lenSq = mx * mx + mz * mz;
        if (lenSq > 1f) {
            float len = (float) Math.sqrt(lenSq);
            mx /= len;
            mz /= len;
        }
        boolean slow = (in.sneaking() || s.wasSneaking || s.crouching) && !inWater;
        if (REBUILD_ON_SNEAK && in.sneaking() != s.wasSneaking) s.pbox = null;
        s.wasSneaking = in.sneaking();
        s.crouching = in.sneaking() || s.crouching && collides(world, s.standingBox());
        if (slow) {
            float sneak = sneakMultiplier(s);
            mx *= sneak;
            mz *= sneak;
        }
        if (s.usingItem && !s.useNoSlow) {
            mx *= USE_ITEM_MULTIPLIER;
            mz *= USE_ITEM_MULTIPLIER;
        }
        if (s.swimming) {
            float look = -tableSin(in.pitch() * 0.017453292f);
            if (!isFluid(world, floor(s.x), floor(s.y + 0.4f), floor(s.z)) && look > 0 && look < 0.55f) {
                s.vy = 0;
            } else {
                float e = look < -0.2f ? 0.085f : 0.06f;
                if ((look <= 0 || isFluid(world, floor(s.x), floor(s.y), floor(s.z))) && !in.jumping()) s.vy += (look - s.vy) * e;
            }
        }
        if (in.startGliding() && !s.onGround && !inWater) s.gliding = true;
        if (s.gliding && (s.onGround || inWater || s.levitationAmp >= 0 && !s.glideHold || in.stopGliding() && in.jumping())) s.gliding = false;
        boolean levitatedGlide = s.glideHold && s.gliding && s.levitationAmp >= 0;
        s.glideHold = false;
        if (!s.gliding) moveRelative(s, mx * INPUT_SCALE, mz * INPUT_SCALE, accel, sin, cos);

        boolean climbing = world.type(bx, floor(s.y), bz).climbable();
        if (climbing) {
            boolean scaffold = isScaffolding(world, bx, floor(s.y), bz);
            if (s.horizontalCollision || in.jumping() && !scaffold) s.vy = CLIMB_SPEED;
            s.vy = Math.max(s.vy, -CLIMB_SPEED);
            if (s.vy < 0 && in.sneaking() && !scaffold) s.vy = 0;
        }

        if (s.gliding && levitatedGlide) {
            int lv = s.levitationAmp;
            s.levitationAmp = -1;
            glide(s, in);
            s.levitationAmp = lv;
            glideMove(s, world);
            s.vy += (0.05f * (lv + 1) - s.vy) * 0.2f;
            if (s.onGround) s.gliding = false;
            entityInside(s, world);
            return;
        }
        if (s.gliding) {
            glide(s, in);
            glideMove(s, world);
            if (s.onGround) s.gliding = false;
            entityInside(s, world);
            return;
        }

        float dx = s.vx, dz = s.vz;
        if (in.sneaking() && s.onGround) {
            float[] b = backOffFromEdge(s.box(), world, dx, dz);
            // Bedrock only zeroes an axis when the edge cancels it entirely
            if (b[0] == 0 && dx != 0) s.vx = 0;
            if (b[1] == 0 && dz != 0) s.vz = 0;
            dx = b[0];
            dz = b[1];
        }
        float yBefore = s.y;
        if (surfaceHold || waterHold) s.vy = 0;
        float dy = s.vy;
        if (s.stuckX != 0 || s.stuckY != 0 || s.stuckZ != 0) {
            dx *= s.stuckX;
            dy *= s.stuckY;
            dz *= s.stuckZ;
            s.stuckX = s.stuckY = s.stuckZ = 0;
            s.vx = s.vy = s.vz = 0;
        }
        move(s, world, dx, dy, dz, in.sneaking());

        if (inWater) s.fallDistance = 0;
        if (inWater || inLava) {
            if (inWater) {
                float f = s.sprinting || swamThisTick ? 0.9f : 0.8f;
                float striderDrag = Math.min(s.depthStrider, 3) / 3f * (s.onGround || NO_STRIDER_HALF ? 1f : 0.5f);
                if (striderDrag > 0) f += (0.54600006f - f) * striderDrag;
                s.vx *= f;
                s.vy *= 0.8f;
                s.vz *= f;
                if (s.levitationAmp >= 0) s.vy += (0.05f * (s.levitationAmp + 1) - s.vy) * 0.2f;
                else if (!s.swimming) s.vy -= gravity(s) / 16f;
                if (surfaceHold) s.vy = 0;
            } else {
                s.vx *= 0.5f;
                s.vy *= 0.5f;
                s.vz *= 0.5f;
                s.vy -= gravity(s) / 4f;
            }
            if (s.horizontalCollision && free(world, s.box().offset(s.vx, s.vy + 0.6f - s.y + yBefore, s.vz))) s.vy = 0.3f;
            entityInside(s, world);
            return;
        }
        if (scaffoldDescend && !s.onGround) {
        } else if (s.levitationAmp >= 0) s.vy += (0.05f * (s.levitationAmp + 1) - s.vy) * 0.2f;
        else s.vy -= gravity(s);
        s.vy *= AIR_DRAG;
        if (s.horizontalCollision && (world.type(floor(s.x), floor(s.y), floor(s.z)).climbable() || s.inPowder && s.leatherBoots)) s.vy = CLIMB_SPEED;
        float horizontal = blockFriction * 0.91f;
        s.vx *= horizontal;
        s.vz *= horizontal;

        // block effects come after friction on Bedrock
        if (s.onGround) {
            BlockType on = STEP_SUPPORT ? supporting(s.box(), s, world) : null;
            stepOn(s, in, on != null ? on : world.type(floor(s.x), floor(s.y - 0.2f), floor(s.z)));
        }
        depenetrate(s, world);
        entityInside(s, world);
    }

    static void glideMove(PlayerState s, BlockWorld world) {
        float dx = s.vx, dy = s.vy, dz = s.vz;
        if (s.stuckX != 0 || s.stuckY != 0 || s.stuckZ != 0) {
            dx *= s.stuckX;
            dy *= s.stuckY;
            dz *= s.stuckZ;
            s.stuckX = s.stuckY = s.stuckZ = 0;
            s.vx = s.vy = s.vz = 0;
        }
        move(s, world, dx, dy, dz, false);
    }

    static void depenetrate(PlayerState s, BlockWorld world) {
        Box b = s.box();
        int y = floor(b.minY() + 0.6f);
        for (int x = floor(b.minX()); x <= floor(b.maxX()); x++)
            for (int z = floor(b.minZ()); z <= floor(b.maxZ()); z++) {
                if (isScaffolding(world, x, y, z) || isPowder(world, x, y, z) || noPushOut(world.type(x, y, z).name())) continue;
                for (Box c : world.collisionBoxes(x, y, z)) {
                    if (!(c.minY() < b.maxY() && c.maxY() > b.minY() && c.minX() < b.maxX() && c.maxX() > b.minX() && c.minZ() < b.maxZ() && c.maxZ() > b.minZ())) continue;
                    float dx = s.x - (x + 0.5f), dz = s.z - (z + 0.5f);
                    float len = (float) Math.sqrt(dx * dx + dz * dz);
                    if (len < 1.0E-7f) { dx = 1; dz = 1; len = (float) Math.sqrt(2); }
                    s.vx = dx / len * 0.1f;
                    s.vz = dz / len * 0.1f;
                    return;
                }
            }
    }

    static float gravity(PlayerState s) {
        return s.vy < 0 && s.slowFallingAmp >= 0 ? Math.min(GRAVITY, 0.01f) : GRAVITY;
    }

    static final int GLIDE_REAL = Integer.getInteger("bs.glidereal", 0);

    static void glide(PlayerState s, Input in) {
        float f = in.pitch() * 0.017453292f, g = -in.yaw() * 0.017453292f;
        boolean realLook = (GLIDE_REAL & 4) != 0;
        float h = realLook ? (float) Math.cos(g) : (LOOK_COS & 2) != 0 ? lookCos(g) : tableCos(g), i = realLook ? (float) Math.sin(g) : tableSin(g);
        float j = realLook ? (float) Math.cos(f) : (LOOK_COS & 1) != 0 ? lookCos(f) : tableCos(f), k = realLook ? (float) Math.sin(f) : tableSin(f);
        float lx = i * j, ly = -k, lz = h * j;
        float lookHor = (float) Math.sqrt(lx * lx + lz * lz);
        float moveHor = (float) Math.sqrt(s.vx * s.vx + s.vz * s.vz);
        float cosLean = (GLIDE_REAL & 1) != 0 ? (float) Math.cos(f) : tableCos(f);
        float lift = cosLean * (Math.min((float) Math.sqrt(lx * lx + ly * ly + lz * lz) * 2.5f, 1.0f) * cosLean);
        s.vy -= (lift * 0.75f - 1.0f) * -gravity(s);
        if (s.vy < 0 && lookHor > 0) {
            float convert = s.vy * -0.1f * lift;
            s.vx += lx * convert / lookHor;
            s.vy += convert;
            s.vz += lz * convert / lookHor;
        }
        if (f < 0) {
            float convert = ((GLIDE_REAL & 2) != 0 ? (float) Math.sin(f) : tableSin(f)) * moveHor * -0.04f;
            s.vx -= convert * lx / lookHor;
            s.vy -= convert * -3.2f;
            s.vz -= convert * lz / lookHor;
        }
        if (lookHor > 0) {
            s.vx += (lx / lookHor * moveHor - s.vx) * 0.1f;
            s.vz += (lz / lookHor * moveHor - s.vz) * 0.1f;
        }
        if (s.glideBoost > 0) {
            float bc = realLook ? j : SIN[index((1.5707964f - f) * 10430.378f) & 65535];
            float bx = i * bc, by = ly, bz = h * bc;
            s.vx += bx * 0.1f + (bx * 1.5f - s.vx) * 0.5f;
            s.vy += by * 0.1f + (by * 1.5f - s.vy) * 0.5f;
            s.vz += bz * 0.1f + (bz * 1.5f - s.vz) * 0.5f;
        }
        s.vx *= 0.99f;
        s.vy *= 0.98f;
        s.vz *= 0.99f;
    }

    static void moveRelative(PlayerState s, float strafe, float forward, float accel, float sin, float cos) {
        float lenSq = strafe * strafe + forward * forward;
        if (lenSq < 1.0E-7f) return;
        if (lenSq > 1f) {
            float len = (float) Math.sqrt(lenSq);
            strafe /= len;
            forward /= len;
        }
        strafe *= accel;
        forward *= accel;
        s.vx += strafe * cos - forward * sin;
        s.vz += forward * cos + strafe * sin;
    }
    // exact zero for a cardinal yaw's sine/cosine
    static final float TRIG_SNAP = Float.parseFloat(System.getProperty("bs.trigsnap", "1e-6"));
    static float snapTrig(float v) { return Math.abs(v) < TRIG_SNAP ? 0f : v; }

    public static boolean maySprint(PlayerState s) {
        return s.food > 6 && s.blindnessAmp < 0;
    }

    static final boolean AIR_RESPRINT = !Boolean.getBoolean("bs.noairresprint");
    static final boolean PBOX = !Boolean.getBoolean("bs.nopbox");
    static final boolean REBUILD_ON_SNEAK = !Boolean.getBoolean("bs.keepboxsneak");
    static final float STEP_HEIGHT = 0.5625f;
    static final float SPRINT_KEEP = Float.parseFloat(System.getProperty("bs.keep", "0.45"));
    static final float EDGE_STEP = 0.05f;
    // ground check: box shrunk 0.025 per side, reaching 0.575 below the feet
    static final float EDGE_INSET = 0.025f;
    static final float EDGE_DEPTH = 0.6f;

    // sneaking: shorten the move in 0.05 steps until the box still stands on something
    static float[] backOffFromEdge(Box box, BlockWorld world, float dx, float dz) {
        Box test = new Box(box.minX() + EDGE_INSET, box.minY() + EDGE_INSET, box.minZ() + EDGE_INSET,
                box.maxX() - EDGE_INSET, box.maxY() - EDGE_INSET, box.maxZ() - EDGE_INSET).offset(0, -EDGE_DEPTH, 0);
        while (dx != 0 && !collides(world, test.offset(dx, 0, 0))) dx = shorten(dx);
        while (dz != 0 && !collides(world, test.offset(0, 0, dz))) dz = shorten(dz);
        while (dx != 0 && dz != 0 && !collides(world, test.offset(dx, 0, dz))) {
            dx = shorten(dx);
            dz = shorten(dz);
        }
        return new float[]{dx, dz};
    }

    static final float WATER_SPEED = 0.02f;
    static final boolean NO_STRIDER_HALF = Boolean.getBoolean("bs.striderfull");
    static final boolean BOAR_WATER = !Boolean.getBoolean("bs.javawater");
    static final float WATER_VERTICAL_INPUT = 0.04f;

    static final float WATER_PUSH = 0.014f;
    static final boolean STANDING_FLUID_BOX = !Boolean.getBoolean("bs.swimfluidbox");
    static final float LAVA_EPS = Float.parseFloat(System.getProperty("bs.lavaeps", "0.001"));
    static final float LAVA_SIDE = Float.parseFloat(System.getProperty("bs.lavaside", "0.1"));
    static final float LAVA_PUSH = Float.parseFloat(System.getProperty("bs.lavapush", "0.0035"));

    static boolean isWater(BlockWorld world, int x, int y, int z) {
        return "water".equals(world.type(x, y, z).fluid());
    }

    static boolean underWater(PlayerState s, BlockWorld world) {
        float eye = s.y + PlayerState.EYE_HEIGHT;
        int x = floor(s.x), y = floor(eye), z = floor(s.z);
        return isWater(world, x, y, z) && eye < y + height(world, x, y, z);
    }

    static final boolean FREEZE_FIRST = !Boolean.getBoolean("bs.freezelate");
    static final boolean JAVA_POWDER = Boolean.getBoolean("bs.javapowder");

    static boolean inScaffolding(PlayerState s, BlockWorld world, int dy) {
        Box b = s.box().offset(0, -dy, 0);
        int fy = floor(s.y) - dy;
        for (int x = floor(b.minX()); x <= floor(b.maxX()); x++)
            for (int z = floor(b.minZ()); z <= floor(b.maxZ()); z++)
                if (isScaffolding(world, x, fy, z) && b.maxX() > x && b.minX() < x + 1 && b.maxY() > fy && b.minY() < fy + 1 && b.maxZ() > z && b.minZ() < z + 1) return true;
        return false;
    }

    static boolean noPushOut(String name) {
        return name.contains("door") || name.contains("fence_gate");
    }

    static boolean isScaffolding(BlockWorld world, int x, int y, int z) {
        return world.type(x, y, z).name().equals("scaffolding");
    }

    static boolean isPowder(BlockWorld world, int x, int y, int z) {
        return world.type(x, y, z).name().equals("powder_snow");
    }

    static boolean isFluid(BlockWorld world, int x, int y, int z) {
        return world.type(x, y, z).fluid() != null;
    }

    // fluid height (8 - level) / 9
    static float height(BlockWorld world, int x, int y, int z) {
        BlockType t = world.type(x, y, z);
        if (t.fluid().equals(world.type(x, y + 1, z).fluid())) return 1f;
        return t.level() == 0 || t.level() >= 8 ? 8f / 9f : (8 - t.level()) / 9f;
    }

    static boolean waterInSwimBand(PlayerState s, BlockWorld world) {
        Box b = s.standingBox();
        float minX = b.minX() + 0.001f, maxX = b.maxX() - 0.001f, minZ = b.minZ() + 0.001f, maxZ = b.maxZ() - 0.001f;
        float minY = b.minY() + 0.6f - 0.4f - 0.001f, maxY = b.minY() + 0.4f + 0.001f;
        for (int x = floor(minX); x <= floor(maxX); x++)
            for (int y = floor(minY); y <= floor(maxY); y++)
                for (int z = floor(minZ); z <= floor(maxZ); z++)
                    if ("water".equals(world.type(x, y, z).fluid()) && y + height(world, x, y, z) >= minY) return true;
        return false;
    }

    public static boolean lavaEdge(PlayerState s, BlockWorld world) {
        Box b = STANDING_FLUID_BOX && s.spinTicks == 0 ? s.standingBox() : s.box();
        float minY = b.minY() + 0.4f + LAVA_EPS;
        int below = floor(minY - LAVA_EPS - 1e-4f), above = floor(minY + 1e-4f);
        if (below == above) return false;
        float minX = b.minX() + LAVA_SIDE, maxX = b.maxX() - LAVA_SIDE, minZ = b.minZ() + LAVA_SIDE, maxZ = b.maxZ() - LAVA_SIDE;
        for (int x = floor(minX); x <= floor(maxX); x++)
            for (int z = floor(minZ); z <= floor(maxZ); z++)
                if ("lava".equals(world.type(x, below, z).fluid()) != "lava".equals(world.type(x, above, z).fluid())) return true;
        return false;
    }

    static float fluidHeight(PlayerState s, BlockWorld world, String fluid, float push) {
        return fluidHeight(s, world, fluid, push, 0);
    }

    static float fluidHeight(PlayerState s, BlockWorld world, String fluid, float push, float shift) {
        Box b = STANDING_FLUID_BOX && s.spinTicks == 0 ? s.standingBox() : s.box();
        float side = fluid.equals("lava") ? LAVA_SIDE : 0.001f;
        float minX = b.minX() + side, maxX = b.maxX() - side, minZ = b.minZ() + side, maxZ = b.maxZ() - side;
        float eps = fluid.equals("lava") ? LAVA_EPS : 0.001f;
        float minY = b.minY() + 0.4f + eps + shift, maxY = b.maxY() - 0.4f - eps;
        if (minY > maxY) {
            float t = minY;
            minY = maxY;
            maxY = t;
        }
        float best = 0;
        float fx = 0, fy = 0, fz = 0;
        for (int x = floor(minX); x <= floor(maxX); x++)
            for (int y = floor(minY); y <= floor(maxY); y++)
                for (int z = floor(minZ); z <= floor(maxZ); z++) {
                    if (!fluid.equals(world.type(x, y, z).fluid())) continue;
                    float h = height(world, x, y, z);
                    if (BOAR_WATER) {
                        best = Math.max(best, Math.max(1.0E-6f, y + 1 - h));
                    } else {
                        float top = y + h;
                        if (top >= minY) best = Math.max(best, top - minY);
                    }
                    float[] f = flow(world, x, y, z);
                    fx += f[0];
                    fy += f[1];
                    fz += f[2];
                }
        if (fx * fx + fy * fy + fz * fz > 0) {
            float[] n = normalize(fx, fy, fz);
            s.vx += n[0] * push;
            s.vy += n[1] * push;
            s.vz += n[2] * push;
        }
        return best;
    }

    static int decay(BlockType t) {
        return t.level() >= 8 ? 0 : t.level();
    }

    static final int[][] HORIZONTAL = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    static float[] flow(BlockWorld world, int x, int y, int z) {
        BlockType self = world.type(x, y, z);
        if (self.name().startsWith("bubble_")) return new float[]{0, 0, 0};
        int i = decay(self);
        float vx = 0, vy = 0, vz = 0;
        for (int[] d : HORIZONTAL) {
            int nx = x + d[0], nz = z + d[1];
            BlockType n = world.type(nx, y, nz);
            if (self.fluid().equals(n.fluid())) {
                int l = decay(n) - i;
                vx += d[0] * l;
                vz += d[1] * l;
            } else if (n.shapes().isEmpty()) {
                BlockType below = world.type(nx, y - 1, nz);
                if (self.fluid().equals(below.fluid())) {
                    int k = decay(below) - (i - 8);
                    vx += d[0] * k;
                    vz += d[1] * k;
                }
            }
        }
        if (self.level() >= 8) {
            for (int[] d : HORIZONTAL) {
                if (solidFace(world, self, x + d[0], y, z + d[1]) || solidFace(world, self, x + d[0], y + 1, z + d[1])) {
                    float[] n = normalize(vx, vy, vz);
                    vx = n[0];
                    vy = n[1] - 6;
                    vz = n[2];
                    break;
                }
            }
        }
        return normalize(vx, vy, vz);
    }

    static boolean solidFace(BlockWorld world, BlockType fluid, int x, int y, int z) {
        BlockType t = world.type(x, y, z);
        if (fluid.fluid().equals(t.fluid())) return false;
        if (t.name().contains("ice")) return false;
        return t.shapes().contains(BlockType.FULL);
    }

    static float[] normalize(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (Math.abs(len) < 1.1920929E-7f) return new float[]{0, 0, 0};
        return new float[]{x / len, y / len, z / len};
    }

    static boolean free(BlockWorld world, Box area) {
        if (collides(world, area)) return false;
        for (int x = floor(area.minX()); x <= floor(area.maxX()); x++)
            for (int y = floor(area.minY()); y <= floor(area.maxY()); y++)
                for (int z = floor(area.minZ()); z <= floor(area.maxZ()); z++)
                    if (isFluid(world, x, y, z)) return false;
        return true;
    }

    static void entityInside(PlayerState s, BlockWorld world) {
        entityInside(s, world, false);
    }

    static void entityInside(PlayerState s, BlockWorld world, boolean bubblesOnly) {
        Box b = s.box();
        if (!bubblesOnly) s.inPowder = false;
        for (int x = floor(b.minX() + 0.001f); x <= floor(b.maxX() - 0.001f); x++)
            for (int y = floor(b.minY() + 0.001f); y <= floor(b.maxY() - 0.001f); y++)
                for (int z = floor(b.minZ() + 0.001f); z <= floor(b.maxZ() - 0.001f); z++) {
                    String name = world.type(x, y, z).name();
                    if (bubblesOnly && !name.startsWith("bubble_")) continue;
                    switch (name) {
                        case "cobweb" -> stick(s, 0.25f, 0.05f, 0.25f);
                        case "powder_snow" -> {
                            boolean hit = JAVA_POWDER ? isPowder(world, floor(s.x), floor(s.y), floor(s.z)) : !b.offset(0, 1.0E-3f, 0).contains(x, y, z) && s.y < y + 1 - 1.0E-5f;
                            if (hit) {
                                stick(s, 0.9f, 1.5f, 0.9f);
                                s.inPowder = true;
                            }
                        }
                        case "bubble_up", "bubble_down" -> {
                            boolean drag = world.type(x, y, z).name().equals("bubble_down");
                            if (world.type(x, y + 1, z) == BlockType.AIR) s.vy = drag ? Math.max(-0.9f, s.vy - 0.03f) : Math.min(1.8f, s.vy + 0.1f);
                            else s.vy = drag ? Math.max(-0.3f, s.vy - 0.03f) : Math.min(0.7f, s.vy + 0.06f);
                        }
                        case "sweet_berry_bush" -> stick(s, 0.8f, 0.75f, 0.8f);
                        case "honey" -> {
                            s.vx *= 0.40000001f;
                            s.vz *= 0.40000001f;
                            s.vy = Math.max(-0.12f, s.vy);
                        }
                        default -> {
                        }
                    }
                }
    }

    public static float jumpFactor(PlayerState s, BlockWorld world) {
        float f = world.type(floor(s.x), floor(s.y), floor(s.z)).jumpFactor();
        return f != 1f ? f : world.type(floor(s.x), floor(s.y - 0.1f), floor(s.z)).jumpFactor();
    }

    static void stick(PlayerState s, float x, float y, float z) {
        if (s.stuckX != 0 || s.stuckY != 0 || s.stuckZ != 0) {
            s.stuckX = Math.min(s.stuckX, x);
            s.stuckY = Math.min(s.stuckY, y);
            s.stuckZ = Math.min(s.stuckZ, z);
        } else {
            s.stuckX = x;
            s.stuckY = y;
            s.stuckZ = z;
        }
    }

    static void stepOn(PlayerState s, Input in, BlockType block) {
        if (block.name().equals("slime") || block.name().equals("honey")) {
            float vy = Math.abs(s.vy);
            if (vy < 0.1f && !in.sneaking()) {
                float e = 0.4f + vy * 0.2f;
                s.vx *= e;
                s.vz *= e;
            }
        }
    }

    static float speedFactor(PlayerState s, BlockWorld world) {
        int x = floor(s.x), z = floor(s.z);
        float f = world.type(x, floor(s.y), z).speedFactor();
        return f != 1f ? f : world.type(x, floor(s.y - 0.500001f), z).speedFactor();
    }

    static float shorten(float d) {
        if (d < EDGE_STEP && d >= -EDGE_STEP) return 0;
        return d > 0 ? d - EDGE_STEP : d + EDGE_STEP;
    }

    static boolean collides(BlockWorld world, Box area) {
        for (Box b : collect(world, area)) {
            if (b.maxX() > area.minX() && b.minX() < area.maxX() && b.maxY() > area.minY() && b.minY() < area.maxY()
                    && b.maxZ() > area.minZ() && b.minZ() < area.maxZ()) return true;
        }
        return false;
    }

    static void move(PlayerState s, BlockWorld world, float dx, float dy, float dz, boolean sneaking) {
        Box box = s.moveBox();
        List<Box> boxes = collect(world, box.expandTowards(dx, dy, dz));
        float[] m = collide(box, boxes, dx, dy, dz);
        boolean collidedH = m[0] != dx || m[2] != dz;
        if (collidedH && (s.onGround || (dy < 0 && m[1] != dy))) {
            float step = s.stepHeight();
            List<Box> stepBoxes = collect(world, box.expandTowards(dx, step, dz));
            float[] up = collide(box, stepBoxes, dx, step, dz);
            float upOnly = step;
            Box lifted = box.expandTowards(dx, 0, dz);
            for (Box b : stepBoxes) upOnly = lifted.clipY(b, upOnly);
            if (upOnly < step) {
                float[] across = collide(box.offset(0, upOnly, 0), stepBoxes, dx, 0, dz);
                if (across[0] * across[0] + across[2] * across[2] > up[0] * up[0] + up[2] * up[2]) {
                    up = new float[]{across[0], upOnly, across[2]};
                }
            }
            if (up[0] * up[0] + up[2] * up[2] > m[0] * m[0] + m[2] * m[2]) {
                float down = -up[1] + (s.onGround ? 0f : Math.min(dy, 0f));
                Box moved = box.offset(up[0], up[1], up[2]);
                for (Box b : stepBoxes) down = moved.clipY(b, down);
                m = new float[]{up[0], up[1] + down, up[2]};
            }
        }
        float nx = m[0], ny = m[1], nz = m[2];
        if (PBOX) {
            // move the box and read the position back (adding to the position drifts past 1024)
            Box moved = box.offset(nx, ny, nz);
            s.pbox = moved;
            s.pboxHeight = s.boxLag && s.lagHeight > 0 ? s.lagHeight : s.boxHeight();
            s.x = (moved.minX() + moved.maxX()) / 2f;
            s.y = moved.minY();
            s.z = (moved.minZ() + moved.maxZ()) / 2f;
        } else {
            s.pbox = null;
            s.x += nx;
            s.y += ny;
            s.z += nz;
        }
        s.eyeY = s.y + PlayerState.EYE_HEIGHT;
        s.horizontalCollision = nx != dx || nz != dz;
        // sprint ends one tick after a wall hit that keeps less than ~45% of the move
        boolean shallowHit = s.horizontalCollision && nx * nx + ny * ny + nz * nz < SPRINT_KEEP * SPRINT_KEEP * (dx * dx + dy * dy + dz * dz);
        if (s.lastShallowHit && !s.inWater && s.sprinting) { s.sprinting = false; s.sprintEndedByHit = true; }
        s.lastShallowHit = shallowHit;
        s.lastShallowHorizontal = s.horizontalCollision && nx * nx + nz * nz < SPRINT_KEEP * SPRINT_KEEP * (dx * dx + dz * dz);
        s.verticalCollision = ny != dy;
        // a move with no vertical part keeps the ground state
        if (dy != 0) s.onGround = dy < 0 && ny != dy;
        if (s.onGround) s.fallDistance = 0;
        else if (ny < 0) s.fallDistance -= ny;
        if (nx != dx) s.vx = 0;
        if (nz != dz) s.vz = 0;
        if (ny != dy) {
            float bounce = 0;
            BlockType below = supporting(box.offset(0, ny, 0), s, world);
            if (below == null) below = world.type(floor(s.x), floor(s.y - 0.378f), floor(s.z));
            if (s.onGround && !(-s.vy < GRAVITY) && !sneaking) bounce = below.bounciness();
            if (bounce > 0) {
                float speed = -s.vy, impact = (float) Math.sqrt(speed * speed + 2f * GRAVITY * -ny);
                s.vy = bounce * speed + (impact - speed);
            } else s.vy = 0;
        }
    }

    // Bedrock always resolves Y, then X, then Z
    static float[] collide(Box box, List<Box> boxes, float dx, float dy, float dz) {
        float ny = dy;
        for (Box b : boxes) ny = box.clipY(b, ny);
        box = box.offset(0, ny, 0);
        float feet = box.minY();
        var tops = CTX.get().scaffoldTops;
        float nx = dx;
        for (Box b : boxes) if (!scaffoldAbove(tops, b, feet)) nx = box.clipX(b, nx);
        box = box.offset(nx, 0, 0);
        float nz = dz;
        for (Box b : boxes) if (!scaffoldAbove(tops, b, feet)) nz = box.clipZ(b, nz);
        return new float[]{nx, ny, nz};
    }

    private static boolean scaffoldAbove(java.util.IdentityHashMap<Box, Integer> tops, Box b, float feet) {
        if (tops.isEmpty()) return false;
        Integer top = tops.get(b);
        return top != null && !((double) feet > top - 1.0E-5);
    }

    static BlockType supporting(Box box, PlayerState s, BlockWorld world) {
        float px = (box.minX() + box.maxX()) / 2, py = box.minY(), pz = (box.minZ() + box.maxZ()) / 2;
        BlockType best = null;
        double bestD = Double.MAX_VALUE;
        int y0 = floor(box.minY() - 0.001f);
        for (int x = floor(box.minX()); x <= floor(box.maxX() - 1e-6f); x++)
            for (int z = floor(box.minZ()); z <= floor(box.maxZ() - 1e-6f); z++)
                for (int y = y0 - 1; y <= y0; y++)
                    for (Box b : world.collisionBoxes(x, y, z)) {
                        if (Math.abs(b.maxY() - box.minY()) > 1e-4f || b.maxX() <= box.minX() || b.minX() >= box.maxX() || b.maxZ() <= box.minZ() || b.minZ() >= box.maxZ()) continue;
                        double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz, d = dx * dx + dy * dy + dz * dz;
                        if (d < bestD) { bestD = d; best = world.type(x, y, z); }
                    }
        return best;
    }

    static List<Box> collect(BlockWorld world, Box area) {
        List<Box> out = new ArrayList<>();
        Collision c = CTX.get();
        c.scaffoldTops.clear();
        for (int x = floor(area.minX()) - 1; x <= floor(area.maxX()) + 1; x++)
            for (int y = floor(area.minY()) - 1; y <= floor(area.maxY()) + 1; y++)
                for (int z = floor(area.minZ()) - 1; z <= floor(area.maxZ()) + 1; z++) {
                    if (c.fall > 2.5f && isPowder(world, x, y, z)) out.add(new Box(x, y, z, x + 1, y + 0.9f, z + 1));
                    else if (c.boots && isPowder(world, x, y, z) && (double) c.feet > y + 1 - 1.0E-5 && !c.descend) out.add(new Box(x, y, z, x + 1, y + 1, z + 1));
                    else if (isScaffolding(world, x, y, z) && !((double) c.feet > y + 1 - 1.0E-5 && !c.descend)) continue;
                    else if (isScaffolding(world, x, y, z)) {
                        for (Box b : world.collisionBoxes(x, y, z)) { out.add(b); c.scaffoldTops.put(b, y + 1); }
                    }
                    else out.addAll(world.collisionBoxes(x, y, z));
                }
        for (Box b : c.entities)
            if (b.maxX() >= area.minX() && b.minX() <= area.maxX() && b.maxY() >= area.minY() && b.minY() <= area.maxY()
                    && b.maxZ() >= area.minZ() && b.minZ() <= area.maxZ()) out.add(b);
        return out;
    }

    static final float[] SIN = new float[65536];
    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
    }
    static final boolean TABLE_YAW = Boolean.getBoolean("bs.tableyaw");

    // Java Mth.sin: 65536-entry table
    static final int TABLE_INDEX = Integer.getInteger("bs.tableindex", 0);

    static int index(float v) {
        return switch (TABLE_INDEX) {
            case 1 -> (int) Math.floor(v);
            case 2 -> Math.round(v);
            default -> (int) v;
        };
    }

    static float tableSin(float r) {
        return SIN[index(r * 10430.378f) & 65535];
    }

    static final int LOOK_COS = Integer.getInteger("bs.lookcos", 3);

    static float lookCos(float r) {
        return SIN[(index(r * 10430.378f) + 16384) & 65535];
    }

    static float tableCos(float r) {
        return SIN[index(r * 10430.378f + 16384.0f) & 65535];
    }

    static int floor(float v) {
        return (int) Math.floor(v);
    }
}
