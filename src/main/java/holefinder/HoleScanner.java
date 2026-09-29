package holefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Purely passive: reads blocks of chunks the server already sent. No packets.
 *
 * Finds 1x2 pockets whose two inner cells are NOT bedrock (air or any breakable block)
 * and whose 10 surrounding blocks are ALL bedrock. So if the cells are filled with stone,
 * you can mine them out and get a pure bedrock 1x2 box.
 */
public class HoleScanner {
    public record Hole(BlockPos a, BlockPos b, boolean filled) {
        double dist2(double x, double y, double z) {
            double cx = (a.getX() + b.getX()) / 2.0 + 0.5;
            double cy = (a.getY() + b.getY()) / 2.0 + 0.5;
            double cz = (a.getZ() + b.getZ()) / 2.0 + 0.5;
            return (cx - x) * (cx - x) + (cy - y) * (cy - y) + (cz - z) * (cz - z);
        }
    }

    private record CP(int x, int z) {
        long key() {
            return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
        }
    }

    private static final Map<Long, List<Hole>> results = new ConcurrentHashMap<>();
    private static final ArrayDeque<CP> queue = new ArrayDeque<>();
    private static ClientLevel lastLevel;
    private static int timer;

    public static void tick(Minecraft mc) {
        ClientLevel w = mc.level;
        Config c = Config.I;
        if (w == null || mc.player == null || !c.enabled) {
            results.clear();
            queue.clear();
            lastLevel = null;
            return;
        }
        if (w != lastLevel) {
            lastLevel = w;
            results.clear();
            queue.clear();
            timer = 0;
        }
        if (queue.isEmpty() && --timer <= 0) {
            timer = c.rescanTicks;
            int cr = (c.radius >> 4) + 1;
            BlockPos pp = mc.player.blockPosition();
            int pcx = pp.getX() >> 4, pcz = pp.getZ() >> 4;
            results.keySet().removeIf(k -> Math.abs((int) k.longValue() - pcx) > cr
                    || Math.abs((int) (k >>> 32) - pcz) > cr);
            List<CP> list = new ArrayList<>();
            for (int dx = -cr; dx <= cr; dx++)
                for (int dz = -cr; dz <= cr; dz++)
                    list.add(new CP(pcx + dx, pcz + dz));
            list.sort(Comparator.comparingInt(cp -> (cp.x() - pcx) * (cp.x() - pcx) + (cp.z() - pcz) * (cp.z() - pcz)));
            queue.addAll(list);
        }
        for (int i = 0; i < c.chunksPerTick && !queue.isEmpty(); i++) {
            scanChunk(w, queue.poll(), c);
        }
    }

    public static List<Hole> nearest(double x, double y, double z, Config c) {
        double r2 = (double) c.radius * c.radius;
        List<Hole> all = new ArrayList<>();
        for (List<Hole> l : results.values())
            for (Hole h : l)
                if (h.dist2(x, y, z) <= r2) all.add(h);
        all.sort(Comparator.comparingDouble(h -> h.dist2(x, y, z)));
        return all.size() > c.maxHoles ? new ArrayList<>(all.subList(0, c.maxHoles)) : all;
    }

    private static void scanChunk(ClientLevel w, CP cp, Config c) {
        long key = cp.key();
        if (!w.getChunkSource().hasChunk(cp.x(), cp.z())) {
            results.remove(key);
            return;
        }
        int y0 = Math.max(c.minY, w.getMinY());
        int y1 = Math.min(c.maxY, w.getMaxY());
        int sx = cp.x() << 4, sz = cp.z() << 4;
        List<Hole> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = sx; x < sx + 16; x++) {
            for (int z = sz; z < sz + 16; z++) {
                for (int y = y0; y <= y1; y++) {
                    m.set(x, y, z);
                    if (!cellOk(w.getBlockState(m), c)) continue;
                    BlockPos p = m.immutable();
                    for (int len = 2; len <= 3; len++) {
                        if (len == 2 && c.length == 1) continue;
                        if (len == 3 && c.length == 0) continue;
                        if (c.mode != 1) {
                            check(w, p, Direction.EAST, len, c, found);
                            check(w, p, Direction.SOUTH, len, c, found);
                        }
                        if (c.mode != 0) check(w, p, Direction.UP, len, c, found);
                    }
                }
            }
        }
        if (found.isEmpty()) results.remove(key);
        else results.put(key, found);
    }

    /** Can this block be an inner cell of the pocket? */
    private static boolean cellOk(BlockState s, Config c) {
        if (s.is(Blocks.BEDROCK)) return false;
        if (c.cellMode == 1) return s.isAir();
        return true;
    }

    private static BlockState state(ClientLevel w, BlockPos p) {
        if (p.getY() < w.getMinY() || p.getY() > w.getMaxY()) return null;
        if (!w.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
        return w.getBlockState(p);
    }

    private static boolean wallsOk(ClientLevel w, BlockPos cell, Direction skipA, Direction skipB, Config c) {
        boolean bedrockOnly = c.cellMode != 1 || c.onlyBedrock;
        for (Direction d : Direction.values()) {
            if (d == skipA || d == skipB) continue;
            BlockState s = state(w, cell.relative(d));
            if (s == null) return false;
            if (bedrockOnly) {
                if (!s.is(Blocks.BEDROCK)) return false;
            } else if (s.isAir() || !s.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Checks a straight pocket of `len` cells starting at a going in dir. */
    private static void check(ClientLevel w, BlockPos a, Direction dir, int len, Config c, List<Hole> out) {
        boolean filled = false;
        BlockPos last = a;
        for (int i = 0; i < len; i++) {
            BlockPos cell = a.relative(dir, i);
            BlockState s = state(w, cell);
            if (s == null || !cellOk(s, c)) return;
            Direction next = i < len - 1 ? dir : null;
            Direction prev = i > 0 ? dir.getOpposite() : null;
            if (!wallsOk(w, cell, next, prev, c)) return;
            if (!s.isAir()) filled = true;
            last = cell;
        }
        if (c.cellMode == 2 && !filled) return;
        out.add(new Hole(a, last, filled));
    }
}
