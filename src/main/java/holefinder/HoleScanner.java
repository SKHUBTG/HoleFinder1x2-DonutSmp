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
 * A "pocket" is a connected set of non-bedrock cells (air or breakable blocks)
 * whose every other neighbour is bedrock. Modes:
 *  - straight 1x2 / 1x3 (horizontal or vertical), same as before
 *  - L-shape: a vertical column of 3 cells (stand / mob / storage) plus one
 *    extra cell branching sideways off the TOP cell, for the storage block's
 *    door to be able to open once mined out.
 */
public class HoleScanner {
    public record Hole(List<BlockPos> cells, boolean filled) {
        double dist2(double x, double y, double z) {
            double cx = 0, cy = 0, cz = 0;
            for (BlockPos p : cells) {
                cx += p.getX();
                cy += p.getY();
                cz += p.getZ();
            }
            int n = cells.size();
            cx = cx / n + 0.5;
            cy = cy / n + 0.5;
            cz = cz / n + 0.5;
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
                    BlockPos p = m.immutable();
                    if (c.shape == 3) {
                        checkL(w, p, c, found);
                        continue;
                    }
                    if (!cellOk(w.getBlockState(m), c)) continue;
                    int minLen = c.shape == 1 ? 3 : 2;
                    int maxLen = c.shape == 0 ? 2 : 3;
                    for (int len = minLen; len <= maxLen; len++) {
                        if (c.mode != 1) {
                            checkStraight(w, p, Direction.EAST, len, c, found);
                            checkStraight(w, p, Direction.SOUTH, len, c, found);
                        }
                        if (c.mode != 0) checkStraight(w, p, Direction.UP, len, c, found);
                    }
                }
            }
        }
        if (found.isEmpty()) results.remove(key);
        else results.put(key, found);
    }

    /** Can this block be part of a pocket? */
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

    private static boolean bedrockOnlyRule(Config c) {
        return c.cellMode != 1 || c.onlyBedrock;
    }

    /** All neighbours of `cell` except the other pocket cells must be bedrock (or non-air if that rule is off). */
    private static boolean wallsOk(ClientLevel w, BlockPos cell, Set<BlockPos> internal, Config c) {
        boolean bedrockOnly = bedrockOnlyRule(c);
        for (Direction d : Direction.values()) {
            BlockPos n = cell.relative(d);
            if (internal.contains(n)) continue;
            BlockState s = state(w, n);
            if (s == null) return false;
            if (bedrockOnly) {
                if (!s.is(Blocks.BEDROCK)) return false;
            } else if (s.isAir() || !s.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void checkStraight(ClientLevel w, BlockPos a, Direction dir, int len, Config c, List<Hole> out) {
        List<BlockPos> cells = new ArrayList<>();
        boolean filled = false;
        for (int i = 0; i < len; i++) {
            BlockPos cell = a.relative(dir, i);
            BlockState s = state(w, cell);
            if (s == null || !cellOk(s, c)) return;
            if (!s.isAir()) filled = true;
            cells.add(cell);
        }
        Set<BlockPos> set = new HashSet<>(cells);
        for (BlockPos cell : cells) if (!wallsOk(w, cell, set, c)) return;
        if (c.cellMode == 2 && !filled) return;
        out.add(new Hole(cells, filled));
    }

    /**
     * Vertical column of 3 (base a, a.up, a.up.up) plus one extra cell branching
     * horizontally off the TOP cell, in any of the 4 horizontal directions.
     */
    private static void checkL(ClientLevel w, BlockPos a, Config c, List<Hole> out) {
        BlockPos p0 = a, p1 = a.above(), p2 = a.above(2);
        BlockState s0 = state(w, p0), s1 = state(w, p1), s2 = state(w, p2);
        if (s0 == null || s1 == null || s2 == null) return;
        if (!cellOk(s0, c) || !cellOk(s1, c) || !cellOk(s2, c)) return;
        for (Direction hd : Direction.Plane.HORIZONTAL) {
            BlockPos p3 = p2.relative(hd);
            BlockState s3 = state(w, p3);
            if (s3 == null || !cellOk(s3, c)) continue;
            List<BlockPos> cells = List.of(p0, p1, p2, p3);
            Set<BlockPos> set = new HashSet<>(cells);
            boolean ok = true;
            for (BlockPos cell : cells) {
                if (!wallsOk(w, cell, set, c)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            boolean filled = !s0.isAir() || !s1.isAir() || !s2.isAir() || !s3.isAir();
            if (c.cellMode == 2 && !filled) continue;
            out.add(new Hole(cells, filled));
            return;
        }
    }
}
