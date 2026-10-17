package ru.domsanta;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DomSantaCommand implements CommandExecutor, TabCompleter {

    private final DomSantaPlugin plugin;

    public DomSantaCommand(DomSantaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("domsanta.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        switch (sub) {
            case "tp" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg("only-player"));
                    return true;
                }
                SantaEvent ev = plugin.getActive();
                if (ev == null) {
                    sender.sendMessage(plugin.msg("not-running"));
                    return true;
                }
                p.teleport(ev.getTeleportLocation());
                p.sendMessage(plugin.msg("teleported"));
            }
            case "start" -> {
                String err = plugin.startEvent();
                if (err != null) sender.sendMessage(plugin.msg(err));
            }
            case "stop" -> {
                if (plugin.stopEvent()) sender.sendMessage(plugin.msg("stopped"));
                else sender.sendMessage(plugin.msg("not-running"));
            }
            case "reload" -> {
                plugin.reloadConfig();
                boolean ok = plugin.loadSchematic();
                plugin.scheduleNext();
                sender.sendMessage(plugin.msg(ok ? "reloaded" : "reloaded-no-schem"));
            }
            default -> sender.sendMessage(plugin.msg("unknown"));
        }
        return true;
    }

    private void info(CommandSender sender) {
        sender.sendMessage(plugin.raw("info-header"));
        SantaEvent ev = plugin.getActive();
        if (ev == null) {
            sender.sendMessage(plugin.raw("info-none"));
            if (plugin.getConfig().getBoolean("auto-start.enabled", true)) {
                long left = plugin.secondsToNext();
                sender.sendMessage(plugin.raw("info-next",
                        "clock", plugin.clockOfNext(),
                        "tz", plugin.getConfig().getString("auto-start.timezone-label", "МСК"),
                        "left", plugin.fmtHuman(left),
                        "time", plugin.fmt(left)));
            }
        } else {
            String time = plugin.fmt(ev.getSecondsLeft());
            sender.sendMessage(plugin.raw(ev.getPhase() == SantaEvent.Phase.LOCKED ? "info-locked" : "info-open", "time", time));
            sender.sendMessage(plugin.raw("info-guards", "guards", String.valueOf(ev.getGuardCount())));
            if (plugin.getConfig().getBoolean("players-see-coordinates", true)) {
                Location l = ev.getChestLocation();
                sender.sendMessage(plugin.raw("info-coords", "x", String.valueOf(l.getBlockX()),
                        "y", String.valueOf(l.getBlockY()), "z", String.valueOf(l.getBlockZ()),
                        "world", l.getWorld().getName()));
                if (sender instanceof Player p && p.getWorld().equals(l.getWorld())) {
                    sender.sendMessage(plugin.raw("info-distance", "dist", String.valueOf((int) p.getLocation().distance(l))));
                }
            }
        }
        if (sender.hasPermission("domsanta.admin")) {
            sender.sendMessage(plugin.raw("info-admin"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("domsanta.admin")) {
            for (String s : new String[]{"tp", "start", "stop", "reload"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }
}
