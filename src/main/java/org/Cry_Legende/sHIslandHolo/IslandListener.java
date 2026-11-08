package org.Cry_Legende.sHIslandHolo;

import com.bgsoftware.superiorskyblock.api.events.IslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandJoinEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandKickEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandQuitEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandWorthUpdateEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import de.oliver.fancyholograms.api.events.HologramHideEvent;
import de.oliver.fancyholograms.api.events.HologramShowEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Événements qui changent le conseil d'un joueur avant la vérification périodique : arrivée, téléportation,
 * île créée, rejointe, quittée ou dissoute, niveau d'île recalculé.
 */
final class IslandListener implements Listener {

    private final SHIslandHolo plugin;
    private final HoloService holos;

    IslandListener(SHIslandHolo plugin, HoloService holos) {
        this.plugin = plugin;
        this.holos = holos;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer().getUniqueId(), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        holos.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        refreshLater(event.getPlayer().getUniqueId(), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreate(IslandCreateEvent event) {
        // L'île est collée et le joueur téléporté juste après l'événement
        refreshLater(event.getPlayer().getUniqueId(), 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandJoin(IslandJoinEvent event) {
        refreshLater(event.getPlayer().getUniqueId(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandQuit(IslandQuitEvent event) {
        refreshLater(event.getPlayer().getUniqueId(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandKick(IslandKickEvent event) {
        refreshLater(event.getTarget().getUniqueId(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent event) {
        refreshMembers(event.getIsland());
    }

    /** SSB2 déclenche cet événement en asynchrone : la liste des membres est lue ici, le reste attend le thread principal. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorthUpdate(IslandWorthUpdateEvent event) {
        if (event.getNewLevel().intValue() != event.getOldLevel().intValue()) {
            refreshMembers(event.getIsland());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHologramShow(HologramShowEvent event) {
        String name = event.getHologram().getName();
        if (HoloService.isOurs(name)) {
            holos.onShown(name, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHologramHide(HologramHideEvent event) {
        String name = event.getHologram().getName();
        if (HoloService.isOurs(name)) {
            holos.onHidden(name, event.getPlayer());
        }
    }

    private void refreshMembers(Island island) {
        List<UUID> members = new ArrayList<>();
        for (SuperiorPlayer member : island.getIslandMembers(true)) {
            members.add(member.getUniqueId());
        }
        for (UUID member : members) {
            refreshLater(member, 1L);
        }
    }

    private void refreshLater(UUID uuid, long ticks) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                holos.refresh(player);
            }
        }, ticks);
    }
}
