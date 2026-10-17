package ru.domsanta;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Схематика формата Sponge v2 (.schem из WorldEdit/FAWE). Читается без зависимостей. */
public final class Schematic {

    public record Item(int slot, String id, int count, String snbt) {}

    /** Блок с инвентарём (сундук, бочка, печь...) и его предметы. */
    public record Storage(int x, int y, int z, List<Item> items) {}

    private final int width, height, length;
    private final BlockData[] palette;
    private final int[] data; // порядок: (y * length + z) * width + x
    private final List<Storage> storages;

    private Schematic(int w, int h, int l, BlockData[] palette, int[] data, List<Storage> storages) {
        this.width = w;
        this.height = h;
        this.length = l;
        this.palette = palette;
        this.data = data;
        this.storages = storages;
    }

    @SuppressWarnings("unchecked")
    public static Schematic load(File file, Logger log) throws IOException {
        Map<String, Object> root = Nbt.readGzip(file);
        if (root.get("Schematic") instanceof Map<?, ?> inner) root = (Map<String, Object>) inner;
        if (!(root.get("Palette") instanceof Map) || !(root.get("BlockData") instanceof byte[])) {
            throw new IOException("Это не схематика Sponge v2 (нет Palette/BlockData). Сохраняйте через //copy и //schem save в формате sponge.");
        }

        int w = ((Number) root.get("Width")).intValue();
        int h = ((Number) root.get("Height")).intValue();
        int l = ((Number) root.get("Length")).intValue();

        Map<String, Object> pal = (Map<String, Object>) root.get("Palette");
        int maxIdx = -1;
        for (Object v : pal.values()) maxIdx = Math.max(maxIdx, ((Number) v).intValue());
        BlockData[] palette = new BlockData[maxIdx + 1];
        for (Map.Entry<String, Object> e : pal.entrySet()) {
            BlockData bd;
            try {
                bd = Bukkit.createBlockData(e.getKey());
            } catch (IllegalArgumentException ex) {
                log.warning("Неизвестный блок в схематике, заменён воздухом: " + e.getKey());
                bd = Bukkit.createBlockData(Material.AIR);
            }
            palette[((Number) e.getValue()).intValue()] = bd;
        }
        BlockData air = Bukkit.createBlockData(Material.AIR);
        for (int i = 0; i < palette.length; i++) if (palette[i] == null) palette[i] = air;

        byte[] bd = (byte[]) root.get("BlockData");
        int[] out = new int[w * h * l];
        int pos = 0, n = 0;
        while (pos < bd.length && n < out.length) {
            int v = 0, shift = 0;
            while (true) {
                byte b = bd[pos++];
                v |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            out[n++] = v;
        }
        if (n != out.length) throw new IOException("BlockData повреждён: " + n + " из " + out.length + " блоков");

        List<Storage> storages = new ArrayList<>();
        Object beObj = root.get("BlockEntities");
        if (beObj instanceof List<?> bes) {
            for (Object o : bes) {
                Map<String, Object> be = (Map<String, Object>) o;
                if (!(be.get("Items") instanceof List<?> li) || li.isEmpty()) continue;
                if (!(be.get("Pos") instanceof int[] p) || p.length < 3) continue;
                List<Item> items = new ArrayList<>();
                for (Object io : li) {
                    Map<String, Object> it = (Map<String, Object>) io;
                    Object tag = it.get("tag");
                    items.add(new Item(
                            ((Number) it.get("Slot")).intValue() & 0xFF,
                            (String) it.get("id"),
                            ((Number) it.get("Count")).intValue(),
                            tag == null ? null : Nbt.toSnbt(tag)));
                }
                storages.add(new Storage(p[0], p[1], p[2], items));
            }
        }
        return new Schematic(w, h, l, palette, out, storages);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int length() { return length; }
    public List<Storage> storages() { return storages; }

    public BlockData get(int x, int y, int z) {
        return palette[data[(y * length + z) * width + x]];
    }

    public Material material(int x, int y, int z) {
        return get(x, y, z).getMaterial();
    }
}
