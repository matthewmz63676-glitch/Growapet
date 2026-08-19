package me.growapet.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.growapet.GrowAPet;
import me.growapet.models.Pet;
import me.growapet.models.Plot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Packet-only plot companions. These never create Bukkit or Minecraft server entities. */
public final class VirtualPetService {
    private final GrowAPet plugin;
    private final Map<UUID, VirtualPet> pets = new HashMap<>();
    private BukkitTask task;
    private long animationStep;

    public VirtualPetService(GrowAPet plugin) { this.plugin = plugin; }

    public void start() {
        requireMain();
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        requireMain();
        if (task != null) task.cancel();
        task = null;
        for (VirtualPet pet : List.copyOf(pets.values())) destroyKnown(pet);
        pets.clear();
    }

    public void show(Pet pet, Location anchor) {
        requireMain();
        remove(pet.getUuid());
        if (anchor == null || anchor.getWorld() == null) return;
        EntityType type = packetType(pet);
        VirtualPet visual = new VirtualPet(pet.getUuid(), pet.getOwner(),
                SpigotReflectionUtil.generateEntityId(anchor.getWorld()), UUID.randomUUID(), type,
                anchor.clone(), anchor.clone(), scale(pet.getSize()), name(pet));
        pets.put(pet.getUuid(), visual);
        reconcileAll(visual);
    }

    public void update(Pet pet) {
        requireMain();
        VirtualPet visual = pets.get(pet.getUuid());
        if (visual == null) return;
        visual.name = name(pet);
        visual.scale = scale(pet.getSize());
        for (UUID viewerId : List.copyOf(visual.viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) sendMetadata(viewer, visual);
        }
    }

    public void remove(UUID petId) {
        requireMain();
        VirtualPet visual = pets.remove(petId);
        if (visual != null) destroyKnown(visual);
    }

    private void tick() {
        requireMain();
        animationStep++;
        for (VirtualPet pet : List.copyOf(pets.values())) {
            double phase = (pet.petId.hashCode() & 1023) / 1024.0 * Math.PI * 2;
            double angle = phase + animationStep * 0.10;
            pet.location = pet.anchor.clone().add(Math.cos(angle) * 1.4, 0, Math.sin(angle) * 1.4);
            pet.location.setYaw((float) Math.toDegrees(-angle + Math.PI / 2));
            for (UUID viewerId : List.copyOf(pet.viewers)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) send(viewer, positionSync(pet.entityId, pet.location, 0, true));
            }
            reconcileAll(pet);
        }
    }

    private void reconcileAll(VirtualPet pet) {
        for (Player viewer : Bukkit.getOnlinePlayers()) reconcile(pet, viewer);
        pet.viewers.removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void reconcile(VirtualPet pet, Player viewer) {
        boolean visible = canSee(pet, viewer);
        boolean spawned = pet.viewers.contains(viewer.getUniqueId());
        if (visible && !spawned) {
            spawn(viewer, pet);
            pet.viewers.add(viewer.getUniqueId());
        } else if (!visible && spawned) {
            destroy(viewer, pet.entityId);
            pet.viewers.remove(viewer.getUniqueId());
        }
    }

    private boolean canSee(VirtualPet pet, Player viewer) {
        if (!viewer.isOnline() || !viewer.getWorld().equals(pet.location.getWorld())) return false;
        double range = Math.max(8, plugin.getConfigManager().config().getDouble("virtual-displays.view-distance", 64));
        if (viewer.getLocation().distanceSquared(pet.location) > range * range) return false;
        Plot plot = plugin.getPlotManager().getPlot(pet.owner);
        return plot != null && plot.contains(pet.location) && plot.contains(viewer.getLocation());
    }

    private void spawn(Player viewer, VirtualPet pet) {
        send(viewer, new WrapperPlayServerSpawnEntity(pet.entityId, Optional.of(pet.entityUuid), pet.type,
                vector(pet.location), 0, pet.location.getYaw(), pet.location.getYaw(), 0,
                Optional.of(new Vector3d(0, 0, 0))));
        sendMetadata(viewer, pet);
    }

    private void sendMetadata(Player viewer, VirtualPet pet) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(pet.name)));
        metadata.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, true));
        metadata.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
        send(viewer, new WrapperPlayServerEntityMetadata(pet.entityId, metadata));
        WrapperPlayServerUpdateAttributes.Property scale = new WrapperPlayServerUpdateAttributes.Property(
                Attributes.SCALE, pet.scale, List.of());
        send(viewer, new WrapperPlayServerUpdateAttributes(pet.entityId, List.of(scale)));
    }

    private void destroyKnown(VirtualPet pet) {
        for (UUID viewerId : List.copyOf(pet.viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) destroy(viewer, pet.entityId);
        }
        pet.viewers.clear();
    }

    private static void destroy(Player viewer, int id) { send(viewer, new WrapperPlayServerDestroyEntities(id)); }
    /** Paper 1.21.11 uses sync_entity_position for the full position payload. */
    private static WrapperPlayServerEntityPositionSync positionSync(int entityId, Location location, float pitch, boolean onGround) {
        return new WrapperPlayServerEntityPositionSync(entityId,
                new EntityPositionData(vector(location), Vector3d.zero(), location.getYaw(), pitch), onGround);
    }
    private static void send(Player viewer, PacketWrapper<?> packet) { PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet); }
    private static Vector3d vector(Location location) { return new Vector3d(location.getX(), location.getY(), location.getZ()); }

    private static EntityType packetType(Pet pet) {
        EntityType type = EntityTypes.getByName(pet.getEntityType().getKey().toString());
        return type != null && type.isInstanceOf(EntityTypes.LIVINGENTITY) ? type : EntityTypes.CAT;
    }

    /** Maps the persisted 1..1000 score to Minecraft's safe 0.75x..8x scale range. */
    public static double scale(int sizeScore) {
        int clamped = Math.max(1, Math.min(1000, sizeScore));
        return 0.75 + (clamped - 1) * (7.25 / 999.0);
    }

    private static Component name(Pet pet) {
        return Component.text(pet.getDisplayName() + " • Lv. " + pet.getLevel(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Virtual pets may only change on the server thread");
    }

    private static final class VirtualPet {
        private final UUID petId;
        private final UUID owner;
        private final int entityId;
        private final UUID entityUuid;
        private final EntityType type;
        private final Location anchor;
        private final Set<UUID> viewers = new HashSet<>();
        private Location location;
        private double scale;
        private Component name;

        private VirtualPet(UUID petId, UUID owner, int entityId, UUID entityUuid, EntityType type,
                           Location anchor, Location location, double scale, Component name) {
            this.petId = petId; this.owner = owner; this.entityId = entityId; this.entityUuid = entityUuid;
            this.type = type; this.anchor = anchor; this.location = location; this.scale = scale; this.name = name;
        }
    }
}
