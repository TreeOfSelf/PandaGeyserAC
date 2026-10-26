package me.TreeOfSelf.bedrocksim;

public final class VehicleEngine {
    private VehicleEngine() {
    }

    public static void tick(PlayerState s, Input in, BlockWorld world) {
        if (s.vehicle == PlayerState.HORSE) horse(s, in, world);
        else if (s.vehicle == PlayerState.BOAT) boat(s, in, world);
    }

    static float wrapDegrees(float d) {
        float f = d % 360.0f;
        if (f >= 180.0f) f -= 360.0f;
        if (f < -180.0f) f += 360.0f;
        return f;
    }

    public static float wrap(float d) {
        return wrapDegrees(d);
    }

    static void horse(PlayerState s, Input in, BlockWorld world) {
        Engine.CTX.get().set(0, s.box().minY(), false, false);
        if (in.jumping() && s.onGround && !s.vehicleJumping) {
            s.vehicleJumpTicks = s.vehicleJumpTicks < 0 ? 0 : s.vehicleJumpTicks + 1;
        } else if (s.vehicleJumpTicks >= 0) {
            int t = s.vehicleJumpTicks;
            float scale = t < 10 ? t * 0.1f : 0.8f + 2.0f / (t - 9) * 0.1f;
            int power = (int) Math.floor(scale * 100.0f);
            if (power >= 0) s.vehicleJumpScale = power >= 90 ? 1.0f : 0.4f + 0.4f * power / 90.0f;
            s.vehicleJumpTicks = -1;
        }

        // k = max(0.126, 0.35 x (1 - |d| / 45))
        float diff = wrapDegrees(in.yaw() - s.vehicleYaw);
        float k = Math.max(0.126f, 0.35f * (1f - Math.abs(diff) / 45f));
        s.vehicleYaw = s.vehicleYaw * (1f - k) + (s.vehicleYaw + diff) * k;
        float yawRad = s.vehicleYaw * 0.017453292f;
        float sin = Engine.snapTrig((float) Math.sin(yawRad));
        float cos = Engine.snapTrig((float) Math.cos(yawRad));
        float mx = in.moveX(), mz = in.moveZ();
        float lenSq = mx * mx + mz * mz;
        if (lenSq > 1f) {
            float len = (float) Math.sqrt(lenSq);
            mx /= len;
            mz /= len;
        }
        float strafe = mx * 0.5f, forward = mz;
        if (forward <= 0) forward *= 0.25f;
        if (s.horseStanding && s.onGround && s.vehicleJumpScale == 0) strafe = forward = 0;

        if (s.onGround) {
            s.vehicleJumping = false;
            if (s.vehicleJumpScale > 0 && !s.vehicleJumping) {
                float scale = Float.isNaN(s.vehicleJumpScaleOverride) ? s.vehicleJumpScale : s.vehicleJumpScaleOverride;
                s.vy = s.vehicleJumpStrength * scale * Engine.jumpFactor(s, world);
                s.vehicleJumping = true;
                if (forward > 0) {
                    s.vx += -0.4f * Engine.tableSin(yawRad) * scale;
                    s.vz += 0.4f * Engine.tableCos(yawRad) * scale;
                }
            }
            s.vehicleJumpScale = 0;
        }
        s.vehicleJumpScaleOverride = Float.NaN;

        if (Engine.fluidHeight(s, world, "water", Engine.WATER_PUSH) > 0) {
            float yBefore = s.y;
            Engine.moveRelative(s, strafe, forward, 0.02f, sin, cos);
            Engine.move(s, world, s.vx, s.vy, s.vz, false);
            s.vx *= 0.8f;
            s.vy *= 0.8f;
            s.vz *= 0.8f;
            s.vy -= Engine.GRAVITY / 4f;
            if (s.horizontalCollision && Engine.free(world, s.box().offset(s.vx, s.vy + 0.6f - s.y + yBefore, s.vz))) s.vy = 0.3f;
            return;
        }

        int bx = Engine.floor(s.x), bz = Engine.floor(s.z);
        float blockFriction = s.onGround ? world.type(bx, Engine.floor(s.y - 0.1f), bz).friction() : 1.0f;
        float speed = s.vehicleSpeed;
        float accel = s.onGround ? speed * (0.21600002f / (blockFriction * blockFriction * blockFriction)) : speed * 0.1f;
        Engine.moveRelative(s, strafe, forward, accel, sin, cos);
        Engine.move(s, world, s.vx, s.vy, s.vz, false);
        s.vy -= Engine.GRAVITY;
        s.vy *= Engine.AIR_DRAG;
        float horizontal = blockFriction * 0.91f;
        s.vx *= horizontal;
        s.vz *= horizontal;
    }

    // Bedrock's boat yaw is Java's + 90
    static void boat(PlayerState s, Input in, BlockWorld world) {
        Engine.CTX.get().set(0, s.box().minY(), false, false);
        boolean water = Engine.isWater(world, Engine.floor(s.x), Engine.floor(s.y + 0.1f), Engine.floor(s.z));
        float bf = water ? 0.9f : s.onGround ? world.type(Engine.floor(s.x), Engine.floor(s.y - 0.001f), Engine.floor(s.z)).friction() : 0.05f;
        float friction = water || s.onGround ? bf * bf : bf;
        boolean fast = s.vx * s.vx + s.vz * s.vz > BOAT_FAST * BOAT_FAST;
        s.vx *= friction;
        s.vz *= friction;
        s.vehicleTurn *= friction;
        float[] b = boatInput(s.boatKeys);
        int pads = boatPaddles(s.boatKeys);
        boolean left = (pads & 1) != 0, right = (pads & 2) != 0;
        s.vehiclePaddle = left ? s.vehiclePaddle + 1 : -1;
        s.vehiclePaddleR = right ? s.vehiclePaddleR + 1 : -1;
        if (b != null) {
            boolean pl = left && (s.boatPulse >= 0 ? (s.boatPulse & 1) != 0 : s.vehiclePaddle % 10 == 0);
            boolean pr = right && (s.boatPulse >= 0 ? (s.boatPulse & 2) != 0 : s.vehiclePaddleR % 10 == 0);
            if (s.boatPulse >= 0) {
                if (pl) s.vehiclePaddle = 0;
                if (pr) s.vehiclePaddleR = 0;
            }
            boolean all = pl == left && pr == right, none = !pl && !pr;
            float turn, push0;
            if (all || none) {
                turn = all ? b[2] : b[3];
                push0 = all ? b[0] : b[1];
            } else {
                turn = b[3] + (pl ? STROKE_TURN : -STROKE_TURN);
                push0 = b[1] + (b[0] - b[1]) / 2;
            }
            float scale = bf / 0.6f;
            s.vehicleTurn += turn * (fast ? bf / 0.96f : scale);
            s.vehicleYaw += s.vehicleTurn;
            float push = push0 * scale * (b[2] != 0 ? fast ? 1 / 0.6f : scale : 1);
            float r = s.vehicleYaw * 0.017453292f;
            s.vx += (float) Math.cos(r) * push;
            s.vz += (float) Math.sin(r) * push;
        } else {
            s.vehicleYaw += s.vehicleTurn;
        }
        s.boatPulse = -1;
        Engine.move(s, world, s.vx, s.vy, s.vz, false);
        float top = waterTop(world, s);
        if (!Float.isNaN(top)) s.vy = Math.max(-0.03f, Math.min(0.03f, (top - 0.3f - s.y) * 0.1f));
        // gravity after the move, with air drag
        else s.vy = (s.vy - 0.04f) * 0.98f;
    }

    static float waterTop(BlockWorld world, PlayerState s) {
        int x = Engine.floor(s.x), z = Engine.floor(s.z), y = Engine.floor(s.y + 0.1f);
        if (!Engine.isWater(world, x, y, z)) return Float.NaN;
        for (int i = 0; i < 4 && Engine.isWater(world, x, y + 1, z); i++) y++;
        return y + Engine.height(world, x, y, z);
    }

    public static boolean touchesFluid(PlayerState s, BlockWorld world) {
        Box b = s.box();
        for (int x = Engine.floor(b.minX()); x <= Engine.floor(b.maxX()); x++)
            for (int y = Engine.floor(b.minY() - 0.5f); y <= Engine.floor(b.maxY()); y++)
                for (int z = Engine.floor(b.minZ()); z <= Engine.floor(b.maxZ()); z++)
                    if (Engine.isFluid(world, x, y, z)) return true;
        return false;
    }

    public static boolean touchesLava(PlayerState s, BlockWorld world) {
        Box b = s.box();
        for (int x = Engine.floor(b.minX()); x <= Engine.floor(b.maxX()); x++)
            for (int y = Engine.floor(b.minY() - 0.5f); y <= Engine.floor(b.maxY()); y++)
                for (int z = Engine.floor(b.minZ()); z <= Engine.floor(b.maxZ()); z++)
                    if ("lava".equals(world.type(x, y, z).fluid())) return true;
        return false;
    }

    public static boolean touchesPowderSnow(PlayerState s, BlockWorld world) {
        Box b = s.box();
        for (int x = Engine.floor(b.minX()); x <= Engine.floor(b.maxX()); x++)
            for (int y = Engine.floor(b.minY() - 0.5f); y <= Engine.floor(b.maxY()); y++)
                for (int z = Engine.floor(b.minZ()); z <= Engine.floor(b.maxZ()); z++)
                    if (world.type(x, y, z).name().equals("powder_snow")) return true;
        return false;
    }

    static final float BOAT_RISE = Float.parseFloat(System.getProperty("bs.boatrise", "0.12"));
    static final float BOAT_FLOAT_DEPTH = Float.parseFloat(System.getProperty("bs.boatdepth", "0.25"));
    static final float BOAT_STEP = 0.6f;
    static final float BOAT_SLACK = 0.003f;
    // above this speed a boat turns the fast way
    static final float BOAT_FAST = Float.parseFloat(System.getProperty("bs.boatfast", "0.11111111"));

    public static float boatExcess(PlayerState s, float lastDx, float lastDy, float lastDz, float dx, float dy, float dz, int keys, BlockWorld world) {
        float limit = boatSpeedLimit(s, lastDx, lastDz, keys, world);
        float h = (float) Math.sqrt(dx * dx + dz * dz);
        float excess = h > limit ? h - limit : 0;
        Box b = s.box();
        boolean water = false, bubbles = false;
        float waterTop = Float.NEGATIVE_INFINITY;
        for (int x = Engine.floor(b.minX()); x <= Engine.floor(b.maxX()); x++)
            for (int z = Engine.floor(b.minZ()); z <= Engine.floor(b.maxZ()); z++)
                for (int y = Engine.floor(b.minY() - 1f); y <= Engine.floor(b.maxY() + 0.5f); y++) {
                    BlockType t = world.type(x, y, z);
                    if (t.fluid() != null && t.fluid().equals("water")) {
                        water = true;
                        waterTop = Math.max(waterTop, y + Engine.height(world, x, y, z));
                    }
                    if (t.name().startsWith("bubble_")) bubbles = true;
                }
        if (bubbles) return excess;
        if (water) {
            if (dy > BOAT_RISE) excess += dy - BOAT_RISE;
            float ceiling = waterTop - BOAT_FLOAT_DEPTH;
            if (dy > 0 && b.minY() + dy > ceiling) excess += Math.min(dy, b.minY() + dy - ceiling);
            return excess;
        }
        float allowed = (lastDy - 0.04f) * 0.98f + BOAT_SLACK;
        if (dy > allowed && !(dy <= BOAT_STEP && supported(world, b.offset(dx, dy, dz)))) excess += dy - allowed;
        return excess;
    }

    public static float boatSpeedLimit(PlayerState s, float lastDx, float lastDz, int keys, BlockWorld world) {
        Box b = s.box();
        float maxFriction = 0, maxBf = 0;
        boolean water = false;
        for (int x = Engine.floor(b.minX()); x <= Engine.floor(b.maxX()); x++)
            for (int z = Engine.floor(b.minZ()); z <= Engine.floor(b.maxZ()); z++) {
                for (int y = Engine.floor(b.minY() - 1f); y <= Engine.floor(b.maxY() + 0.5f); y++) {
                    BlockType t = world.type(x, y, z);
                    if (t.fluid() != null && t.fluid().equals("water")) water = true;
                }
                float bf = world.type(x, Engine.floor(b.minY() - 0.001f), z).friction();
                maxBf = Math.max(maxBf, bf);
                maxFriction = Math.max(maxFriction, bf * bf);
            }
        if (water) {
            maxBf = Math.max(maxBf, 0.9f);
            maxFriction = Math.max(maxFriction, 0.81f);
        }
        float[] push = boatInput(keys);
        float pushMax = 0;
        if (push != null) {
            float scale = maxBf / 0.6f;
            pushMax = Math.max(Math.abs(push[0]), Math.abs(push[1])) * scale * (push[2] != 0 ? Math.max(scale, 1 / 0.6f) : 1);
        }
        return (float) Math.sqrt(lastDx * lastDx + lastDz * lastDz) * maxFriction + pushMax + BOAT_SLACK;
    }

    public static boolean supported(BlockWorld world, Box b) {
        Box under = new Box(b.minX(), b.minY() - 0.001f, b.minZ(), b.maxX(), b.minY(), b.maxZ());
        for (int x = Engine.floor(b.minX()); x <= Engine.floor(b.maxX()); x++)
            for (int z = Engine.floor(b.minZ()); z <= Engine.floor(b.maxZ()); z++)
                for (int y = Engine.floor(b.minY() - 1f); y <= Engine.floor(b.minY()); y++)
                    for (Box c : world.collisionBoxes(x, y, z))
                        if (c.maxY() >= under.minY() && c.maxY() <= b.minY() + 0.001f && c.maxX() > b.minX() && c.minX() < b.maxX() && c.maxZ() > b.minZ() && c.minZ() < b.maxZ()) return true;
        return false;
    }

    public static int boatPaddles(int keys) {
        boolean w = (keys & PlayerState.KEY_W) != 0, b = (keys & PlayerState.KEY_S) != 0;
        boolean a = (keys & PlayerState.KEY_A) != 0, d = (keys & PlayerState.KEY_D) != 0;
        if (a && d) a = d = false;
        boolean both = (w || b) && !(w && b && (a || d));
        return (both || d ? 1 : 0) | (both || a ? 2 : 0);
    }

    static final float STROKE_TURN = 1.188f - 1.1484f;

    static float[] boatInput(int keys) {
        boolean w = (keys & PlayerState.KEY_W) != 0, b = (keys & PlayerState.KEY_S) != 0;
        boolean a = (keys & PlayerState.KEY_A) != 0, d = (keys & PlayerState.KEY_D) != 0;
        if (a && d) a = d = false;
        float side = a ? -1 : d ? 1 : 0;
        if (side == 0) {
            if (w) return W;
            if (b) return S;
            return null;
        }
        float[] t = w && b ? WS_SIDE : w ? W_SIDE : b ? S_SIDE : SIDE;
        return new float[]{t[0], t[1], t[2] * side, t[3] * side};
    }

    static final float[] W = {0.0495f, 0.04785f, 0, 0};
    static final float[] S = {-0.007425f, -0.005775f, 0, 0};
    static final float[] SIDE = {0.01485f, 0.014355f, 1.188f, 1.1484f};
    static final float[] W_SIDE = {0.02351445f, 0.0225242f, 1.0288382f, 1.0288385f};
    static final float[] S_SIDE = {-0.00287992f, -0.00188991f, 0.1260064f, 0.1260065f};
    static final float[] WS_SIDE = {0.021001072f, 0.0204965f, 1.6800858f, 1.6404859f};
}
