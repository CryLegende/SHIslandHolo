package org.Cry_Legende.sHIslandHolo;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.data.property.Visibility;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un hologramme par joueur connecté qui se trouve sur sa propre île : le premier conseil qui lui correspond.
 * Les hologrammes ne sont pas sauvegardés par FancyHolograms, ils sont recréés à chaque passage sur l'île.
 * Tout ici tourne sur le thread principal, sauf onShown/onHidden.
 */
final class HoloService {

    private static final String PREFIX = "shislandholo_";

    /** Conseil affiché à un joueur. */
    private record Shown(String hologram, Tip tip, Location location) {
    }

    private final SHIslandHolo plugin;
    private final HologramManager manager;
    private final ClickBoxes boxes;
    private final NamespacedKey hiddenKey;
    private final NamespacedKey seenKey;
    private final Dimension normal;
    private final Map<UUID, Shown> shown = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    HoloService(SHIslandHolo plugin, ClickBoxes boxes) {
        this.plugin = plugin;
        this.manager = FancyHologramsPlugin.get().getHologramManager();
        this.boxes = boxes;
        this.hiddenKey = new NamespacedKey(plugin, "hidden-tips");
        this.seenKey = new NamespacedKey(plugin, "seen-tips");
        this.normal = normalDimension();
    }

    static boolean isOurs(String hologram) {
        return hologram.startsWith(PREFIX);
    }

    void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** Affiche, change ou retire l'hologramme du joueur selon sa position et le conseil qui lui correspond. */
    void refresh(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Settings settings = plugin.settings();
        Tip tip = null;
        Location location = null;
        Island island = ownIslandUnder(player);
        if (island != null) {
            Location center = island.getCenter(normal);
            if (center != null && center.getWorld() != null && center.getWorld().equals(player.getWorld())) {
                tip = currentTip(player, island, settings);
                if (tip != null) {
                    location = center.clone().add(tip.position() != null ? tip.position() : settings.position());
                }
            }
        }
        Shown current = shown.get(player.getUniqueId());
        if (tip == null) {
            if (current != null) {
                remove(player);
            }
            return;
        }
        if (current != null && current.tip() == tip && current.location().equals(location)
                && manager.getHologram(current.hologram()).isPresent()) {
            return;
        }
        if (current != null) {
            remove(player);
        }
        show(player, tip, location, settings);
        markSeen(player, tip, settings);
    }

    /** Île du joueur, s'il se trouve dans son périmètre. */
    private static Island ownIslandUnder(Player player) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        Island island = superiorPlayer == null ? null : superiorPlayer.getIsland();
        return island != null && island.isInsideRange(player.getLocation()) ? island : null;
    }

    private Tip currentTip(Player player, Island island, Settings settings) {
        int level = island.getIslandLevel().intValue();
        int members = island.getIslandMembers(true).size();
        Set<String> hidden = readSet(player.getPersistentDataContainer(), hiddenKey);
        boolean placeholderApi = plugin.hasPlaceholderApi();
        for (Tip tip : settings.tips()) {
            if (!hidden.contains(tip.id()) && tip.matches(player, level, members, placeholderApi)) {
                return tip;
            }
        }
        return null;
    }

    private void show(Player player, Tip tip, Location location, Settings settings) {
        String name = PREFIX + player.getUniqueId();
        TextHologramData data = new TextHologramData(name, location);
        data.setText(new ArrayList<>(tip.lines()));
        data.setPersistent(false);
        data.setVisibility(Visibility.MANUAL);
        data.setVisibilityDistance(settings.viewDistance());
        data.setBillboard(settings.billboard());
        data.setTextShadow(settings.textShadow());
        data.setScale(new Vector3f(settings.scale(), settings.scale(), settings.scale()));
        if (settings.background() != null) {
            data.setBackground(settings.background());
        }
        if (tip.hasPlaceholders()) {
            data.setTextUpdateInterval(settings.placeholderRefreshMs());
        }
        Hologram hologram = manager.create(data);
        if (hologram == null) {
            plugin.getLogger().warning("FancyHolograms n'a pas créé l'hologramme de " + player.getName() + ".");
            return;
        }
        // La zone doit être connue avant que FancyHolograms montre l'hologramme (HologramShowEvent)
        if (settings.clickBox()) {
            float height = tip.lines().size() * settings.lineHeight() * settings.scale() + 0.1f;
            boxes.register(name, new ClickBoxes.Box(ClickBoxes.newEntityId(), UUID.randomUUID(), player.getUniqueId(),
                    location.clone(), settings.clickWidth() * settings.scale(), height));
        }
        Visibility.ManualVisibility.addDistantViewer(name, player.getUniqueId());
        manager.addHologram(hologram);
        shown.put(player.getUniqueId(), new Shown(name, tip, location));
        hologram.forceUpdateShownStateFor(player);
    }

    /** Retire l'hologramme du joueur et sa zone cliquable. */
    void remove(Player player) {
        Shown current = shown.remove(player.getUniqueId());
        if (current == null) {
            return;
        }
        ClickBoxes.Box box = boxes.unregister(current.hologram());
        if (box != null && player.isOnline()) {
            ClickBoxes.destroy(player, box);
        }
        Visibility.ManualVisibility.remove(current.hologram());
        manager.getHologram(current.hologram()).ifPresent(manager::removeHologram);
    }

    void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
        }
        // Joueurs déjà partis : leurs hologrammes resteraient dans le manager
        for (Shown current : new ArrayList<>(shown.values())) {
            boxes.unregister(current.hologram());
            Visibility.ManualVisibility.remove(current.hologram());
            manager.getHologram(current.hologram()).ifPresent(manager::removeHologram);
        }
        shown.clear();
    }

    void forget(Player player) {
        remove(player);
        lastClick.remove(player.getUniqueId());
    }

    /** Clic sur la zone de l'hologramme (thread principal). */
    void click(UUID owner, boolean left) {
        Player player = Bukkit.getPlayer(owner);
        Shown current = shown.get(owner);
        if (player == null || current == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(owner);
        if (last != null && now - last < plugin.settings().clickCooldownMs()) {
            return;
        }
        lastClick.put(owner, now);
        if (!left && current.tip().click() != null) {
            // Comme si le joueur tapait la commande : bloqueurs de commandes et menus l'interceptent normalement
            player.chat("/" + current.tip().click());
            return;
        }
        hide(player);
    }

    /** Cache le conseil affiché au joueur ; faux s'il n'en a aucun. */
    boolean hide(Player player) {
        Shown current = shown.get(player.getUniqueId());
        if (current == null) {
            return false;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Set<String> hidden = readSet(pdc, hiddenKey);
        hidden.add(current.tip().id());
        writeSet(pdc, hiddenKey, hidden);
        plugin.messages().send(player, "tip-hidden");
        refresh(player);
        return true;
    }

    /** Réaffiche tous les conseils cachés ; faux s'il n'y en avait aucun. */
    boolean restore(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (readSet(pdc, hiddenKey).isEmpty()) {
            return false;
        }
        pdc.remove(hiddenKey);
        refresh(player);
        return true;
    }

    /** FancyHolograms montre un hologramme : la zone cliquable suit (appelé depuis n'importe quel thread). */
    void onShown(String hologram, Player viewer) {
        ClickBoxes.Box box = boxes.get(hologram);
        if (box != null && box.owner().equals(viewer.getUniqueId())) {
            ClickBoxes.spawn(viewer, box);
        }
    }

    void onHidden(String hologram, Player viewer) {
        ClickBoxes.Box box = boxes.get(hologram);
        if (box != null && box.owner().equals(viewer.getUniqueId())) {
            ClickBoxes.destroy(viewer, box);
        }
    }

    /** Annonce un conseil jamais vu, sauf le tout premier du joueur. */
    private void markSeen(Player player, Tip tip, Settings settings) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Set<String> seen = readSet(pdc, seenKey);
        if (!seen.add(tip.id())) {
            return;
        }
        boolean first = seen.size() == 1;
        writeSet(pdc, seenKey, seen);
        if (first || !settings.announce()) {
            return;
        }
        plugin.messages().send(player, "new-tip");
        if (settings.sound() != null) {
            player.playSound(player.getLocation(), settings.sound(), settings.soundVolume(), settings.soundPitch());
        }
    }

    private static Set<String> readSet(PersistentDataContainer pdc, NamespacedKey key) {
        Set<String> values = new LinkedHashSet<>();
        String raw = pdc.get(key, PersistentDataType.STRING);
        if (raw != null) {
            for (String value : raw.split(",")) {
                if (!value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
        return values;
    }

    private static void writeSet(PersistentDataContainer pdc, NamespacedKey key, Set<String> values) {
        if (values.isEmpty()) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, String.join(",", values));
        }
    }

    /** SSB2 2026.3 attend une Dimension pour trouver le centre d'une île. */
    private static Dimension normalDimension() {
        for (Dimension dimension : Dimension.values()) {
            if (dimension.getEnvironment() == World.Environment.NORMAL) {
                return dimension;
            }
        }
        return Dimension.getByName("NORMAL");
    }
}
