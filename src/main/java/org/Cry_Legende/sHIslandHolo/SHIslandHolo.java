package org.Cry_Legende.sHIslandHolo;

import com.bgsoftware.superiorskyblock.api.events.*;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;

import eu.decentsoftware.holograms.api.holograms.HologramLine;
import eu.decentsoftware.holograms.api.holograms.HologramPage;
import eu.decentsoftware.holograms.event.HologramClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class SHIslandHolo extends JavaPlugin implements Listener {

    private FileConfiguration config;
    // Paliers lus une fois au chargement, dans l'ordre du fichier
    private List<HologramTier> hologramTiers;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        config = this.getConfig();
        getServer().getPluginManager().registerEvents(this, this);
        loadHologramConfigs();
        getLogger().info("Plugin SHIslandHolo activé.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin SHIslandHolo désactivé.");
    }

    private void loadHologramConfigs() {
        getLogger().info("Chargement des configurations des hologrammes en cours...");
        hologramTiers = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("holograms");
        if (section == null) {
            getLogger().warning("Section 'holograms' absente de config.yml : aucun hologramme.");
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = "holograms." + key;
            try {
                List<String> lines = config.getStringList(path + ".lines");
                String[] positionSplit = config.getString(path + ".position", "").split(",");
                Location offset = new Location(null,
                    Double.parseDouble(positionSplit[0].trim()),
                    Double.parseDouble(positionSplit[1].trim()),
                    Double.parseDouble(positionSplit[2].trim()));
                hologramTiers.add(parseTier(key, new HologramConfig(lines, offset)));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                getLogger().warning("Hologramme '" + key + "' ignoré : palier ou position invalide.");
            }
        }
        getLogger().info("Configurations des hologrammes chargées.");
    }

    /**
     * Clé de palier : "min-max", "<n", ">n" ou "n".
     */
    private static HologramTier parseTier(String key, HologramConfig hologramConfig) {
        if (key.contains("-")) {
            String[] parts = key.split("-");
            return new HologramTier(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), hologramConfig);
        } else if (key.startsWith("<")) {
            return new HologramTier(Integer.MIN_VALUE, Integer.parseInt(key.substring(1)) - 1, hologramConfig);
        } else if (key.startsWith(">")) {
            return new HologramTier(Integer.parseInt(key.substring(1)) + 1, Integer.MAX_VALUE, hologramConfig);
        }
        int configLevel = Integer.parseInt(key);
        return new HologramTier(configLevel, configLevel, hologramConfig);
    }

    private HologramConfig getHologramConfigForLevel(int level) {
        for (HologramTier tier : hologramTiers) {
            if (level >= tier.min() && level <= tier.max()) {
                return tier.config();
            }
        }
        return null;
    }

    /**
     * SSB2 2026.3 attend une Dimension (et non plus un World.Environment) pour trouver le centre d'une île.
     */
    private static Dimension normalDimension() {
        for (Dimension dimension : Dimension.values()) {
            if (dimension.getEnvironment() == org.bukkit.World.Environment.NORMAL) {
                return dimension;
            }
        }
        return Dimension.getByName("NORMAL");
    }

    private void updateIslandHologram(Island island, int islandLevel, int islandOldLevel) {
        if (islandLevel <= islandOldLevel) {
            return;
        }

        String hologramName = "island_" + island.getUniqueId() + "_holo";
        Hologram hologram = DHAPI.getHologram(hologramName);

        HologramConfig hologramConfig = getHologramConfigForLevel(islandLevel);
        HologramConfig hologramOldConfig = getHologramConfigForLevel(islandOldLevel);

        if (hologramConfig == null) {
            if (hologram != null) {
                List<SuperiorPlayer> members = island.getIslandMembers(true);
                for (SuperiorPlayer member : members) {
                    Player player = member.asPlayer();
                    if (player != null && player.isOnline()) {
                        hologram.removeShowPlayer(player);
                    }
                }
            }
            return;
        }

        if (hologramConfig == hologramOldConfig) {
            return;
        }

        Location center = island.getCenter(normalDimension());
        if (center == null || center.getWorld() == null) {
            return;
        }
        Location holoLocation = new Location(center.getWorld(),
            center.getX() + hologramConfig.offset.getX(),
            center.getY() + hologramConfig.offset.getY(),
            center.getZ() + hologramConfig.offset.getZ());

        if (hologram == null) {
            hologram = DHAPI.createHologram(hologramName, holoLocation);
            hologram.setDefaultVisibleState(false);
            hologram.hideAll();
        } else {
            DHAPI.moveHologram(hologram, holoLocation);
        }

        List<String> processedLines = new ArrayList<>(hologramConfig.lines);
        DHAPI.setHologramLines(hologram, processedLines);

        List<SuperiorPlayer> members = island.getIslandMembers(true);
        for (SuperiorPlayer member : members) {
            Player player = member.asPlayer();
            if (player != null && player.isOnline()) {
                hologram.setShowPlayer(player);
            }
        }
    }


    /**
     * SSB2 déclenche IslandWorthUpdateEvent en asynchrone : l'hologramme est toujours géré sur le thread principal.
     */
    private void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    @EventHandler
    public void onIslandLevelChange(IslandWorthUpdateEvent event) {
        Island island = event.getIsland();
        int islandLevel = event.getNewLevel().intValue();
        int islandOldLevel = event.getOldLevel().intValue();
        if (islandLevel <= islandOldLevel) {
            return;
        }

        runSync(() -> updateIslandHologram(island, islandLevel, islandOldLevel));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandcreate(IslandCreateEvent event) {
        Island island = event.getIsland();
        int islandLevel = 0;
        int islandOldLevel = -1;

        runSync(() -> updateIslandHologram(island, islandLevel, islandOldLevel));
    }


    @EventHandler
    public void onHologramClick(HologramClickEvent event) {
        Player player = event.getPlayer();
        Hologram hologram = event.getHologram();
        String hologramName = hologram.getName();

        SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayer(player);
        if (sPlayer != null && sPlayer.getIsland() != null) {
            String islandHologramName = "island_" + sPlayer.getIsland().getUniqueId() + "_holo";
            if (hologramName.equals(islandHologramName)) {
                hologram.removeShowPlayer(player);
            }
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisbandIsland(IslandDisbandEvent event) {
        Island island = event.getIsland();

        String hologramName = "island_" + island.getUniqueId() + "_holo";
        Hologram hologram = DHAPI.getHologram(hologramName);

        if (hologram != null) {
            DHAPI.removeHologram(hologramName);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandJoin(IslandJoinEvent event) {
        Island island = event.getIsland();
        SuperiorPlayer asPlayer = event.getPlayer();

        String hologramName = "island_" + island.getUniqueId() + "_holo";
        Hologram hologram = DHAPI.getHologram(hologramName);

        if (hologram != null) {
            Player player = asPlayer.asPlayer();
            if (player != null && player.isOnline()) {
                hologram.setShowPlayer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandQuit(IslandQuitEvent event) {
        Island island = event.getIsland();
        SuperiorPlayer asPlayer = event.getPlayer();

        String hologramName = "island_" + island.getUniqueId() + "_holo";
        Hologram hologram = DHAPI.getHologram(hologramName);

        if (hologram != null) {
            Player player = asPlayer.asPlayer();
            if (player != null && player.isOnline()) {
                hologram.removeShowPlayer(player);
            }
        }
    }

    //  @EventHandler
    //public void onJoin(PlayerJoinEvent event) {
    //SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayer(event.getPlayer());
    //if (sPlayer != null) {
    //    Island island = sPlayer.getIsland();
    //    if (island != null) {
    //          String hologramName = "island_" + island.getUniqueId() + "_holo";
    //      Hologram hologram = DHAPI.getHologram(hologramName);
    //      Player player = sPlayer.asPlayer();
    //      if (hologram != null && sPlayer.isOnline()) {
    //          hologram.setShowPlayer(player);
    //      }
    //    }
    //  }
    //}


}

class HologramConfig {
    List<String> lines;
    Location offset;

    HologramConfig(List<String> lines, Location offset) {
        this.lines = lines;
        this.offset = offset;
    }

}

record HologramTier(int min, int max, HologramConfig config) {
}
