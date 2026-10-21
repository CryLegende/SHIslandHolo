package org.Cry_Legende.sHIslandHolo;

import com.bgsoftware.superiorskyblock.api.events.*;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;

import eu.decentsoftware.holograms.api.holograms.HologramLine;
import eu.decentsoftware.holograms.api.holograms.HologramPage;
import eu.decentsoftware.holograms.event.HologramClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SHIslandHolo extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<String, HologramConfig> hologramConfigs;

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
        hologramConfigs = new HashMap<>();
        for (String key : config.getConfigurationSection("holograms").getKeys(false)) {
            String path = "holograms." + key;
            List<String> lines = config.getStringList(path + ".lines");
            String[] positionSplit = config.getString(path + ".position").split(",");
            Location offset = new Location(null,
                Double.parseDouble(positionSplit[0]),
                Double.parseDouble(positionSplit[1]),
                Double.parseDouble(positionSplit[2]));
            hologramConfigs.put(key, new HologramConfig(lines, offset));
        }
        getLogger().info("Configurations des hologrammes chargées.");
    }

    private HologramConfig getHologramConfigForLevel(int level) {
        for (Map.Entry<String, HologramConfig> entry : hologramConfigs.entrySet()) {
            String key = entry.getKey();
            HologramConfig config = entry.getValue();

            if (key.contains("-")) {
                String[] parts = key.split("-");
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);
                if (level >= min && level <= max) {
                    return config;
                }
            } else if (key.startsWith("<")) {
                int specificLevel = Integer.parseInt(key.substring(1));
                if (level < specificLevel) {
                    return config;
                }
            } else if (key.startsWith(">")) {
                int specificLevel = Integer.parseInt(key.substring(1));
                if (level > specificLevel) {
                    return config;
                }
            } else {
                int configLevel = Integer.parseInt(key);
                if (level == configLevel) {
                    return config;
                }
            }
        }
        return null;
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

        org.bukkit.World skyblockWorld = island.getCenter(org.bukkit.World.Environment.NORMAL).getWorld();
        Location holoLocation = new Location(skyblockWorld,
            island.getCenter(org.bukkit.World.Environment.NORMAL).getX() + hologramConfig.offset.getX(),
            island.getCenter(org.bukkit.World.Environment.NORMAL).getY() + hologramConfig.offset.getY(),
            island.getCenter(org.bukkit.World.Environment.NORMAL).getZ() + hologramConfig.offset.getZ());

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


    @EventHandler
    public void onIslandLevelChange(IslandWorthUpdateEvent event) {
        Island island = event.getIsland();
        int islandLevel = event.getNewLevel().intValue();
        int islandOldLevel = event.getOldLevel().intValue();

        updateIslandHologram(island, islandLevel, islandOldLevel);
    }

    @EventHandler
    public void onIslandcreate(IslandCreateEvent event) {
        Island island = event.getIsland();
        int islandLevel = 0;
        int islandOldLevel = -1;

        updateIslandHologram(island, islandLevel, islandOldLevel);
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


    @EventHandler
    public void onDisbandIsland(IslandDisbandEvent event) {
        Island island = event.getIsland();

        String hologramName = "island_" + island.getUniqueId() + "_holo";
        Hologram hologram = DHAPI.getHologram(hologramName);

        if (hologram != null) {
            DHAPI.removeHologram(hologramName);
        }
    }

    @EventHandler
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

    @EventHandler
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
