package org.Cry_Legende.sHIslandHolo;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Comparaison « gauche opérateur droite » de tips.yml, les deux côtés passant par PlaceholderAPI.
 * Deux nombres se comparent comme des nombres, sinon comme du texte sans tenir compte des majuscules.
 */
record Condition(String left, String operator, String right) {

    // Les opérateurs à deux caractères passent avant « > » et « < »
    private static final String[] OPERATORS = {" contains ", " == ", " != ", " >= ", " <= ", " > ", " < "};

    static Condition parse(String text) {
        for (String operator : OPERATORS) {
            int index = text.indexOf(operator);
            if (index > 0) {
                return new Condition(text.substring(0, index).trim(), operator.trim(),
                        text.substring(index + operator.length()).trim());
            }
        }
        throw new IllegalArgumentException("opérateur introuvable (espaces autour obligatoires) : " + text);
    }

    boolean test(Player player, boolean placeholderApi) {
        String a = resolve(player, left, placeholderApi);
        String b = resolve(player, right, placeholderApi);
        if (a == null || b == null) {
            return false;
        }
        Double x = number(a);
        Double y = number(b);
        if (x != null && y != null) {
            return switch (operator) {
                case "==" -> x.doubleValue() == y.doubleValue();
                case "!=" -> x.doubleValue() != y.doubleValue();
                case ">=" -> x >= y;
                case "<=" -> x <= y;
                case ">" -> x > y;
                case "<" -> x < y;
                default -> a.contains(b);
            };
        }
        String la = a.toLowerCase(Locale.ROOT);
        String lb = b.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case "==" -> la.equals(lb);
            case "!=" -> !la.equals(lb);
            case "contains" -> la.contains(lb);
            default -> false;
        };
    }

    /** Sans PlaceholderAPI, un côté qui contient un placeholder ne peut pas être évalué : la condition est fausse. */
    private static String resolve(Player player, String text, boolean placeholderApi) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        return placeholderApi ? PlaceholderAPI.setPlaceholders(player, text).trim() : null;
    }

    private static Double number(String text) {
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
