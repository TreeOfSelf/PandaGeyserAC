package me.TreeOfSelf.bedrocksim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Replay {
    static final Pattern TICK = Pattern.compile("tick (\\d+) \\[(.*?)\\] move \\(([-\\d.E]+), ([-\\d.E]+)\\) rot \\(([-\\d.E]+), ([-\\d.E]+), [-\\d.E]+\\) client \\(([-\\d.E]+), ([-\\d.E]+), ([-\\d.E]+)\\) delta \\(([-\\d.E]+), ([-\\d.E]+), ([-\\d.E]+)\\)");
    static final Pattern BOOST_SENT = Pattern.compile("glide boost sent duration (\\d+)"), BOOST_ACKED = Pattern.compile("glide boost acked duration (\\d+)");
    static final Pattern ATTR = Pattern.compile("attribute (?:sent )?movement value ([-\\d.E]+) default ([-\\d.E]+)");

    public static void main(String[] a) throws Exception {
        String player = a[1];
        int lag = Integer.parseInt(a[2]);
        boolean verbose = a.length > 3;
        BoxWorld world = new BoxWorld();
        world.fill(980, 280, 980, 1099, 280, 1099, BlockType.get("stone"));
        world.fill(1060, 281, 1040, 1070, 281, 1050, BlockType.get("stone"));
        world.fill(1030, 281, 1060, 1050, 283, 1060, BlockType.get("stone"));
        world.fill(1000, 283, 1000, 1010, 283, 1010, BlockType.get("stone"));
        world.fill(1040, 281, 1082, 1054, 283, 1082, BlockType.get("stone"));

        PlayerState s = new PlayerState();
        s.attrMode = true;
        List<float[]> pendingAttr = new ArrayList<>();
        String prevFlags = null;
        int ticks = 0, off = 0;
        boolean started = false;
        int serverBoost = 0, inFlight = 0, either = 0, rocketWait = -1, eitherUsed = 0;
        List<String> lines = Files.readAllLines(Path.of(a[0]));
        java.util.Set<Integer> awaiting = new java.util.HashSet<>();
        Pattern AWAIT = Pattern.compile(" " + Pattern.quote(player) + " tick (\\d+) awaiting correction ack");
        for (String line : lines) { Matcher w = AWAIT.matcher(line); if (w.find()) awaiting.add(Integer.parseInt(w.group(1))); }
        int skipped = 0;
        boolean onAck = lines.stream().anyMatch(l -> l.contains(" " + player + " attribute acked movement"));
        java.util.ArrayDeque<float[]> sentAttr = new java.util.ArrayDeque<>();
        for (String line : lines) {
            if (!line.contains(" " + player + " ")) continue;
            Matcher m = ATTR.matcher(line);
            if (m.find()) {
                float[] at = {lag, Float.parseFloat(m.group(2)), Float.parseFloat(m.group(1))};
                if (onAck) sentAttr.add(at); else pendingAttr.add(at);
                continue;
            }
            if (onAck && line.contains(" attribute acked movement")) {
                float[] at = sentAttr.poll();
                if (at != null) s.serverMovementAttribute(at[1], at[2]);
                continue;
            }
            if (line.contains("rocket used")) { rocketWait = 40; continue; }
            m = BOOST_SENT.matcher(line);
            if (m.find()) { inFlight++; continue; }
            m = BOOST_ACKED.matcher(line);
            if (m.find()) { inFlight--; int d = Integer.parseInt(m.group(1)); serverBoost = d / 2; either = 3; if (d > 0) rocketWait = -1; continue; }
            m = TICK.matcher(line);
            if (!m.find()) continue;
            String flags = m.group(2);
            float cx = Float.parseFloat(m.group(7)), cy = Float.parseFloat(m.group(8)), cz = Float.parseFloat(m.group(9));
            float dvx = Float.parseFloat(m.group(10)), dvy = Float.parseFloat(m.group(11)), dvz = Float.parseFloat(m.group(12));
            for (var it = pendingAttr.iterator(); it.hasNext(); ) {
                float[] p = it.next();
                if (p[0]-- <= 0) { s.serverMovementAttribute(p[1], p[2]); it.remove(); }
            }
            if (!started || flags.contains("HANDLE_TELEPORT")) {
                s.x = cx; s.eyeY = cy; s.y = cy - PlayerState.EYE_HEIGHT; s.z = cz; s.pbox = null;
                s.vx = dvx; s.vy = dvy; s.vz = dvz;
                started = true;
                continue;
            }
            Input in = new Input(Float.parseFloat(m.group(3)), Float.parseFloat(m.group(4)), Float.parseFloat(m.group(6)), Float.parseFloat(m.group(5)),
                    has(flags, "JUMPING"), has(flags, "SPRINT_DOWN"), has(flags, "START_SPRINTING"), has(flags, "STOP_SPRINTING"), has(flags, "SNEAKING"),
                    has(flags, "START_SWIMMING"), has(flags, "STOP_SWIMMING"), has(flags, "START_GLIDING"), has(flags, "STOP_GLIDING"));
            s.glideBoost = serverBoost;
            PlayerState before = either > 0 || inFlight > 0 || rocketWait >= 0 ? s.copy() : null;
            Engine.tick(s, in, world);
            if (System.getProperty("trace") != null && m.group(1).matches(System.getProperty("trace"))) System.out.println("trace " + m.group(1) + " " + s.x + " " + s.z + " v " + s.vx + " " + s.vz + " ground " + s.onGround + " sneak " + s.wasSneaking + " sprint " + s.sprinting + " glide " + s.gliding + " attr " + s.attrValue + "/" + s.attrBase + " mod " + s.attrSprintModifier + " as " + s.attrSprinting + " speedAmp " + s.speedAmp + " in " + in);
            ticks++;
            float tol = Math.max(1e-4f, 3 * Math.ulp(Math.max(Math.abs(cx), Math.abs(cz))));
            if (before != null && !(Math.abs(cx - s.x) <= tol && Math.abs(cy - s.eyeY) <= tol && Math.abs(cz - s.z) <= tol)) {
                PlayerState alt = before.copy();
                alt.glideBoost = before.glideBoost > 0 ? 0 : 1;
                Engine.tick(alt, in, world);
                if (Math.abs(cx - alt.x) <= tol && Math.abs(cy - alt.eyeY) <= tol && Math.abs(cz - alt.z) <= tol) { s = alt; eitherUsed++; }
            }
            if (serverBoost > 0) serverBoost--;
            s.glideBoost = serverBoost;
            if (either > 0) either--;
            if (rocketWait >= 0) rocketWait--;
            float dx = cx - s.x, dy = cy - s.eyeY, dz = cz - s.z;
            int tickNo = Integer.parseInt(m.group(1));
            boolean rewinding = awaiting.contains(tickNo) || awaiting.contains(tickNo - 1);
            if (rewinding) skipped++;
            if (Math.abs(dx) > tol || Math.abs(dy) > tol || Math.abs(dz) > tol) {
                if (!rewinding) off++;
                if (verbose && !rewinding) System.out.printf("tick %s off %.5f %.5f %.5f speed %.4f sprint %b glide %b boost %d flags %s%n", m.group(1), dx, dy, dz, s.attrValue, s.sprinting, s.gliding, serverBoost, flags);
                s.x = cx; s.eyeY = cy; s.y = cy - PlayerState.EYE_HEIGHT; s.z = cz; s.pbox = null;
                s.vx = dvx; s.vy = dvy; s.vz = dvz;
            }
            prevFlags = flags;
        }
        System.out.println("lag " + lag + ": " + ticks + " ticks, " + off + " off, " + eitherUsed + " either-or, " + skipped + " not judged (setback rewind)");
    }

    static boolean has(String flags, String f) {
        for (String x : flags.split(", ")) if (x.equals(f)) return true;
        return false;
    }
}
