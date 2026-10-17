package ru.domsanta;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 5 маленьких стражей Санты: незеритовая броня, тотем, привязка к границам ивента. */
public class GuardManager implements Listener {

    private final DomSantaPlugin plugin;
    private final Location center; // блок шалкера
    private final double zone;
    private final World world;
    private final Set<UUID> guards = new HashSet<>();
    private final Set<UUID> totemUsed = new HashSet<>();
    private BukkitTask task;
    private boolean registered;
    private List<Location> spawns = new ArrayList<>(); // безопасные точки появления/возврата (из схематики)

    public GuardManager(DomSantaPlugin plugin, Location center, double zone) {
        this.plugin = plugin;
        this.center = center;
        this.zone = zone;
        this.world = center.getWorld();
    }

    private double cxD() { return center.getBlockX() + 0.5; }
    private double czD() { return center.getBlockZ() + 0.5; }

    public void spawn(int count, List<Location> points) {
        if (points != null) spawns = new ArrayList<>(points);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered = true;
        for (int i = 0; i < count; i++) {
            Location l;
            if (!spawns.isEmpty()) {
                l = spawns.get(i % spawns.size()).clone();
            } else { // запасной вариант: кольцо вокруг шалкера
                double ang = Math.PI * 2 * i / count;
                double sr = plugin.getConfig().getDouble("guards.spawn-radius", 6.8);
                l = new Location(world, cxD() + Math.cos(ang) * sr, center.getY(), czD() + Math.sin(ang) * sr);
                while (!l.getBlock().isPassable() && l.getY() < center.getY() + 4) l.add(0, 1, 0);
            }
            spawnGuard(l);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private static void attr(Zombie z, Attribute a, double v) {
        AttributeInstance ai = z.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    private ItemStack piece(Material m) {
        ItemStack it = new ItemStack(m);
        it.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
        return it;
    }

    private void spawnGuard(Location loc) {
        double hp = plugin.getConfig().getDouble("guards.health", 60);
        Zombie z = world.spawn(loc, Zombie.class, g -> {
            g.setBaby();
            g.setRemoveWhenFarAway(false);
            g.setPersistent(false);
            g.setCanPickupItems(false);
            g.setCustomNameVisible(true);
            attr(g, Attribute.GENERIC_MAX_HEALTH, hp);
            attr(g, Attribute.GENERIC_FOLLOW_RANGE, 32);
            attr(g, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.6);
            attr(g, Attribute.ZOMBIE_SPAWN_REINFORCEMENTS, 0);
            g.setHealth(hp);

            EntityEquipment eq = g.getEquipment();
            eq.setHelmet(piece(Material.NETHERITE_HELMET));
            eq.setChestplate(piece(Material.NETHERITE_CHESTPLATE));
            eq.setLeggings(piece(Material.NETHERITE_LEGGINGS));
            eq.setBoots(piece(Material.NETHERITE_BOOTS));
            ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
            sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 5);
            sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
            eq.setItemInMainHand(sword);
            eq.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);

            g.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        });
        guards.add(z.getUniqueId());
        updateName(z);
    }

    private void updateName(Zombie z) {
        String base = plugin.getConfig().getString("guards.name", "&4&lСтраж Санты");
        String name = base + " &c❤" + (int) Math.ceil(z.getHealth()) + (totemUsed.contains(z.getUniqueId()) ? "" : " &6✦");
        z.setCustomName(plugin.color(name));
    }

    public int count() {
        return guards.size();
    }

    // ---------------- привязка к границам ----------------

    private void tick() {
        Iterator<UUID> it = guards.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Zombie) || e.isDead()) {
                it.remove();
                continue;
            }
            Zombie z = (Zombie) e;
            updateName(z);

            Location l = z.getLocation();
            double dx = l.getX() - cxD(), dz = l.getZ() - czD();
            double dist = Math.sqrt(dx * dx + dz * dz);
            boolean farY = Math.abs(l.getY() - center.getY()) > 30;

            if (dist > zone - 1) {
                z.setTarget(null);
                z.getPathfinder().moveTo(new Location(world, cxD(), center.getY(), czD()), 1.2);
            }
            if (dist > zone + 1.5 || farY || l.getWorld() != world) {
                Location back;
                if (!spawns.isEmpty()) {
                    back = spawns.get(Math.floorMod(id.hashCode(), spawns.size())).clone();
                } else {
                    double ang = dist > 0.01 ? Math.atan2(dz, dx) : 0;
                    back = new Location(world, cxD() + Math.cos(ang) * 6.8, center.getY(), czD() + Math.sin(ang) * 6.8);
                }
                z.teleport(back);
                z.setTarget(null);
            }
        }
    }

    // ---------------- события ----------------

    private boolean isGuard(Entity e) {
        return e instanceof Zombie && guards.contains(e.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!isGuard(e.getEntity())) return;
        Zombie z = (Zombie) e.getEntity();
        UUID id = z.getUniqueId();
        if (!totemUsed.contains(id) && z.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            popTotem(z);
        }
    }

    private void popTotem(Zombie z) {
        totemUsed.add(z.getUniqueId());
        double hp = plugin.getConfig().getDouble("guards.totem-health", 45);
        attr(z, Attribute.GENERIC_MAX_HEALTH, hp);
        z.setHealth(hp);
        z.setFireTicks(0);
        z.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
        z.playEffect(EntityEffect.TOTEM_RESURRECT);
        Location l = z.getLocation().add(0, 0.8, 0);
        world.spawnParticle(Particle.TOTEM, l, 60, 0.4, 0.6, 0.4, 0.4);
        world.playSound(l, Sound.ITEM_TOTEM_USE, 1.2f, 0.9f);
        updateName(z);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!isGuard(e.getDamager())) return;
        if (isGuard(e.getEntity())) {
            e.setCancelled(true);
            return;
        }
        boolean used = totemUsed.contains(e.getDamager().getUniqueId());
        e.setDamage(used ? plugin.getConfig().getDouble("guards.totem-damage", 9)
                : plugin.getConfig().getDouble("guards.damage", 7));
        if (e.getEntity() instanceof LivingEntity le) {
            le.setFireTicks(Math.max(le.getFireTicks(), 160)); // Fire Aspect II = 8 сек
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!isGuard(e.getEntity()) || e.getTarget() == null) return;
        if (!(e.getTarget() instanceof Player)) {
            e.setCancelled(true);
            return;
        }
        Location t = e.getTarget().getLocation();
        double dx = t.getX() - cxD(), dz = t.getZ() - czD();
        if (t.getWorld() != world || dx * dx + dz * dz > zone * zone) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!isGuard(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        guards.remove(e.getEntity().getUniqueId());
        totemUsed.remove(e.getEntity().getUniqueId());
    }

    public void removeAll() {
        if (task != null) task.cancel();
        for (UUID id : new HashSet<>(guards)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        guards.clear();
        totemUsed.clear();
        if (registered) HandlerList.unregisterAll(this);
    }
}
