package me.TreeOfSelf.pandageyserac;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.BlockUtils;
import java.util.List;

final class FastBreak {
    private static final int HASTE_HOLD = 40;
    // loose on purpose: a 10% faster break is no real advantage
    private static final float NEED = 0.9F;

    private final AcPlayer player;
    private final GeyserSession session;
    private Vector3i pos;
    private BlockState state;
    private Vector3i lastBroken;
    private int restoredInput = -1;
    private float progress;
    private int inputs, hasteMax, hasteSince, fatigue, fatigueRaised = Integer.MIN_VALUE / 2;
    private long breaks, flags;

    FastBreak(AcPlayer player, GeyserSession session) {
        this.player = player;
        this.session = session;
    }

    void input(PlayerAuthInputPacket p) {
        inputs++;
        var effects = session.getEffectCache();
        int haste = Math.max(effects.getHaste(), effects.getConduitPower());
        if (haste >= hasteMax || inputs - hasteSince > HASTE_HOLD) { hasteMax = haste; hasteSince = inputs; }
        if (effects.getMiningFatigue() > fatigue) fatigueRaised = inputs;
        fatigue = effects.getMiningFatigue();
        List<PlayerBlockActionData> actions = p.getPlayerActions();
        if (p.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS) && actions != null) {
            for (int i = 0; i < actions.size(); i++) {
                PlayerBlockActionData a = actions.get(i);
                switch (a.getAction()) {
                    case START_BREAK -> start(a.getBlockPosition(), false);
                    case BLOCK_CONTINUE_DESTROY -> { if (!a.getBlockPosition().equals(pos)) start(a.getBlockPosition(), true); }
                    case ABORT_BREAK -> pos = null;
                    case BLOCK_PREDICT_DESTROY -> {
                        if (pos == null || !a.getBlockPosition().equals(pos)) break;
                        lastBroken = pos;
                        restoredInput = -1;
                        if (!predict(a)) {
                            PlayerBlockActionData abort = new PlayerBlockActionData();
                            abort.setAction(PlayerActionType.ABORT_BREAK);
                            abort.setBlockPosition(a.getBlockPosition());
                            abort.setFace(a.getFace());
                            actions.set(i, abort);
                            BlockUtils.stopBreakAndRestoreBlock(session, pos, state);
                        }
                        pos = null;
                    }
                    default -> { }
                }
            }
        }
        if (pos != null) progress += perTick();
    }

    private void start(Vector3i at, boolean held) {
        pos = at;
        state = session.getGeyser().getWorldManager().blockAt(session, at);
        progress = 0;
        if (held && at.equals(lastBroken) && restoredInput >= 0 && inputs - restoredInput <= HASTE_HOLD) progress = perTick() * (inputs - restoredInput);
    }

    void restored(Vector3i at) {
        if (at.equals(lastBroken)) restoredInput = inputs + 1;
    }

    private float perTick() {
        float p = BlockUtils.getBlockMiningProgressPerTick(session, state.block(), session.getPlayerInventory().getItemInHand());
        return hasteMax > 0 ? (float) (p * bedrockHaste(shownHaste(hasteMax)) / (1 + 0.2 * hasteMax)) : p;
    }

    // Java refuses a break below 70% of its progress (1 / 0.7 = 1.43)
    private static final double HASTE_MAX_RATIO = 1.3;

    // Bedrock: (1 + 0.2 L) x 1.2^L, Java: (1 + 0.2 L)
    static double bedrockHaste(int level) {
        return level <= 0 ? 1 : (1 + 0.2 * level) * Math.pow(1.2, level);
    }

    static int shownHaste(int level) {
        int shown = 0;
        while (shown < level && bedrockHaste(shown + 1) <= HASTE_MAX_RATIO * (1 + 0.2 * level)) shown++;
        return shown;
    }

    private boolean predict(PlayerBlockActionData a) {
        breaks++;
        float total = progress + perTick();
        boolean early = total < NEED && inputs - fatigueRaised > HASTE_HOLD && !session.isInstabuild();
        player.log("break %s at %s progress %.4f (%.4f per tick, haste %d, ground %b)%s", state.block().javaIdentifier().value(), pos, total, perTick(), hasteMax,
                session.getPlayerEntity().isOnGround(), early ? " EARLY" : "");
        if (!early) return true;
        flags++;
        PandaGeyserAC.log().info(String.format("[%s] fast break: %s at %s after %.0f%% of its mining time (%d of %d breaks)%s", player.name(), state.block().javaIdentifier().value(),
                pos, total * 100, flags, breaks, PandaGeyserAC.enforce ? ", block restored" : ""));
        return !PandaGeyserAC.enforce;
    }
}
