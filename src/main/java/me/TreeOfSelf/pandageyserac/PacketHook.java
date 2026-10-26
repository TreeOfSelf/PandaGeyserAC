/*
 * Adapted from Boar (https://github.com/opencollab-incubator/Boar): ac.boar.protocol.BoarHandlerAdaptor,
 * ac.boar.protocol.BoarBatchAcknowledger, ac.boar.anticheat.util.LatencyUtil and ac.boar.geyser.player.GeyserPlayerManager.
 *
 * MIT License
 *
 * Copyright (c) 2026 oryxel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package me.TreeOfSelf.pandageyserac;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.UpstreamSession;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class PacketHook extends ChannelDuplexHandler {
    static final String NAME = "pandageyserac-hook";
    // clients answer with the id times this; PlayStation x1000
    static final long MAGNITUDE = 1_000_000L, MAGNITUDE_PS = 1_000L;
    private static final AtomicLong nextId = new AtomicLong(1_000_000_000L);

    static final long PING_INTERVAL = 50_000_000L;
    // unanswered pings would keep acked changes at their old, more lenient state
    static final long PING_TIMEOUT = 20_000_000_000L;

    private record Ping(long sentNanos, List<Runnable> changes) { }

    private Object held;
    private long heldSince;
    private final ArrayDeque<Object> afterHeld = new ArrayDeque<>();
    static final long HOLD_TIMEOUT = 500_000_000L;

    private final AcPlayer player;
    private List<Runnable> batch = new ArrayList<>();
    private final Map<Long, Ping> inFlight = new LinkedHashMap<>();
    private long lastPing;
    private ScheduledFuture<?> clock;

    PacketHook(AcPlayer player) {
        this.player = player;
    }

    static void install(GeyserSession session, AcPlayer player) {
        Channel channel = bedrockSession(session).getPeer().getChannel();
        channel.pipeline().addAfter(BedrockPacketCodec.NAME, NAME, new PacketHook(player));
    }

    static BedrockServerSession bedrockSession(GeyserSession session) {
        try {
            Field upstream = GeyserSession.class.getDeclaredField("upstream");
            upstream.setAccessible(true);
            Field inner = UpstreamSession.class.getDeclaredField("session");
            inner.setAccessible(true);
            return (BedrockServerSession) inner.get(upstream.get(session));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get the BedrockServerSession of " + session.bedrockUsername(), e);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        clock = ctx.executor().scheduleAtFixedRate(() -> {
            if (!inFlight.isEmpty() && System.nanoTime() - inFlight.values().iterator().next().sentNanos() > PING_TIMEOUT) player.timedOut();
            if (held != null && System.nanoTime() - heldSince > HOLD_TIMEOUT) {
                try {
                    release(ctx, null);
                } catch (Exception e) {
                    PandaGeyserAC.log().error("[" + player.name() + "] held attack", e);
                }
            }
            if (System.nanoTime() - lastPing < PING_INTERVAL || !ctx.channel().isActive()) return;
            ping(ctx, List.of());
            ctx.flush();
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.NANOSECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (clock != null) clock.cancel(false);
        if (held != null) ReferenceCountUtil.release(held);
        held = null;
        afterHeld.forEach(ReferenceCountUtil::release);
        afterHeld.clear();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BedrockPacketWrapper w && w.getPacket() instanceof NetworkStackLatencyPacket nsl && acknowledged(nsl.getTimestamp())) {
            // our own ping: Geyser never sees it
            ReferenceCountUtil.release(msg);
            return;
        }
        if (held != null) {
            if (msg instanceof BedrockPacketWrapper w && w.getPacket() instanceof PlayerAuthInputPacket p) release(ctx, p);
            if (held != null) {
                afterHeld.addLast(msg);
                return;
            }
        }
        receive(ctx, msg);
    }

    private void receive(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BedrockPacketWrapper w && w.getPacket() != null) {
            long start = System.nanoTime();
            try {
                Reach.Verdict v = player.refuse(w.getPacket());
                if (v == Reach.Verdict.REFUSE) {
                    ReferenceCountUtil.release(msg);
                    return;
                }
                if (v == Reach.Verdict.HOLD) {
                    held = msg;
                    heldSince = System.nanoTime();
                    return;
                }
                player.onReceive(w.getPacket());
            } catch (Throwable t) {
                PandaGeyserAC.log().error("[" + player.name() + "] receive " + w.getPacket().getClass().getSimpleName(), t);
            } finally {
                Cost.add(System.nanoTime() - start, w.getPacket() instanceof PlayerAuthInputPacket);
            }
        }
        super.channelRead(ctx, msg);
    }

    private void release(ChannelHandlerContext ctx, PlayerAuthInputPacket p) throws Exception {
        Object msg = held;
        held = null;
        boolean pass;
        try {
            pass = p != null ? player.heldAttack(p) : player.heldAttackTimedOut();
        } catch (Throwable t) {
            PandaGeyserAC.log().error("[" + player.name() + "] held attack", t);
            pass = true;
        }
        if (pass) {
            try {
                player.onReceive(((BedrockPacketWrapper) msg).getPacket());
            } catch (Throwable t) {
                PandaGeyserAC.log().error("[" + player.name() + "] receive held attack", t);
            }
            super.channelRead(ctx, msg);
        } else ReferenceCountUtil.release(msg);
        while (held == null && !afterHeld.isEmpty()) receive(ctx, afterHeld.pollFirst());
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof BedrockPacketWrapper w && w.getPacket() != null && !(w.getPacket() instanceof NetworkStackLatencyPacket)) {
            try {
                Runnable change = player.onSend(w.getPacket());
                if (change != null) batch.add(change);
            } catch (Throwable t) {
                PandaGeyserAC.log().error("[" + player.name() + "] send " + w.getPacket().getClass().getSimpleName(), t);
            }
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (!batch.isEmpty() && ctx.channel().isActive()) {
            ping(ctx, batch);
            batch = new ArrayList<>();
        }
        super.flush(ctx);
    }

    private void ping(ChannelHandlerContext ctx, List<Runnable> changes) {
        long id = nextId.getAndIncrement();
        lastPing = System.nanoTime();
        inFlight.put(id, new Ping(lastPing, changes));
        player.lastPingId = id;
        NetworkStackLatencyPacket nsl = new NetworkStackLatencyPacket();
        nsl.setTimestamp(id);
        nsl.setFromServer(true);
        // not the void promise: a failed write there makes Geyser disconnect the session
        ctx.write(BedrockPacketWrapper.create(0, 0, 0, nsl, null), ctx.newPromise());
    }

    private boolean acknowledged(long timestamp) {
        long id = timestamp % MAGNITUDE == 0 && inFlight.containsKey(timestamp / MAGNITUDE) ? timestamp / MAGNITUDE
                : timestamp % MAGNITUDE_PS == 0 && inFlight.containsKey(timestamp / MAGNITUDE_PS) ? timestamp / MAGNITUDE_PS : -1;
        if (id < 0) return false;
        for (Iterator<Map.Entry<Long, Ping>> it = inFlight.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Ping> e = it.next();
            it.remove();
            player.onClock(e.getValue().sentNanos());
            player.onPingAcked(e.getKey());
            for (Runnable r : e.getValue().changes()) {
                try {
                    r.run();
                } catch (Throwable t) {
                    PandaGeyserAC.log().error("[" + player.name() + "] ack", t);
                }
            }
            if (e.getKey() == id) break;
        }
        return true;
    }
}
