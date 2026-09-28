package pl.sentinel;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Sentinel extends JavaPlugin implements Listener {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Set<UUID> alertsOff = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new MoveCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldCheck(this), this);
        Bukkit.getScheduler().runTaskTimer(this, this::decay, 200L, 200L);
        getLogger().info("Sentinel włączony. dry-run=" + getConfig().getBoolean("dry-run"));
    }

    PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new PlayerData());
    }

    boolean bypass(Player p) {
        return p.hasPermission("sentinel.bypass");
    }

    private void decay() {
        double dec = getConfig().getDouble("vl-decay", 0.5);
        for (PlayerData d : data.values()) {
            d.vl.replaceAll((k, v) -> Math.max(0, v - dec));
        }
    }

    void flag(Player p, String check, double add, String info) {
        if (!getConfig().getBoolean("checks." + check + ".enabled", true)) return;
        PlayerData d = data(p);
        if (d.punished) return;

        double vl = d.vl.merge(check, add, Double::sum);
        double limit = getConfig().getDouble("checks." + check + ".ban-vl", 15);

        long now = System.currentTimeMillis();
        if (now - d.lastAlert.getOrDefault(check, 0L) > 500) {
            d.lastAlert.put(check, now);
            String msg = "§8[§cSentinel§8] §f" + p.getName() + " §7» §c" + check + " §7(" + info
                    + ") §8VL §f" + String.format(Locale.US, "%.1f", vl) + "§7/" + (int) limit;
            getLogger().info(ChatColor.stripColor(msg));
            for (Player s : Bukkit.getOnlinePlayers()) {
                if (s.hasPermission("sentinel.alerts") && !alertsOff.contains(s.getUniqueId())) s.sendMessage(msg);
            }
        }
        if (vl >= limit) punish(p, check);
    }

    @SuppressWarnings("deprecation")
    private void punish(Player p, String check) {
        PlayerData d = data(p);
        if (getConfig().getBoolean("dry-run", true)) {
            d.vl.put(check, 0.0);
            getLogger().warning("[DRY-RUN] " + p.getName() + " dostałby bana za " + check);
            return;
        }
        d.punished = true;

        int days = getConfig().getInt("ban.duration-days", 7);
        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String reason = String.join("\n", getConfig().getStringList("ban.kick-message"))
                .replace("{player}", p.getName())
                .replace("{check}", check)
                .replace("{duration}", days + "d")
                .replace("{id}", id);
        reason = ChatColor.translateAlternateColorCodes('&', reason);

        Date expires = new Date(System.currentTimeMillis() + days * 86_400_000L);
        BanList<PlayerProfile> list = Bukkit.getBanList(BanList.Type.PROFILE);
        list.addBan(p.getPlayerProfile(), reason, expires, "Sentinel");

        p.kick(LegacyComponentSerializer.legacySection().deserialize(reason));
        getLogger().warning("BAN " + p.getName() + " (" + days + "d) za " + check + " id=" + id);

        if (getConfig().getBoolean("ban.broadcast", true)) {
            String bc = ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("ban.broadcast-message", "").replace("{player}", p.getName()));
            if (!bc.isEmpty()) Bukkit.broadcastMessage(bc);
        }
    }

    // ---- zdarzenia pomocnicze ----
    @EventHandler public void onJoin(PlayerJoinEvent e) { data(e.getPlayer()).joinTime = System.currentTimeMillis(); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { data.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void onTp(PlayerTeleportEvent e) { data(e.getPlayer()).lastTeleport = System.currentTimeMillis(); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { data(e.getPlayer()).lastTeleport = System.currentTimeMillis(); }
    @EventHandler public void onVelocity(PlayerVelocityEvent e) { data(e.getPlayer()).lastVelocity = System.currentTimeMillis(); }

    // ---- komendy ----
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("sentinel.admin")) { s.sendMessage("§cBrak uprawnień."); return true; }
        if (a.length == 0) {
            s.sendMessage("§c/sentinel reload §7- przeładuj config");
            s.sendMessage("§c/sentinel alerts §7- włącz/wyłącz alerty");
            s.sendMessage("§c/sentinel vl <gracz> §7- pokaż VL gracza");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> { reloadConfig(); s.sendMessage("§aPrzeładowano config."); }
            case "alerts" -> {
                if (s instanceof Player p) {
                    if (!alertsOff.remove(p.getUniqueId())) alertsOff.add(p.getUniqueId());
                    s.sendMessage(alertsOff.contains(p.getUniqueId()) ? "§7Alerty: §cOFF" : "§7Alerty: §aON");
                }
            }
            case "vl" -> {
                if (a.length < 2) { s.sendMessage("§cPodaj gracza."); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage("§cGracz offline."); return true; }
                s.sendMessage("§7VL " + t.getName() + ": §f" + data(t).vl);
            }
            default -> s.sendMessage("§cNieznana podkomenda.");
        }
        return true;
    }
}
