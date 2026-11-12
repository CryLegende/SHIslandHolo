package org.Cry_Legende.sHIslandHolo;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Position d'un hologramme, calculée une seule fois, à sa création.
 * Par défaut devant le point d'apparition de l'île, à portée de clic d'un joueur qui y arrive ; si des blocs
 * occupent cette place, la place libre la plus proche autour du point d'apparition, puis autour du joueur.
 * Une position fixe (centre de l'île + décalage) monte d'abord au-dessus des blocs.
 * Aucun chunk n'est chargé : un bloc d'un chunk absent compte comme libre.
 */
final class Placement {

    /**
     * Réglages de config.yml.
     *
     * @param distance distance horizontale entre le joueur et le milieu de l'hologramme
     * @param height   hauteur du bas de l'hologramme au-dessus des pieds du joueur
     */
    record Rules(boolean enabled, double width, int maxRise, double distance, double height) {
    }

    // Portée de clic sur une entité d'un joueur en survie, hauteur de ses yeux
    private static final double REACH = 3.0;
    private static final double EYES = 1.62;
    private static final double EPSILON = 1.0E-3;
    // Directions autour d'un point, en huitièmes de tour à partir de son regard : devant d'abord, derrière en dernier
    private static final int[] DIRECTIONS = {0, 1, -1, 2, -2, 3, -3, 4};

    private Placement() {
    }

    /** Devant le point donné (pieds du joueur), dans la direction de son regard. */
    static Location front(Location anchor, Rules rules) {
        return spot(anchor, 0, 0, rules);
    }

    /**
     * @param wanted position prévue : devant le point d'apparition, ou position fixe
     * @param fixed  vrai pour une position fixe (centre de l'île + décalage)
     * @param home   point d'apparition de l'île, ou null
     * @param player position du joueur, ou null
     * @param height hauteur de l'hologramme, en blocs
     * @return la position prévue si elle est libre ou si aucune place libre n'est trouvée
     */
    static Location find(Location wanted, boolean fixed, Location home, Location player, double height, Rules rules) {
        World world = wanted.getWorld();
        if (!rules.enabled() || world == null) {
            return wanted;
        }
        double half = rules.width() / 2;
        if (fixed) {
            for (int rise = 0; rise <= rules.maxRise(); rise++) {
                if (free(world, wanted.getX(), wanted.getY() + rise, wanted.getZ(), half, height)) {
                    return rise == 0 ? wanted : wanted.clone().add(0, rise, 0);
                }
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

    /**
     * Place libre autour du point donné, à la distance prévue. D'abord les places encore à portée de clic,
     * de la plus proche de « devant, à hauteur prévue » à la plus éloignée (un huitième de tour vaut un bloc
     * de montée), puis les plus hautes.
     */
    private static Location around(World world, Location anchor, double half, double height, Rules rules) {
        // Montée au-delà de laquelle le bas de la zone cliquable sort de la portée d'un joueur resté sur place
        double nearFace = Math.max(0, rules.distance() - half);
        double reachUp = Math.sqrt(Math.max(0, REACH * REACH - nearFace * nearFace));
        int close = (int) Math.min(rules.maxRise(), Math.max(0, Math.floor(EYES + reachUp - rules.height())));
        for (int cost = 0; cost <= close + 4; cost++) {
            for (int direction : DIRECTIONS) {
                int rise = cost - Math.abs(direction);
                if (rise < 0 || rise > close) {
                    continue;
                }
                Location spot = spot(anchor, direction, rise, rules);
                if (free(world, spot.getX(), spot.getY(), spot.getZ(), half, height)) {
                    return spot;
                }
            }
        }
        for (int direction : DIRECTIONS) {
            for (int rise = close + 1; rise <= rules.maxRise(); rise++) {
                Location spot = spot(anchor, direction, rise, rules);
                if (free(world, spot.getX(), spot.getY(), spot.getZ(), half, height)) {
                    return spot;
                }
            }
        }
        return null;
    }

    private static Location spot(Location anchor, int direction, int rise, Rules rules) {
        double yaw = Math.toRadians(anchor.getYaw()) + direction * Math.PI / 4;
        return new Location(anchor.getWorld(), anchor.getX() - Math.sin(yaw) * rules.distance(),
                anchor.getY() + rules.height() + rise, anchor.getZ() + Math.cos(yaw) * rules.distance());
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
