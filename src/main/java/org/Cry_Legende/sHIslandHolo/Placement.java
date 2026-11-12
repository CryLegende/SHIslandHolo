package org.Cry_Legende.sHIslandHolo;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Place d'un hologramme que des blocs occupent (île construite à sa position, point d'apparition déplacé) :
 * la place libre la plus proche, cherchée une seule fois, à la création de l'hologramme.
 * Ordre : au-dessus de la position prévue, puis devant le point d'apparition de l'île, puis devant le joueur.
 * Aucun chunk n'est chargé : un bloc d'un chunk absent compte comme libre.
 */
final class Placement {

    /** Réglages de config.yml (section « placement »). */
    record Rules(boolean enabled, double width, int maxRise, double distance, double height) {
    }

    private static final double EPSILON = 1.0E-3;
    // Directions essayées autour d'un point d'apparition, en huitièmes de tour à partir de son regard
    private static final int[] DIRECTIONS = {0, 1, -1, 2, -2, 3, -3, 4};

    private Placement() {
    }

    /**
     * @param wanted position prévue (centre de l'île + position du conseil)
     * @param home   point d'apparition de l'île, ou null
     * @param player position du joueur, ou null
     * @param height hauteur de l'hologramme, en blocs
     * @return la position prévue si elle est libre ou si aucune place libre n'est trouvée
     */
    static Location find(Location wanted, Location home, Location player, double height, Rules rules) {
        World world = wanted.getWorld();
        if (!rules.enabled() || world == null) {
            return wanted;
        }
        double half = rules.width() / 2;
        for (int rise = 0; rise <= rules.maxRise(); rise++) {
            if (free(world, wanted.getX(), wanted.getY() + rise, wanted.getZ(), half, height)) {
                return rise == 0 ? wanted : wanted.clone().add(0, rise, 0);
            }
        }
        for (Location anchor : new Location[]{home, player}) {
            if (anchor == null || !world.equals(anchor.getWorld())) {
                continue;
            }
            Location spot = around(world, anchor, half, height, rules);
            if (spot != null) {
                return spot;
            }
        }
        return wanted;
    }

    /** Devant le point donné ; à chaque hauteur, toutes les directions sont essayées avant de monter d'un bloc. */
    private static Location around(World world, Location anchor, double half, double height, Rules rules) {
        double facing = Math.toRadians(anchor.getYaw());
        for (int rise = 0; rise <= rules.maxRise(); rise++) {
            double y = anchor.getY() + rules.height() + rise;
            for (int direction : DIRECTIONS) {
                double yaw = facing + direction * Math.PI / 4;
                double x = anchor.getX() - Math.sin(yaw) * rules.distance();
                double z = anchor.getZ() + Math.cos(yaw) * rules.distance();
                if (free(world, x, y, z, half, height)) {
                    return new Location(world, x, y, z);
                }
            }
        }
        return null;
    }

    /** Vrai si aucun bloc qu'on ne traverse pas ne touche le volume (largeur × largeur × hauteur, base en y). */
    private static boolean free(World world, double x, double y, double z, double half, double height) {
        int minY = floor(y + EPSILON);
        int maxY = Math.min(floor(y + height - EPSILON), world.getMaxHeight() - 1);
        if (minY < world.getMinHeight()) {
            return false;
        }
        int minX = floor(x - half + EPSILON);
        int maxX = floor(x + half - EPSILON);
        int minZ = floor(z - half + EPSILON);
        int maxZ = floor(z + half - EPSILON);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    continue;
                }
                for (int by = minY; by <= maxY; by++) {
                    if (!world.getBlockAt(bx, by, bz).isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
