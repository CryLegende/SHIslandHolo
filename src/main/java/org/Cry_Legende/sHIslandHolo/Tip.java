package org.Cry_Legende.sHIslandHolo;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Un conseil de tips.yml.
 *
 * @param click    commande lancée par le joueur au clic droit (sans « / »), ou null
 * @param position position par rapport au centre de l'île, ou null pour celle de config.yml
 */
record Tip(String id, Range levels, Range members, List<Condition> conditions, List<String> lines,
           String click, Vector position) {

    boolean matches(Player player, int islandLevel, int islandMembers, boolean placeholderApi) {
        if (!levels.contains(islandLevel) || !members.contains(islandMembers)) {
            return false;
        }
        for (Condition condition : conditions) {
            if (!condition.test(player, placeholderApi)) {
                return false;
            }
        }
        return true;
    }

    boolean hasPlaceholders() {
        return lines.stream().anyMatch(line -> line.indexOf('%') >= 0);
    }
}
