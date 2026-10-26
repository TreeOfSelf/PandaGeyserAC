package me.TreeOfSelf.pandageyserac;

import java.util.List;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

final class Inventory {
    static final int GRACE = 2;
    static final int RECENT = 10;
    private static final Set<PlayerAuthInputData> MOVING = Set.of(PlayerAuthInputData.UP, PlayerAuthInputData.DOWN,
            PlayerAuthInputData.LEFT, PlayerAuthInputData.RIGHT, PlayerAuthInputData.UP_LEFT, PlayerAuthInputData.UP_RIGHT,
            PlayerAuthInputData.JUMP_DOWN, PlayerAuthInputData.JUMPING, PlayerAuthInputData.START_JUMPING, PlayerAuthInputData.SPRINT_DOWN,
            PlayerAuthInputData.START_SPRINTING, PlayerAuthInputData.SNEAK_DOWN, PlayerAuthInputData.START_SNEAKING,
            PlayerAuthInputData.WANT_UP, PlayerAuthInputData.WANT_DOWN, PlayerAuthInputData.START_GLIDING, PlayerAuthInputData.START_SWIMMING,
            PlayerAuthInputData.PERFORM_BLOCK_ACTIONS, PlayerAuthInputData.PERFORM_ITEM_INTERACTION, PlayerAuthInputData.MISSED_SWING);

    private final AcPlayer player;
    private int openId = -1;
    private int openInputs;
    private long inputs, lastMovedOpen = Long.MIN_VALUE / 2;
    private int flags;

    Inventory(AcPlayer player) {
        this.player = player;
    }

    private boolean judged() {
        return openId >= 0 && openInputs > GRACE;
    }

    private void open(int id) {
        openId = id;
        openInputs = 0;
    }

    boolean allow(BedrockPacket packet) {
        if (packet instanceof ItemStackRequestPacket p && openId >= 0 && inputs - lastMovedOpen <= RECENT) {
            List<ItemStackRequest> requests = p.getRequests();
            for (int i = 0; i < requests.size(); i++) {
                ItemStackRequest r = requests.get(i);
                flag("inventory click %d while moving with the screen open (%d inputs ago)", r.getRequestId(), inputs - lastMovedOpen);
                if (PandaGeyserAC.enforce) requests.set(i, new ItemStackRequest(r.getRequestId(), new ItemStackRequestAction[0], r.getFilterStrings(), r.getTextProcessingEventOrigin()));
            }
            return true;
        }
        if (packet instanceof InventoryTransactionPacket t && judged() && (t.getTransactionType() == InventoryTransactionType.ITEM_USE
                || t.getTransactionType() == InventoryTransactionType.ITEM_USE_ON_ENTITY)) {
            flag("%s with the screen open", t.getTransactionType());
            return !PandaGeyserAC.enforce;
        }
        return true;
    }

    private void flag(String fmt, Object... args) {
        flags++;
        PandaGeyserAC.log().info(String.format("[%s] %s (%d)%s", player.name(), String.format(fmt, args), flags, PandaGeyserAC.enforce ? ", refused" : ""));
    }

    void onReceive(BedrockPacket packet) {
        if (packet instanceof InteractPacket p && p.getAction() == InteractPacket.Action.OPEN_INVENTORY) {
            player.log("inv interact OPEN_INVENTORY");
            open(0);
        } else if (packet instanceof ContainerClosePacket p) {
            player.log("inv close from client id %d (open %d, %d inputs)", p.getId(), openId, openInputs);
            openId = -1;
        } else if (packet instanceof InventoryTransactionPacket p && p.getTransactionType() == InventoryTransactionType.NORMAL) {
            StringBuilder b = new StringBuilder();
            for (InventoryActionData a : p.getActions()) b.append(' ').append(a.getSource().getType()).append('/').append(a.getSlot());
            player.log("inv transaction NORMAL:%s (open %d, %d inputs)", b, openId, openInputs);
        } else if (packet instanceof ItemStackRequestPacket p) {
            for (ItemStackRequest r : p.getRequests()) {
                StringBuilder b = new StringBuilder();
                for (ItemStackRequestAction a : r.getActions()) b.append(' ').append(a.getType());
                player.log("inv request %d:%s (open %d, %d inputs)", r.getRequestId(), b, openId, openInputs);
            }
        } else if (packet instanceof PlayerAuthInputPacket p) {
            inputs++;
            if (openId < 0) return;
            openInputs++;
            if (openInputs <= GRACE) return;
            boolean moved = p.getMotion().getX() != 0 || p.getMotion().getY() != 0;
            if (!moved) for (PlayerAuthInputData d : p.getInputData()) if (MOVING.contains(d)) { moved = true; break; }
            if (moved) {
                if (inputs - lastMovedOpen > RECENT) player.log("inv moving with screen %d open (%d inputs after the open) %s move %s", openId, openInputs, p.getInputData(), p.getMotion());
                lastMovedOpen = inputs;
            }
        }
    }

    Runnable onSend(BedrockPacket packet) {
        if (packet instanceof ContainerOpenPacket p) {
            player.log("inv open sent id %d type %s", p.getId(), p.getType());
            int id = p.getId();
            return () -> open(id);
        }
        if (packet instanceof ContainerClosePacket p) {
            player.log("inv close sent id %d (open %d)", p.getId(), openId);
            int id = p.getId();
            return () -> { if (openId == id) openId = -1; };
        }
        return null;
    }
}
