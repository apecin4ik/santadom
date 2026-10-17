package ru.domsanta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LootBuilder {

    private LootBuilder() {}

    public static void fill(DomSantaPlugin plugin, Inventory inv) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("loot");
        if (sec == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection e = sec.getConfigurationSection(key);
            if (e == null) continue;
            double chance = e.getDouble("chance", 100);
            for (int slot : parseSlots(e.getString("slots", ""))) {
                if (slot < 0 || slot >= inv.getSize()) continue;
                if (rnd.nextDouble() * 100.0 < chance) inv.setItem(slot, build(plugin, e));
            }
        }
    }

    private static List<Integer> parseSlots(String s) {
        List<Integer> out = new ArrayList<>();
        for (String part : s.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                if (part.contains("-")) {
                    String[] ab = part.split("-");
                    int a = Integer.parseInt(ab[0].trim()), b = Integer.parseInt(ab[1].trim());
                    for (int i = Math.min(a, b); i <= Math.max(a, b); i++) out.add(i);
                } else {
                    out.add(Integer.parseInt(part));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** Помечает предмет как ExecutableItems-предмет (как /ei give <id>): executableitems:ei-id + score:usage. */
    private static void tagEI(ItemMeta meta, String id) {
        if (id == null || id.isEmpty()) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey("executableitems", "ei-id"), PersistentDataType.STRING, id);
        pdc.set(new NamespacedKey("score", "usage"), PersistentDataType.INTEGER, 1);
    }

    private static List<Component> lore(DomSantaPlugin plugin, ConfigurationSection e) {
        List<Component> out = new ArrayList<>();
        for (String s : e.getStringList("lore")) out.add(name(plugin, s));
        return out;
    }

    private static Component name(DomSantaPlugin plugin, String s) {
        return plugin.comp(s).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack build(DomSantaPlugin plugin, ConfigurationSection e) {
        if ("SFERA".equalsIgnoreCase(e.getString("special"))) return sfera(plugin, e);

        Material mat = Material.matchMaterial(e.getString("material", "SNOWBALL"));
        if (mat == null) mat = Material.SNOWBALL;
        ItemStack it = new ItemStack(mat, Math.max(1, e.getInt("amount", 1)));
        ItemMeta meta = it.getItemMeta();
        if (e.contains("name")) meta.displayName(name(plugin, e.getString("name")));
        if (e.contains("lore")) meta.lore(lore(plugin, e));
        tagEI(meta, e.getString("ei-id"));

        if (meta instanceof PotionMeta pm) {
            String base = e.getString("base-potion");
            try {
                if (base != null) {
                    pm.setBasePotionData(new PotionData(PotionType.valueOf(base.toUpperCase()), false, e.getBoolean("base-upgraded", false)));
                } else {
                    pm.setBasePotionData(new PotionData(PotionType.WATER));
                }
            } catch (IllegalArgumentException ignored) {
            }
            for (String eff : e.getStringList("effects")) {
                String[] p = eff.split(":");
                if (p.length < 3) continue;
                PotionEffectType t = PotionEffectType.getByName(p[0].toUpperCase());
                if (t == null) continue;
                try {
                    pm.addCustomEffect(new PotionEffect(t, Integer.parseInt(p[1]), Integer.parseInt(p[2])), true);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        it.setItemMeta(meta);
        return it;
    }

    private static void mod(SkullMeta m, Attribute a, double amount, AttributeModifier.Operation op) {
        m.addAttributeModifier(a, new AttributeModifier(UUID.randomUUID(), "domsanta_sfera", amount, op, EquipmentSlot.OFF_HAND));
    }

    private static ItemStack sfera(DomSantaPlugin plugin, ConfigurationSection e) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) head.getItemMeta();
        String tex = e.getString("texture", "");
        if (!tex.isEmpty()) {
            PlayerProfile prof = Bukkit.createProfile(UUID.fromString("b33183ad-e9c0-4d48-8eea-f8c9358d3568"), "test");
            prof.setProperty(new ProfileProperty("textures", tex));
            m.setPlayerProfile(prof);
        }
        m.displayName(name(plugin, e.getString("name", "&c&lСфера Санты")));
        m.lore(e.contains("lore") ? lore(plugin, e) : List.of(name(plugin, "&8Реликвия тёмной зимы")));
        tagEI(m, e.getString("ei-id"));
        m.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        mod(m, Attribute.GENERIC_ARMOR, -3.5, AttributeModifier.Operation.ADD_NUMBER);
        mod(m, Attribute.GENERIC_ARMOR_TOUGHNESS, -2.0, AttributeModifier.Operation.ADD_NUMBER);
        mod(m, Attribute.GENERIC_MAX_HEALTH, 10.0, AttributeModifier.Operation.ADD_NUMBER);
        mod(m, Attribute.GENERIC_MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_SCALAR);
        mod(m, Attribute.GENERIC_ATTACK_SPEED, 0.2, AttributeModifier.Operation.ADD_SCALAR);
        head.setItemMeta(m);
        return head;
    }
}
