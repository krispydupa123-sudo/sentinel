package pl.sentinel;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@SuppressWarnings("deprecation")
final class MoveCheck implements Listener {
    private final Sentinel pl;

    MoveCheck(Sentinel pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (!e.hasChangedPosition() || !from.getWorld().equals(to.getWorld())) return;
        Player p = e.getPlayer();
        if (pl.bypass(p)) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();

        if (Util.bouncy(to) || Util.bouncy(from)) d.lastSpecial = now;
        if (Util.icy(to)) d.lastIce = now;

        if (exempt(p, d, now) || Util.envSpecial(to) || Util.envSpecial(from)) { reset(d); return; }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        boolean ground = Util.nearGround(to);
        if (ground) { d.airTicks = 0; d.airRise = 0; }
        else { d.airTicks++; if (dy > 0) d.airRise += dy; }

        // ---- SPEED ----
        double h = Math.hypot(dx, dz);
        double ratio = 1.0;
        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr != null) ratio = Math.max(1.0, attr.getValue() / 0.13);
        double limit = pl.getConfig().getDouble("checks.speed.max-speed", 0.68) * ratio;
        if (now - d.lastIce < 1200) limit *= 1.8;
        if (h > limit) {
            d.speedBuf++;
            if (d.speedBuf >= 3) pl.flag(p, "speed", 1 + (h - limit) * 4, Util.f(h) + ">" + Util.f(limit));
        } else {
            d.speedBuf = Math.max(0, d.speedBuf - 0.3);
        }

        // ---- FLY ----
        if (!ground) {
            PotionEffect jb = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
            double maxRise = 1.7 + (jb == null ? 0 : (jb.getAmplifier() + 1) * 0.6);
            if (d.airRise > maxRise) {
                pl.flag(p, "fly", 2, "rise " + Util.f(d.airRise));
            } else if (jb == null && d.airTicks > 12 && dy > -0.25) {
                pl.flag(p, "fly", 1.5, "hover dy=" + Util.f(dy) + " air=" + d.airTicks);
            }
        }

        // ---- NOFALL ----
        if (p.isOnGround() && !ground && dy < -0.6) {
            d.noFallBuf++;
            if (d.noFallBuf >= 2) pl.flag(p, "nofall", 2, "spoof ground dy=" + Util.f(dy));
        } else {
            d.noFallBuf = Math.max(0, d.noFallBuf - 0.25);
        }
    }

    private boolean exempt(Player p, PlayerData d, long now) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || p.getAllowFlight() || p.isFlying() || p.isDead()) return true;
        if (p.isGliding() || p.isRiptiding() || p.isInsideVehicle()) { d.lastSpecial = now; return true; }
        if (p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        return now - d.joinTime < 3000 || now - d.lastTeleport < 1500
                || now - d.lastVelocity < 1500 || now - d.lastSpecial < 1000;
    }

    private void reset(PlayerData d) {
        d.airTicks = 0; d.airRise = 0; d.speedBuf = 0; d.noFallBuf = 0;
    }
}
