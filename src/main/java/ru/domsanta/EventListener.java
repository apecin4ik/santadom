package ru.domsanta;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public class EventListener implements Listener {

    private final DomSantaPlugin plugin;

    public EventListener(DomSantaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        SantaEvent ev = plugin.getActive();
        if (ev == null || e.getClickedBlock() == null || !ev.isChest(e.getClickedBlock())) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        ev.interact(e.getPlayer());
    }

    private boolean blocked(Player p, Location l) {
        SantaEvent ev = plugin.getActive();
        return ev != null && ev.isProtected(l) && !p.hasPermission("domsanta.bypass");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocked(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocked(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        SantaEvent ev = plugin.getActive();
        if (ev != null && ev.isProtected(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        SantaEvent ev = plugin.getActive();
        if (ev != null && ev.isProtected(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        SantaEvent ev = plugin.getActive();
        if (ev != null) e.blockList().removeIf(b -> ev.isProtected(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        SantaEvent ev = plugin.getActive();
        if (ev != null) e.blockList().removeIf(b -> ev.isProtected(b.getLocation()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        SantaEvent ev = plugin.getActive();
        if (ev != null) ev.addPlayer(e.getPlayer());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        SantaEvent ev = plugin.getActive();
        if (ev == null || !plugin.getConfig().getBoolean("remove-when-empty", false)) return;
        if (e.getInventory() != ev.getLoot()) return;
        if (ev.isLootEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> ev.end(true));
        }
    }
}
