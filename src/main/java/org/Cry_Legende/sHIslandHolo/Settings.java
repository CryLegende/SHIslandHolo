package org.Cry_Legende.sHIslandHolo;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Réglages de config.yml et conseils de tips.yml, relus à chaque /shislandholo reload.
 *
 * @param background null pour le fond par défaut de Minecraft
 * @param sound      clé du son (« block.note_block.pling »), ou null
 */
record Settings(int refreshTicks, Vector position, int viewDistance, Display.Billboard billboard, Color background,
                boolean textShadow, float scale, int placeholderRefreshMs, boolean clickBox, float clickWidth,
                float lineHeight, long clickCooldownMs, boolean announce, String sound, float soundVolume,
                float soundPitch, List<Tip> tips) {

    static Settings load(FileConfiguration config, ConfigurationSection tipsSection, Logger logger) {
        Vector position = parseVector(config.getString("hologram.position", "0,4,8"));
        if (position == null) {
            logger.warning("hologram.position invalide, « 0,4,8 » utilisé.");
            position = new Vector(0, 4, 8);
        }
        Display.Billboard billboard;
        try {
            billboard = Display.Billboard.valueOf(config.getString("hologram.billboard", "CENTER").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("hologram.billboard invalide, CENTER utilisé.");
            billboard = Display.Billboard.CENTER;
        }
        String sound = config.getString("new-tip.sound", "");
        return new Settings(
                Math.max(1, config.getInt("refresh-seconds", 3)) * 20,
                position,
                Math.max(1, config.getInt("hologram.view-distance", 32)),
                billboard,
                parseBackground(config.getString("hologram.background", "default"), logger),
                config.getBoolean("hologram.text-shadow", true),
                (float) config.getDouble("hologram.scale", 1.0),
                Math.max(1, config.getInt("hologram.placeholder-refresh-seconds", 10)) * 1000,
                config.getBoolean("click-box.enabled", true),
                (float) config.getDouble("click-box.width", 3.0),
                (float) config.getDouble("click-box.line-height", 0.25),
                Math.max(0, config.getLong("click-box.cooldown-ms", 500)),
                config.getBoolean("new-tip.announce", true),
                sound == null || sound.isBlank() ? null : sound.trim().toLowerCase(Locale.ROOT),
                (float) config.getDouble("new-tip.sound-volume", 0.6),
                (float) config.getDouble("new-tip.sound-pitch", 1.4),
                loadTips(tipsSection, logger));
    }

    private static List<Tip> loadTips(ConfigurationSection section, Logger logger) {
        List<Tip> tips = new ArrayList<>();
        if (section == null) {
            logger.warning("Section « tips » absente de tips.yml : aucun conseil.");
            return tips;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection tip = section.getConfigurationSection(id);
            if (tip == null) {
                continue;
            }
            try {
                List<String> lines = tip.getStringList("lines");
                if (lines.isEmpty()) {
                    throw new IllegalArgumentException("aucune ligne");
                }
                List<Condition> conditions = new ArrayList<>();
                for (String condition : tip.getStringList("conditions")) {
                    conditions.add(Condition.parse(condition));
                }
                Vector position = null;
                if (tip.isSet("position")) {
                    position = parseVector(tip.getString("position"));
                    if (position == null) {
                        throw new IllegalArgumentException("position invalide");
                    }
                }
                String click = tip.getString("click", "");
                if (click.startsWith("/")) {
                    click = click.substring(1);
                }
                tips.add(new Tip(id, Range.parse(tip.getString("levels")), Range.parse(tip.getString("members")),
                        List.copyOf(conditions), List.copyOf(lines), click.isBlank() ? null : click, position));
            } catch (IllegalArgumentException e) {
                logger.warning("Conseil « " + id + " » ignoré : " + e.getMessage());
            }
        }
        return List.copyOf(tips);
    }

    private static Vector parseVector(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new Vector(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Color parseBackground(String text, Logger logger) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("default")) {
            return null;
        }
        if (text.equalsIgnoreCase("transparent")) {
            return Color.fromARGB(0, 0, 0, 0);
        }
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            if (hex.length() == 6) {
                return Color.fromRGB(Integer.parseInt(hex, 16));
            }
            if (hex.length() == 8) {
                return Color.fromARGB((int) Long.parseLong(hex, 16));
            }
        } catch (NumberFormatException ignored) {
            // message ci-dessous
        }
        logger.warning("hologram.background invalide (« " + text + " »), fond par défaut utilisé.");
        return null;
    }
}
