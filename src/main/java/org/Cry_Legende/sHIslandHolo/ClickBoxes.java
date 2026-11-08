package org.Cry_Legende.sHIslandHolo;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Zones cliquables des hologrammes : une entité « interaction » qui n'existe que chez le joueur concerné,
 * envoyée quand FancyHolograms lui montre son hologramme et retirée quand il le cache.
 * Les clics arrivent sur le thread réseau : seules des maps concurrentes y sont lues.
 */
final class ClickBoxes {

    // Métadonnées d'une entité interaction (depuis 1.19.4) : largeur, hauteur, réponse au clic
    private static final int WIDTH = 8;
    private static final int HEIGHT = 9;
    private static final int RESPONSIVE = 10;

    /** Zone d'un hologramme, visible de son seul propriétaire. */
    record Box(int entityId, UUID entityUuid, UUID owner, Location location, float width, float height) {
    }

    private final Map<String, Box> byHologram = new ConcurrentHashMap<>();
    private final Map<Integer, Box> byEntity = new ConcurrentHashMap<>();
    private PacketListenerCommon listener;

    static int newEntityId() {
        return SpigotReflectionUtil.generateEntityId();
    }

    /** Écoute les clics sur les zones ; « onClick » reçoit le propriétaire et vrai pour un clic gauche. */
    void listen(BiConsumer<UUID, Boolean> onClick) {
        listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                int entityId;
                boolean left;
                if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
                    // Depuis 26.1, le clic gauche sur une entité a son propre paquet
                    entityId = new WrapperPlayClientAttack(event).getEntityId();
                    left = true;
                } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                    entityId = packet.getEntityId();
                    left = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
                    if (!left && packet.getHand() != InteractionHand.MAIN_HAND) {
                        // Seule la main principale compte ; le paquet de l'autre main est tout de même bloqué
                        if (byEntity.containsKey(entityId)) {
                            event.setCancelled(true);
                        }
                        return;
                    }
                } else {
                    return;
                }
                Box box = byEntity.get(entityId);
                if (box == null) {
                    return;
                }
                // Entité inconnue du serveur : le paquet s'arrête ici
                event.setCancelled(true);
                if (box.owner().equals(event.getUser().getUUID())) {
                    // Avant 26.1, un clic droit envoie INTERACT_AT puis INTERACT : le délai entre deux clics garde le premier
                    onClick.accept(box.owner(), left);
                }
            }
        });
    }

    void close() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
    }

    void register(String hologram, Box box) {
        Box previous = byHologram.put(hologram, box);
        if (previous != null) {
            byEntity.remove(previous.entityId());
        }
        byEntity.put(box.entityId(), box);
    }

    Box unregister(String hologram) {
        Box box = byHologram.remove(hologram);
        if (box != null) {
            byEntity.remove(box.entityId());
        }
        return box;
    }

    Box get(String hologram) {
        return byHologram.get(hologram);
    }

    static void spawn(Player viewer, Box box) {
        Location at = box.location();
        send(viewer, new WrapperPlayServerSpawnEntity(box.entityId(), Optional.of(box.entityUuid()), EntityTypes.INTERACTION,
                new Vector3d(at.getX(), at.getY(), at.getZ()), 0.0f, 0.0f, 0.0f, 0, Optional.empty()));
        send(viewer, new WrapperPlayServerEntityMetadata(box.entityId(), List.of(
                new EntityData<>(WIDTH, EntityDataTypes.FLOAT, box.width()),
                new EntityData<>(HEIGHT, EntityDataTypes.FLOAT, box.height()),
                new EntityData<>(RESPONSIVE, EntityDataTypes.BOOLEAN, true))));
    }

    static void destroy(Player viewer, Box box) {
        send(viewer, new WrapperPlayServerDestroyEntities(box.entityId()));
    }

    private static void send(Player viewer, PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, packet);
        } catch (RuntimeException ignored) {
            // joueur en cours de déconnexion
        }
    }
}
