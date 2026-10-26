package me.TreeOfSelf.bedrocksim;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

public final class Repl {
    public static void main(String[] args) throws Exception {
        BoxWorld world = new BoxWorld();
        PlayerState s = new PlayerState();
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        PrintStream out = new PrintStream(System.out, true);
        String line;
        while ((line = r.readLine()) != null) {
            String[] a = line.trim().split(" ");
            switch (a[0]) {
                case "solid" -> { world.fill(i(a[1]), i(a[2]), i(a[3]), i(a[4]), i(a[5]), i(a[6]), BlockType.get("stone")); out.println("ok"); }
                case "shape" -> {
                    Box b = new Box(f(a[7]), f(a[8]), f(a[9]), f(a[10]), f(a[11]), f(a[12]));
                    world.fill(i(a[1]), i(a[2]), i(a[3]), i(a[4]), i(a[5]), i(a[6]), new BlockType("shape", java.util.List.of(b), 0.6f, 1f, 1f, false, 0));
                    out.println("ok");
                }
                case "glidehold" -> { s.glideHold = true; out.println("ok"); }
                case "shapes" -> {
                    java.util.List<Box> boxes = new java.util.ArrayList<>();
                    for (int k = 7; k + 5 < a.length; k += 6) boxes.add(new Box(f(a[k]), f(a[k + 1]), f(a[k + 2]), f(a[k + 3]), f(a[k + 4]), f(a[k + 5])));
                    world.fill(i(a[1]), i(a[2]), i(a[3]), i(a[4]), i(a[5]), i(a[6]), new BlockType("shape", boxes, 0.6f, 1f, 1f, false, 0));
                    out.println("ok");
                }
                case "block" -> { world.fill(i(a[1]), i(a[2]), i(a[3]), i(a[4]), i(a[5]), i(a[6]), BlockType.get(a[7])); out.println("ok"); }
                case "effect" -> {
                    int amp = i(a[2]);
                    switch (i(a[1])) {
                        case 1 -> s.speedAmp = amp;
                        case 2 -> s.slownessAmp = amp;
                        case 8 -> s.jumpBoostAmp = amp;
                        case 24 -> s.levitationAmp = amp;
                        case 27 -> s.slowFallingAmp = amp;
                        case 15 -> s.blindnessAmp = amp;
                        default -> { }
                    }
                    out.println("ok");
                }
                case "ride" -> {
                    s.vehicle = i(a[1]);
                    if (s.vehicle != 0) {
                        s.vehicleWidth = f(a[2]); s.vehicleHeight = f(a[3]); s.vehicleStep = f(a[4]);
                        s.vehicleSpeed = f(a[5]); s.vehicleJumpStrength = f(a[6]);
                    }
                    s.vehicleJumpTicks = -1; s.vehicleJumpScale = 0; s.vehicleJumping = false; s.vehiclePaddle = -1; s.vehiclePaddleR = -1; s.vehicleTurn = 0;
                    s.pbox = null;
                    out.println("ok");
                }
                case "riptide" -> { s.riptide = i(a[1]); out.println("ok"); }
                case "spin" -> { s.spinTicks = i(a[1]); out.println("ok"); }
                case "usenoslow" -> { s.useNoSlow = a[1].equals("1"); out.println("ok"); }
                case "swiftsneak" -> { s.swiftSneak = Integer.parseInt(a[1]); out.println("ok"); }
                case "soulspeed" -> { s.soulSpeed = Integer.parseInt(a[1]); out.println("ok"); }
                case "strider" -> { s.depthStrider = i(a[1]); out.println("ok"); }
                case "leather" -> { s.leatherBoots = i(a[1]) != 0; s.freezeImmune = i(a[2]) != 0; out.println("ok"); }
                case "food" -> { s.food = f(a[1]); out.println("ok"); }
                case "glide" -> { s.gliding = a[1].equals("1"); out.println("ok"); }
                case "using" -> { s.usingItem = a[1].equals("1"); out.println("ok"); }
                case "boost" -> { s.glideBoost = i(a[1]); out.println("ok"); }
                case "frozen" -> { s.ticksFrozen = i(a[1]); out.println("ok"); }
                case "motion" -> { s.vx = f(a[1]); s.vy = f(a[2]); s.vz = f(a[3]); out.println("ok"); }
                case "speed" -> { s.movementSpeed = f(a[1]); out.println("ok"); }
                case "attrmode" -> { s.attrMode = a[1].equals("1"); out.println("ok"); }
                case "attr" -> { s.serverMovementAttribute(f(a[1]), f(a[2])); out.println("ok"); }
                case "push" -> {
                    Engine.teleportAckTick(s, world);
                    print(out, s);
                }
                case "waterfall" -> {
                    int x0 = i(a[1]), top = i(a[2]), z0 = i(a[3]), y0 = i(a[4]);
                    for (int dx = -8; dx <= 8; dx++)
                        for (int dz = -8; dz <= 8; dz++) {
                            int d = Math.abs(dx) + Math.abs(dz);
                            int toPlus = Math.min(d, Math.min(Math.min(Math.abs(dx - 1) + Math.abs(dz), Math.abs(dx + 1) + Math.abs(dz)),
                                    Math.min(Math.abs(dx) + Math.abs(dz - 1), Math.abs(dx) + Math.abs(dz + 1))));
                            if (d <= 1) {
                                world.fill(x0 + dx, y0, z0 + dz, x0 + dx, top - 1, z0 + dz, BlockType.get("water8"));
                                world.fill(x0 + dx, top, z0 + dz, x0 + dx, top, z0 + dz, BlockType.get(d == 0 ? "water" : "water1"));
                            } else if (toPlus <= 7) {
                                world.fill(x0 + dx, y0, z0 + dz, x0 + dx, y0, z0 + dz, BlockType.get("water" + toPlus));
                            }
                        }
                    out.println("ok");
                }
                case "pour" -> {
                    int x0 = i(a[1]), y0 = i(a[2]), z0 = i(a[3]), step = i(a[5]);
                    for (int dx = -7; dx <= 7; dx++)
                        for (int dz = -7; dz <= 7; dz++) {
                            int l = (Math.abs(dx) + Math.abs(dz)) * step;
                            if (l <= 7) world.fill(x0 + dx, y0, z0 + dz, x0 + dx, y0, z0 + dz, BlockType.get(l == 0 ? a[4] : a[4] + l));
                        }
                    out.println("ok");
                }
                case "reset" -> {
                    s.x = f(a[1]); s.y = f(a[2]); s.z = f(a[3]);
                    s.pbox = null;
                    s.vx = f(a[4]); s.vy = f(a[5]); s.vz = f(a[6]);
                    s.onGround = a[7].equals("1");
                    if (a.length > 9) { s.sprinting = a[8].equals("1"); s.jumpDelay = i(a[9]); }
                    if (a.length > 10) s.wasSneaking = a[10].equals("1");
                    s.eyeY = a.length > 11 ? f(a[11]) : s.y + PlayerState.EYE_HEIGHT;
                    if (a.length > 14) { s.vehicleJumpTicks = i(a[12]); s.vehicleJumpScale = f(a[13]); s.vehicleJumping = a[14].equals("1"); }
                    if (a.length > 15) s.vehicleYaw = f(a[15]);
                    if (a.length > 17) { s.vehicleTurn = f(a[16]); s.vehiclePaddle = i(a[17]); s.vehiclePaddleR = a.length > 18 ? i(a[18]) : s.vehiclePaddle; }
                    print(out, s);
                }
                case "tick" -> {
                    s.boatKeys = a.length > 16 ? i(a[16]) : 0;
                    Engine.tick(s, new Input(f(a[1]), f(a[2]), f(a[3]), f(a[4]), a[5].equals("1"), a[6].equals("1"), a[7].equals("1"), a[8].equals("1"), a[9].equals("1"), a.length > 10 && a[10].equals("1"), a.length > 11 && a[11].equals("1"), a.length > 12 && a[12].equals("1"), a.length > 13 && a[13].equals("1"), a.length > 14 && a[14].equals("1"), a.length > 15 && a[15].equals("1")), world);
                    print(out, s);
                }
                default -> out.println("err " + a[0]);
            }
        }
    }

    static void print(PrintStream out, PlayerState s) {
        out.println(s.x + " " + s.y + " " + s.z + " " + s.vx + " " + s.vy + " " + s.vz + " " + (s.onGround ? 1 : 0) + " " + (s.sprinting ? 1 : 0) + " " + s.jumpDelay + " " + (s.wasSneaking ? 1 : 0) + " " + s.eyeY + " " + (s.gliding ? 1 : 0) + " " + s.vehicleJumpTicks + " " + s.vehicleJumpScale + " " + (s.vehicleJumping ? 1 : 0) + " " + s.vehicleYaw + " " + s.vehicleTurn + " " + s.vehiclePaddle + " " + s.vehiclePaddleR);
    }

    static float f(String v) { return Float.parseFloat(v); }
    static int i(String v) { return Integer.parseInt(v); }
}
