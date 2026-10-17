package ru.domsanta;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ищет место и вставляет схематику domsanta.schem. Все изменённые блоки запоминаются и возвращаются обратно.
 * Сундуки/бочки/печи получают содержимое ровно такое, как записано в схематике.
 */
public class StructureBuilder {

    private record Saved(int x, int y, int z, BlockData data) {}

    private final World world;
    private final Schematic sch;
    private final int ox, oz, baseY;      // угол схематики (мин. x/z) и мировой Y слоя 0 схематики
    private final int shX, shY, shZ;      // клетка шалкера внутри схематики
    private final int tpX, tpY, tpZ;      // точка телепорта (/domsanta tp) внутри схематики
    private final Random rnd = new Random();
    private final Set<Long> seen = new HashSet<>();
    private final List<Saved> saved = new ArrayList<>();
    private final List<Location> containers = new ArrayList<>();

    private StructureBuilder(World world, Schematic sch, FileConfiguration c, int ox, int oz, int baseY) {
        this.world = world;
        this.sch = sch;
        this.ox = ox;
        this.oz = oz;
        this.baseY = baseY;
        this.shX = c.getInt("schematic.shulker-x", 17);
        this.shY = c.getInt("schematic.shulker-y", 1);
        this.shZ = c.getInt("schematic.shulker-z", 13);
        this.tpX = c.getInt("schematic.teleport-x", 19);
        this.tpY = c.getInt("schematic.teleport-y", 1);
        this.tpZ = c.getInt("schematic.teleport-z", 19);
    }

    // ================= поиск места =================

    public static StructureBuilder find(DomSantaPlugin plugin, World world) {
        Schematic sch = plugin.getSchematic();
        if (sch == null) return null;
        FileConfiguration c = plugin.getConfig();
        int xmin = Math.min(c.getInt("location.x-min", -5000), c.getInt("location.x-max", 5000));
        int xmax = Math.max(c.getInt("location.x-min", -5000), c.getInt("location.x-max", 5000));
        int zmin = Math.min(c.getInt("location.z-min", -5000), c.getInt("location.z-max", 5000));
        int zmax = Math.max(c.getInt("location.z-min", -5000), c.getInt("location.z-max", 5000));
        int attempts = c.getInt("location.max-attempts", 40);
        int maxDiff = c.getInt("location.max-height-diff", 6);
        boolean flat = c.getBoolean("location.flatness-check", true);
        int shX = c.getInt("schematic.shulker-x", 17);
        int shZ = c.getInt("schematic.shulker-z", 13);
        int W = sch.width(), L = sch.length(), H = sch.height();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        for (int i = 0; i < attempts; i++) {
            int x = r.nextInt(xmin, xmax + 1);
            int z = r.nextInt(zmin, zmax + 1);
            int ox = x - shX, oz = z - shZ;
            if (!inBorder(world, ox, oz, W, L)) continue;

            // сетка замеров высоты по всей площадке 5x5
            int[] hs = new int[25];
            int n = 0;
            boolean bad = false;
            for (int a = 0; a < 5 && !bad; a++) {
                for (int b = 0; b < 5; b++) {
                    int px = ox + (W - 1) * a / 4;
                    int pz = oz + (L - 1) * b / 4;
                    int hy = height(world, px, pz);
                    if (world.getBlockAt(px, hy, pz).isLiquid()) {
                        bad = true;
                        break;
                    }
                    hs[n++] = hy;
                }
            }
            if (bad) continue;
            int[] sorted = hs.clone();
            Arrays.sort(sorted);
            int base = sorted[12]; // медиана
            if (flat && sorted[24] - sorted[0] > maxDiff) continue;
            if (base < world.getMinHeight() + 16 || base + H + 4 > world.getMaxHeight()) continue;
            if (hasTiles(world, ox - 1, oz - 1, ox + W, oz + L)) continue;
            return new StructureBuilder(world, sch, c, ox, oz, base);
        }
        return null;
    }

    private static int height(World w, int x, int z) {
        return w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    private static boolean inBorder(World w, int ox, int oz, int W, int L) {
        int m = 25;
        return w.getWorldBorder().isInside(new Location(w, ox - m, 64, oz - m))
                && w.getWorldBorder().isInside(new Location(w, ox + W + m, 64, oz - m))
                && w.getWorldBorder().isInside(new Location(w, ox - m, 64, oz + L + m))
                && w.getWorldBorder().isInside(new Location(w, ox + W + m, 64, oz + L + m));
    }

    private static boolean hasTiles(World w, int minX, int minZ, int maxX, int maxZ) {
        for (int chx = minX >> 4; chx <= maxX >> 4; chx++) {
            for (int chz = minZ >> 4; chz <= maxZ >> 4; chz++) {
                for (BlockState st : w.getChunkAt(chx, chz).getTileEntities()) {
                    if (st.getX() >= minX && st.getX() <= maxX && st.getZ() >= minZ && st.getZ() <= maxZ) return true;
                }
            }
        }
        return false;
    }

    // ================= геттеры =================

    public World getWorld() {
        return world;
    }

    /** Блок главного шалкера (внутри дома). */
    public Location getShulkerLocation() {
        return new Location(world, ox + shX, baseY + shY, oz + shZ);
    }

    public Location getTeleportLocation() {
        Location l = new Location(world, ox + tpX + 0.5, baseY + tpY, oz + tpZ + 0.5);
        l.setYaw(180f);
        return l;
    }

    /** Попадает ли точка в прямоугольник схематики (по x/z), расширенный на margin блоков. */
    public boolean isInside(Location l, double margin) {
        if (l.getWorld() != world) return false;
        return l.getX() >= ox - margin && l.getX() < ox + sch.width() + margin
                && l.getZ() >= oz - margin && l.getZ() < oz + sch.length() + margin;
    }

    /** Безопасные точки для стражей: земля снаружи дома на расстоянии 8-13 блоков от шалкера, равномерно по кругу. */
    public List<Location> guardSpawns(int count) {
        int W = sch.width(), H = sch.height(), L = sch.length();
        List<int[]> cand = new ArrayList<>();
        for (int z = 0; z < L; z++) {
            for (int x = 0; x < W; x++) {
                double d = Math.hypot(x - shX, z - shZ);
                if (d < 8 || d > 13) continue;
                int top = -1;
                for (int y = H - 1; y >= 0; y--) {
                    Material m = sch.material(x, y, z);
                    if (m.isAir() || m == Material.SNOW) continue;
                    top = y;
                    break;
                }
                if (top != 0 || !sch.material(x, 0, z).isSolid()) continue; // только чистая земля: без деревьев, забора, крыши, огня
                cand.add(new int[]{x, z});
            }
        }
        List<Location> out = new ArrayList<>();
        for (int i = 0; i < count && !cand.isEmpty(); i++) {
            double want = Math.PI * 2 * i / count;
            int[] best = null;
            double bestDiff = Double.MAX_VALUE;
            for (int[] c : cand) {
                double ang = Math.atan2(c[1] - shZ, c[0] - shX);
                double diff = Math.abs(Math.atan2(Math.sin(ang - want), Math.cos(ang - want)));
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = c;
                }
            }
            cand.remove(best);
            out.add(new Location(world, ox + best[0] + 0.5, baseY + 1.1, oz + best[1] + 0.5));
        }
        return out;
    }

    // ================= постройка =================

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    private void set(int x, int y, int z, BlockData data) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        Block b = world.getBlockAt(x, y, z);
        BlockData old = b.getBlockData();
        if (old.equals(data)) return;
        if (seen.add(key(x, y, z))) saved.add(new Saved(x, y, z, old));
        b.setBlockData(data, false);
    }

    private void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    public void build() {
        int W = sch.width(), H = sch.height(), L = sch.length();

        // высота земли ДО перестройки - нужна, чтобы достроить фундамент там, где земля ниже
        int[][] surf = new int[W][L];
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < L; z++) surf[x][z] = height(world, ox + x, oz + z);
        }

        // вставка схематики (воздух тоже вставляется - так вырубается всё лишнее на площадке)
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < L; z++) {
                for (int x = 0; x < W; x++) {
                    set(ox + x, baseY + y, oz + z, sch.get(x, y, z));
                }
            }
        }

        // фундамент под схематикой, чтобы дом не висел над провалами
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < L; z++) {
                if (sch.material(x, 0, z).isAir()) continue;
                for (int y = baseY - 1; y > surf[x][z] && y >= baseY - 14 && y >= world.getMinHeight(); y--) {
                    set(ox + x, y, oz + z, rnd.nextInt(4) == 0 ? Material.COBBLED_DEEPSLATE : Material.BLACKSTONE);
                }
            }
        }

        // шалкер внутри дома (закрытый; SantaEvent заменит на открытый)
        set(ox + shX, baseY + shY, oz + shZ, Material.CYAN_SHULKER_BOX);

        fillContainers();
    }

    @SuppressWarnings("deprecation")
    private static ItemStack makeItem(Schematic.Item it) {
        Material m = Material.matchMaterial(it.id());
        if (m == null || m.isAir()) return null;
        ItemStack st = new ItemStack(m, Math.max(1, it.count()));
        if (it.snbt() != null) {
            try {
                st = Bukkit.getUnsafe().modifyItemStack(st, it.snbt());
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[DomSanta] Не удалось применить NBT к предмету " + it.id() + ": " + t);
            }
        }
        return st;
    }

    private void fillContainers() {
        for (Schematic.Storage s : sch.storages()) {
            Block b = world.getBlockAt(ox + s.x(), baseY + s.y(), oz + s.z());
            BlockState st = b.getState();
            Inventory inv;
            if (st instanceof Chest ch) inv = ch.getBlockInventory(); // только своя половинка двойного сундука
            else if (st instanceof Container co) inv = co.getInventory();
            else continue;
            for (Schematic.Item it : s.items()) {
                if (it.slot() < 0 || it.slot() >= inv.getSize()) continue;
                ItemStack is = makeItem(it);
                if (is != null) inv.setItem(it.slot(), is);
            }
            containers.add(b.getLocation());
        }
    }

    public void restore() {
        // закрыть инвентари и очистить содержимое, чтобы ничего не выпало/не осталось
        for (Location l : containers) {
            BlockState st = l.getBlock().getState();
            if (st instanceof Container co) {
                Inventory inv = co.getInventory();
                for (HumanEntity h : new ArrayList<>(inv.getViewers())) h.closeInventory();
                inv.clear();
            }
        }
        containers.clear();
        for (Saved s : saved) {
            world.getBlockAt(s.x(), s.y(), s.z()).setBlockData(s.data(), false);
        }
        saved.clear();
        seen.clear();
    }
}
