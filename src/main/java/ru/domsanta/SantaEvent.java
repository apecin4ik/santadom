package ru.domsanta;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class SantaEvent {

    public enum Phase { LOCKED, OPEN }

    private final DomSantaPlugin plugin;
    private final StructureBuilder structure;
    private final World world;
    private final Location chest;
    private final Inventory loot;
    private final BossBar bar;
    private final GuardManager guards;
    private final double zone;
    private final List<Chunk> tickets = new ArrayList<>();
    private TextDisplay holo;
    private BukkitTask visual;
    private Phase phase = Phase.LOCKED;
    private int secondsLeft, phaseTotal;
    private boolean ended;
    private int frame;
    private static final String[] FLAKES = {"❄", "❅", "❆", "❅"};

    public SantaEvent(DomSantaPlugin plugin, StructureBuilder structure) {
        this.plugin = plugin;
        this.structure = structure;
        this.world = structure.getWorld();
        this.chest = structure.getShulkerLocation();
        this.zone = plugin.getConfig().getDouble("zone.radius", 20);
        this.loot = Bukkit.createInventory(null, 54, plugin.comp(plugin.getConfig().getString("inventory-title", "Подарок Санты")));
        this.bar = Bukkit.createBossBar("", BarColor.PURPLE, parseStyle());
        this.guards = new GuardManager(plugin, chest, zone);
    }

    private BarStyle parseStyle() {
        try {
            return BarStyle.valueOf(plugin.getConfig().getString("bossbar.style", "SEGMENTED_10").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BarStyle.SEGMENTED_10;
        }
    }

    private BarColor parseColor(String path, BarColor def) {
        try {
            return BarColor.valueOf(plugin.getConfig().getString(path, def.name()).toUpperCase());
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    // ---------------- жизненный цикл ----------------

    public void start() {
        int ccx = chest.getBlockX() >> 4, ccz = chest.getBlockZ() >> 4;
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                Chunk ch = world.getChunkAt(ccx + ox, ccz + oz);
                ch.addPluginChunkTicket(plugin);
                tickets.add(ch);
            }
        }
        structure.build();
        LootBuilder.fill(plugin, loot);

        Location hl = chest.clone().add(0.5, 1.9, 0.5);
        holo = world.spawn(hl, TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            t.setLineWidth(300);
        });

        int guardCount = plugin.getConfig().getInt("guards.count", 5);
        guards.spawn(guardCount, structure.guardSpawns(guardCount));

        secondsLeft = plugin.getConfig().getInt("timers.open-after-seconds", 300);
        phaseTotal = secondsLeft;
        refresh();
        appear();
        visual = Bukkit.getScheduler().runTaskTimer(plugin, this::visuals, 10L, 10L);
    }

    public void tick() {
        if (ended) return;
        secondsLeft--;
        if (secondsLeft <= 0) {
            if (phase == Phase.LOCKED) open();
            else {
                end(true);
                return;
            }
        }
        refresh();
    }

    private void open() {
        phase = Phase.OPEN;
        secondsLeft = plugin.getConfig().getInt("timers.disappear-after-open-seconds", 1200);
        phaseTotal = secondsLeft;
        chest.getBlock().setType(Material.WHITE_SHULKER_BOX, false);
        world.strikeLightningEffect(chest);
        world.playSound(chest, Sound.BLOCK_BELL_USE, 4f, 0.6f);
        world.playSound(chest, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 3f, 0.8f);
        plugin.broadcast(plugin.list("opened", DomSantaPlugin.concat(plugin.coordKv(chest), "time", plugin.fmt(secondsLeft))));
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(chest) <= 100 * 100) {
                p.sendTitle(plugin.raw("title-open"), plugin.raw("subtitle-open"), 10, 60, 20);
            }
        }
    }

    public void end(boolean announce) {
        if (ended) return;
        ended = true;
        if (visual != null) visual.cancel();
        for (org.bukkit.entity.HumanEntity h : new ArrayList<>(loot.getViewers())) h.closeInventory();
        guards.removeAll();
        if (holo != null) holo.remove();
        bar.removeAll();
        if (announce) {
            Location mid = chest.clone().add(0.5, 2, 0.5);
            world.spawnParticle(Particle.SNOWFLAKE, mid, 250, 5, 4, 5, 0.05);
            world.spawnParticle(Particle.END_ROD, mid, 100, 4, 4, 4, 0.08);
            disappear();
        }
        structure.restore();
        for (Chunk ch : tickets) ch.removePluginChunkTicket(plugin);
        tickets.clear();
        if (announce) plugin.broadcast(plugin.list("ended", plugin.coordKv(chest)));
        plugin.eventEnded(this);
    }

    // ---------------- звуки и титры ----------------

    private void jingle(float[] pitches, Sound sound, float vol, int step) {
        for (int i = 0; i < pitches.length; i++) {
            final float pitch = pitches[i];
            Runnable r = () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), sound, SoundCategory.MASTER, vol, pitch);
                }
            };
            if (i == 0) r.run();
            else if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, r, (long) i * step);
        }
    }

    private void everyone(Sound sound, float vol, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, SoundCategory.MASTER, vol, pitch);
    }

    /** Приятная восходящая мелодия колокольчиков при появлении. */
    private void appear() {
        everyone(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 1.3f);
        jingle(new float[]{0.8f, 1.0f, 1.2f, 1.6f}, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 3);
        String[] kv = plugin.coordKv(chest);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(plugin.raw("title-start"), plugin.raw("subtitle-start", kv), 10, 80, 20);
        }
    }

    /** Мягкая нисходящая мелодия при исчезновении. */
    private void disappear() {
        everyone(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.7f);
        jingle(new float[]{1.6f, 1.2f, 1.0f, 0.8f}, Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 4);
    }

    // ---------------- отображение ----------------

    private void refresh() {
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        String time = plugin.fmt(secondsLeft);
        String guardsStr = String.valueOf(guards.count());

        String title = plugin.getConfig().getString(phase == Phase.LOCKED ? "bossbar.locked-title" : "bossbar.open-title", "");
        frame++;
        bar.setTitle(plugin.color(plugin.replace(title, DomSantaPlugin.concat(plugin.coordKv(chest),
                "time", time, "guards", guardsStr, "snow", FLAKES[frame % FLAKES.length]))));
        bar.setProgress(Math.max(0.0, Math.min(1.0, secondsLeft / (double) Math.max(1, phaseTotal))));
        if (phase == Phase.LOCKED) bar.setColor(parseColor("bossbar.locked-color", BarColor.PURPLE));
        else if (secondsLeft <= 60) bar.setColor(frame % 2 == 0 ? parseColor("bossbar.last-minute-color", BarColor.WHITE) : parseColor("bossbar.open-color", BarColor.RED));
        else bar.setColor(parseColor("bossbar.open-color", BarColor.RED));

        if (holo != null && holo.isValid()) {
            List<String> lines = plugin.getConfig().getStringList(phase == Phase.LOCKED ? "hologram.locked" : "hologram.open");
            List<String> out = new ArrayList<>();
            for (String l : lines) out.add(plugin.replace(l, "time", time));
            holo.text(plugin.comp(String.join("\n", out)));
        }
    }

    public void addPlayer(Player p) {
        bar.addPlayer(p);
    }

    private void visuals() {
        if (ended) return;
        double cxD = chest.getBlockX() + 0.5, czD = chest.getBlockZ() + 0.5;
        Location mid = new Location(world, cxD, chest.getY() + 3, czD);
        world.spawnParticle(Particle.SNOWFLAKE, mid, 40, 11, 5, 11, 0.02);
        world.spawnParticle(Particle.SOUL, mid, 3, 10, 2, 10, 0.01);
        Location top = new Location(world, cxD, chest.getY() + 1.2, czD);
        if (phase == Phase.LOCKED) world.spawnParticle(Particle.SOUL_FIRE_FLAME, top, 4, 0.3, 0.3, 0.3, 0.01);
        else world.spawnParticle(Particle.END_ROD, top, 6, 0.35, 0.4, 0.35, 0.02);

        // кольцо границы для игроков рядом
        for (Player p : world.getPlayers()) {
            Location pl = p.getLocation();
            double dist = Math.hypot(pl.getX() - cxD, pl.getZ() - czD);
            if (Math.abs(dist - zone) > 14) continue;
            for (int a = 0; a < 360; a += 4) {
                double rad = Math.toRadians(a);
                double px = cxD + Math.cos(rad) * zone, pz = czD + Math.sin(rad) * zone;
                double ddx = px - pl.getX(), ddz = pz - pl.getZ();
                if (ddx * ddx + ddz * ddz > 14 * 14) continue;
                for (double h = 0; h <= 2.5; h += 1.25) {
                    p.spawnParticle(Particle.SOUL_FIRE_FLAME, px, pl.getY() + h, pz, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // ---------------- взаимодействие ----------------

    public boolean isChest(Block b) {
        return b.getWorld() == world && b.getX() == chest.getBlockX() && b.getY() == chest.getBlockY() && b.getZ() == chest.getBlockZ();
    }

    public void interact(Player p) {
        if (phase == Phase.LOCKED) {
            p.sendMessage(plugin.msg("locked", "time", plugin.fmt(secondsLeft)));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        p.openInventory(loot);
        p.playSound(p.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 0.8f);
    }

    public boolean isProtected(Location l) {
        return structure.isInside(l, 4);
    }

    public boolean isLootEmpty() {
        for (ItemStack it : loot.getContents()) {
            if (it != null && !it.getType().isAir()) return false;
        }
        return true;
    }

    // ---------------- геттеры ----------------

    public Inventory getLoot() { return loot; }
    public Phase getPhase() { return phase; }
    public int getSecondsLeft() { return secondsLeft; }
    public int getGuardCount() { return guards.count(); }
    public Location getChestLocation() { return chest.clone(); }
    public Location getTeleportLocation() { return structure.getTeleportLocation(); }
    public boolean isEnded() { return ended; }
}
