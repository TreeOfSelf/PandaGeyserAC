package me.TreeOfSelf.bedrocksim;

public final class PlayerState {
    public static final float EYE_HEIGHT = 1.62f;
    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float SWIM_HEIGHT = 0.6f;
    public static final float SNEAK_HEIGHT = Float.parseFloat(System.getProperty("bs.sneakheight", "1.5"));

    public float x, y, z;
    // eye y, what Bedrock stores and sends (feet = eyeY - 1.62)
    public float eyeY;
    public float vx, vy, vz;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public float movementSpeed = 0.1f;
    public boolean sprinting;
    public int jumpDelay;
    public boolean wasSneaking;
    public boolean lastShallowHit;
    public boolean inWater;
    public boolean sprintEndedByHit;
    public boolean lastShallowHorizontal;
    public boolean swimming;
    public boolean gliding;
    public int glideBoost;
    public float fallDistance;
    public int ticksFrozen;
    // -1 = none
    public int speedAmp = -1, slownessAmp = -1, jumpBoostAmp = -1, levitationAmp = -1, slowFallingAmp = -1;
    public boolean inPowder;
    public boolean swimHold;
    public int forceSwim = -1;
    public boolean glideHold;
    public int blindnessAmp = -1;
    public float food = 20;
    public boolean leatherBoots, freezeImmune;
    public int depthStrider;
    public int swiftSneak;
    public int soulSpeed;
    public boolean usingItem;
    public boolean useNoSlow;
    public boolean lavaFlip;
    public int riptide, spinTicks;
    public boolean boxLag;
    public float lagHeight;
    public boolean attrMode;
    public float attrBase = 0.1f, attrValue = 0.1f;
    public boolean attrSprinting;
    public boolean attrSprintModifier;

    public void serverMovementAttribute(float defaultValue, float value) {
        attrBase = defaultValue;
        attrValue = value;
        attrSprintModifier = false;
    }
    public static final int HORSE = 1, BOAT = 2;
    public int vehicle;
    public float vehicleWidth, vehicleHeight, vehicleStep = 1f, vehicleSpeed, vehicleJumpStrength;
    public int vehicleJumpTicks = -1;
    public float vehicleJumpScale;
    public boolean horseStanding;
    public float vehicleJumpScaleOverride = Float.NaN;
    public float vehicleYaw;
    public float vehicleTurn;
    public int vehiclePaddle = -1;
    public int vehiclePaddleR = -1;
    public int boatPulse = -1;
    public int boatKeys;
    public static final int KEY_W = 1, KEY_S = 2, KEY_A = 4, KEY_D = 8;
    public boolean vehicleJumping;

    public float width() {
        return vehicle != 0 ? vehicleWidth : WIDTH;
    }

    public float stepHeight() {
        return vehicle != 0 ? vehicleStep : Engine.STEP_HEIGHT;
    }

    public float stuckX, stuckY, stuckZ;

    // Bedrock keeps the box as the state and reads the position back as its centre
    public Box pbox;
    public float pboxHeight;

    public Box box() {
        return sized(boxHeight());
    }

    public float boxHeight() {
        if (vehicle != 0) return vehicleHeight;
        return swimming || gliding || spinTicks > 0 ? SWIM_HEIGHT : crouching ? SNEAK_HEIGHT : HEIGHT;
    }

    public Box moveBox() {
        return boxLag && lagHeight > 0 ? sized(lagHeight) : box();
    }

    public boolean crouching;

    public Box standingBox() {
        return sized(vehicle != 0 ? vehicleHeight : HEIGHT);
    }

    private Box sized(float height) {
        if (pbox != null && pboxHeight == height) return pbox;
        float h = width() / 2;
        return new Box(x - h, y, z - h, x + h, y + height, z + h);
    }

    public PlayerState copy() {
        PlayerState s = new PlayerState();
        s.x = x; s.y = y; s.z = z; s.eyeY = eyeY;
        s.vx = vx; s.vy = vy; s.vz = vz;
        s.onGround = onGround;
        s.horizontalCollision = horizontalCollision;
        s.verticalCollision = verticalCollision;
        s.movementSpeed = movementSpeed;
        s.sprinting = sprinting;
        s.jumpDelay = jumpDelay;
        s.wasSneaking = wasSneaking;
        s.crouching = crouching;
        s.lastShallowHit = lastShallowHit;
        s.inWater = inWater; s.sprintEndedByHit = sprintEndedByHit; s.lastShallowHorizontal = lastShallowHorizontal;
        s.swimming = swimming;
        s.gliding = gliding;
        s.glideBoost = glideBoost;
        s.fallDistance = fallDistance;
        s.pbox = pbox;
        s.pboxHeight = pboxHeight;
        s.ticksFrozen = ticksFrozen;
        s.speedAmp = speedAmp; s.slownessAmp = slownessAmp; s.jumpBoostAmp = jumpBoostAmp; s.levitationAmp = levitationAmp; s.slowFallingAmp = slowFallingAmp;
        s.inPowder = inPowder;
        s.blindnessAmp = blindnessAmp; s.food = food; s.depthStrider = depthStrider; s.swiftSneak = swiftSneak; s.soulSpeed = soulSpeed;
        s.leatherBoots = leatherBoots; s.freezeImmune = freezeImmune;
        s.usingItem = usingItem;
        s.useNoSlow = useNoSlow;
        s.riptide = riptide; s.spinTicks = spinTicks; s.boxLag = boxLag; s.lagHeight = lagHeight;
        s.stuckX = stuckX; s.stuckY = stuckY; s.stuckZ = stuckZ;
        s.attrMode = attrMode; s.attrBase = attrBase; s.attrValue = attrValue;
        s.attrSprinting = attrSprinting; s.attrSprintModifier = attrSprintModifier;
        s.vehicle = vehicle; s.vehicleWidth = vehicleWidth; s.vehicleHeight = vehicleHeight; s.vehicleStep = vehicleStep;
        s.vehicleSpeed = vehicleSpeed; s.vehicleJumpStrength = vehicleJumpStrength;
        s.vehicleJumpTicks = vehicleJumpTicks; s.vehicleJumpScale = vehicleJumpScale; s.vehicleJumping = vehicleJumping; s.vehicleYaw = vehicleYaw; s.vehicleTurn = vehicleTurn; s.vehiclePaddle = vehiclePaddle; s.vehiclePaddleR = vehiclePaddleR; s.boatPulse = boatPulse; s.boatKeys = boatKeys; s.horseStanding = horseStanding;
        return s;
    }
}
