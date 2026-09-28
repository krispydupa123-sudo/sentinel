package pl.sentinel;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Vehicle;

import java.util.Locale;

final class Util {
    private Util() {}

    private static final double[] XZ = {-0.31, 0, 0.31};
    private static final double[] Y = {-0.1, -0.3};

    static String f(double v) { return String.format(Locale.US, "%.2f", v); }

    private static Block at(Location l, double ox, double oy, double oz) {
        return l.getWorld().getBlockAt(Location.locToBlock(l.getX() + ox),
                Location.locToBlock(l.getY() + oy), Location.locToBlock(l.getZ() + oz));
    }

    /** Czy pod nogami jest cokolwiek stałego (blok, ciecz, łódka, shulker). */
    static boolean nearGround(Location l) {
        for (double x : XZ) for (double z : XZ) for (double y : Y) {
            Block b = at(l, x, y, z);
            if (b.isLiquid() || !b.isPassable()) return true;
        }
        for (Entity e : l.getWorld().getNearbyEntities(l, 0.9, 0.6, 0.9)) {
            if (e instanceof Vehicle || e instanceof Shulker) return true;
        }
        return false;
    }

    /** Miejsca, gdzie normalna fizyka nie obowiązuje (woda, drabiny, pajęczyna...). */
    static boolean envSpecial(Location l) {
        for (double y : new double[]{0.0, 0.9, 1.6}) {
            Block b = at(l, 0, y, 0);
            Material m = b.getType();
            if (b.isLiquid() || Tag.CLIMBABLE.isTagged(m)) return true;
            switch (m) {
                case COBWEB, POWDER_SNOW, SWEET_BERRY_BUSH, HONEY_BLOCK, BUBBLE_COLUMN, SCAFFOLDING -> { return true; }
                default -> { }
            }
        }
        Material below = at(l, 0, -0.5, 0).getType();
        return below == Material.HONEY_BLOCK;
    }

    static boolean bouncy(Location l) {
        Material m = at(l, 0, -0.5, 0).getType();
        return m == Material.SLIME_BLOCK || Tag.BEDS.isTagged(m);
    }

    static boolean icy(Location l) {
        for (double y : new double[]{-0.5, -1.2}) {
            Material m = at(l, 0, y, 0).getType();
            if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE || m == Material.FROSTED_ICE) return true;
        }
        return false;
    }
}
