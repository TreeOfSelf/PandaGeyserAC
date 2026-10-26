package me.TreeOfSelf.pandageyserac;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.TreeOfSelf.pandageyserac.chunk.ChunkDecoder;
import me.TreeOfSelf.pandageyserac.chunk.BlockStorage;
import me.TreeOfSelf.pandageyserac.chunk.ChunkSection;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ClientWorld {
    private static final Map<BlockMappings, Int2IntOpenHashMap> BEDROCK_TO_JAVA = new IdentityHashMap<>();

    private final BlockMappings mappings;
    private final Int2IntOpenHashMap bedrockToJava;
    private final int airId, waterId;
    private final Long2ObjectOpenHashMap<ChunkSection[]> chunks = new Long2ObjectOpenHashMap<>();
    private int minY = BedrockDimension.OVERWORLD.minY(), height = BedrockDimension.OVERWORLD.height();
    static final int UNCERTAIN = -2;
    private final Long2IntOpenHashMap predicted = new Long2IntOpenHashMap(), predictedUntil = new Long2IntOpenHashMap();

    public ClientWorld(BlockMappings mappings) {
        this.mappings = mappings;
        this.airId = mappings.getBedrockAir().getRuntimeId();
        this.waterId = mappings.getBedrockWater().getRuntimeId();
        synchronized (BEDROCK_TO_JAVA) {
            this.bedrockToJava = BEDROCK_TO_JAVA.computeIfAbsent(mappings, m -> {
                GeyserBedrockBlock[] javaToBedrock = m.getJavaToBedrockBlocks();
                Int2IntOpenHashMap map = new Int2IntOpenHashMap(javaToBedrock.length);
                for (int i = javaToBedrock.length - 1; i >= 0; i--) map.put(javaToBedrock[i].getRuntimeId(), i);
                return map;
            });
        }
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    Runnable decode(LevelChunkPacket p) {
        if (p.isCachingEnabled() || p.isRequestSubChunks() || p.getSubChunksLength() < 0) return null;
        int dimMinY = minYOf(p.getDimension()), dimHeight = heightOf(p.getDimension());
        ChunkSection[] sections = new ChunkSection[dimHeight >> 4];
        ByteBuf buf = p.getData().retainedDuplicate();
        try {
            for (int i = 0; i < p.getSubChunksLength(); i++) {
                ChunkDecoder.DecodedSubChunk d = ChunkDecoder.readSubChunk(buf, airId, i, dimMinY);
                if (d.sectionY() >= 0 && d.sectionY() < sections.length) sections[d.sectionY()] = d.section();
            }
        } catch (Exception e) {
        } finally {
            buf.release();
        }
        long k = key(p.getChunkX(), p.getChunkZ());
        return () -> chunks.put(k, sections);
    }

    void dimension(int bedrockId) {
        chunks.clear();
        predicted.clear();
        predictedUntil.clear();
        minY = minYOf(bedrockId);
        height = heightOf(bedrockId);
    }

    static int minYOf(int id) {
        return id == BedrockDimension.OVERWORLD_ID ? BedrockDimension.OVERWORLD.minY() : id == BedrockDimension.END_ID ? BedrockDimension.THE_END.minY() : BedrockDimension.THE_NETHER.minY();
    }

    static int heightOf(int id) {
        return id == BedrockDimension.OVERWORLD_ID ? BedrockDimension.OVERWORLD.height() : id == BedrockDimension.END_ID ? BedrockDimension.THE_END.height() : BedrockDimension.THE_NETHER.height();
    }

    void setBlock(Vector3i pos, int layer, int runtimeId) {
        ChunkSection[] sections = chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        if (sections == null) return;
        int sy = (pos.getY() - minY) >> 4;
        if (sy < 0 || sy >= sections.length) return;
        if (sections[sy] == null) sections[sy] = new ChunkSection(airId);
        BlockStorage[] layers = sections[sy].storage();
        if (layer >= layers.length || layers[layer] == null) {
            BlockStorage[] grown = java.util.Arrays.copyOf(layers, Math.max(layers.length, layer + 1));
            for (int i = 0; i <= layer; i++) if (grown[i] == null) grown[i] = new BlockStorage(airId);
            sections[sy] = new ChunkSection(grown);
        }
        if (layer == 0) { predicted.remove(posKey(pos.getX(), pos.getY(), pos.getZ())); predictedUntil.remove(posKey(pos.getX(), pos.getY(), pos.getZ())); }
        int old = sections[sy].getFullBlock(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, layer);
        sections[sy].setFullBlock(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, layer, runtimeId);
        if (old != runtimeId && recent.size() < 4096) recent.add(new Change(sections[sy], pos, pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, layer, old, runtimeId));
    }

    record Change(ChunkSection section, Vector3i pos, int x, int y, int z, int layer, int before, int after) {
        void set(boolean late) { section.setFullBlock(x, y, z, layer, late ? before : after); }
    }

    private final List<Change> recent = new ArrayList<>();

    List<Change> takeRecent() {
        if (recent.isEmpty()) return List.of();
        List<Change> r = List.copyOf(recent);
        recent.clear();
        return r;
    }

    static void setRecent(List<Change> changes, boolean late) {
        if (late) for (int i = changes.size() - 1; i >= 0; i--) changes.get(i).set(true);
        else for (Change c : changes) c.set(false);
    }

    boolean isLoaded(int x, int z) {
        return chunks.containsKey(key(x >> 4, z >> 4));
    }

    int bedrockAt(int x, int y, int z, int layer) {
        ChunkSection[] sections = chunks.get(key(x >> 4, z >> 4));
        if (sections == null) return -1;
        int sy = (y - minY) >> 4;
        if (layer == 0 && !predicted.isEmpty()) {
            long k = posKey(x, y, z);
            if (predicted.containsKey(k)) return predicted.get(k);
        }
        if (sy < 0 || sy >= sections.length || sections[sy] == null) return airId;
        int id = sections[sy].getFullBlock(x & 15, y & 15, z & 15, layer);
        return id == Integer.MIN_VALUE ? airId : id;
    }

    static long posKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    void predict(Vector3i pos, int runtimeId, int until) {
        long k = posKey(pos.getX(), pos.getY(), pos.getZ());
        predicted.put(k, runtimeId);
        predictedUntil.put(k, until);
    }

    void predictAir(Vector3i pos, int until) {
        predict(pos, airId, until);
    }

    void expirePredicted(int now) {
        if (predictedUntil.isEmpty()) return;
        predictedUntil.long2IntEntrySet().removeIf(e -> {
            if (e.getIntValue() > now) return false;
            predicted.remove(e.getLongKey());
            return true;
        });
    }

    boolean isAir(int runtimeId) {
        return runtimeId == airId;
    }

    int javaId(int runtimeId) {
        return bedrockToJava.getOrDefault(runtimeId, 0);
    }

    boolean isWater(int runtimeId) {
        return runtimeId == waterId;
    }

    void forget(int playerChunkX, int playerChunkZ, int radius) {
        chunks.long2ObjectEntrySet().removeIf(e -> {
            int cx = (int) (e.getLongKey() >> 32), cz = (int) e.getLongKey();
            return Math.abs(cx - playerChunkX) > radius || Math.abs(cz - playerChunkZ) > radius;
        });
    }
}
