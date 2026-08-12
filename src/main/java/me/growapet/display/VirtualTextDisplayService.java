package me.growapet.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.growapet.GrowAPet;
import net.kyori.adventure.text.Component;
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
import java.util.function.Predicate;

/**
 * Owns viewer-scoped, packet-only text displays. No Bukkit entity is created.
 * All methods are deliberately main-thread confined because they inspect Bukkit
 * player/world state while composing packets.
 */
public final class VirtualTextDisplayService {
    private static final int META_NO_GRAVITY = 5;
    private static final int META_SCALE = 12;
    private static final int META_BILLBOARD = 15;
    private static final int META_BRIGHTNESS = 16;
    private static final int META_VIEW_RANGE = 17;
    private static final int META_TEXT = 23;
    private static final int META_LINE_WIDTH = 24;
    private static final int META_BACKGROUND = 25;
    private static final int META_TEXT_OPACITY = 26;
    private static final int META_TEXT_FLAGS = 27;

    private static final byte CENTER_BILLBOARD = 3;
    private static final byte TEXT_SHADOW = 1;
    private static final int FULL_BRIGHTNESS = (15 << 4) | (15 << 20);

    private final GrowAPet plugin;
    private final Map<UUID, Display> displays = new HashMap<>();
    private BukkitTask visibilityTask;

    public VirtualTextDisplayService(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void start() {
        requirePrimaryThread();
        if (visibilityTask != null) return;
        visibilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 10L, 10L);
    }

    public void stop() {
        requirePrimaryThread();
        if (visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
        for (Display display : List.copyOf(displays.values())) {
            destroyForKnownViewers(display);
        }
        displays.clear();
    }

    public Handle create(Location location, Component text, Predicate<Player> visibility) {
        requirePrimaryThread();
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("A virtual display requires a loaded world");
        }
        UUID handleId = UUID.randomUUID();
        Display display = new Display(
                handleId,
                SpigotReflectionUtil.generateEntityId(location.getWorld()),
                UUID.randomUUID(),
                location.clone(),
                text == null ? Component.empty() : text,
                visibility == null ? ignored -> true : visibility
        );
        displays.put(handleId, display);
        refresh(display);
        return new Handle(handleId);
    }

    public void update(Handle handle, Component text) {
        requirePrimaryThread();
        Display display = find(handle);
        if (display == null) return;
        display.text = text == null ? Component.empty() : text;
        WrapperPlayServerEntityMetadata packet = metadata(display);
        for (UUID viewerId : List.copyOf(display.viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) send(viewer, packet);
        }
    }

    public void teleport(Handle handle, Location location) {
        requirePrimaryThread();
        Display display = find(handle);
        if (display == null || location == null || location.getWorld() == null) return;
        boolean changedWorld = !display.location.getWorld().equals(location.getWorld());
        display.location = location.clone();
        if (changedWorld) {
            destroyForKnownViewers(display);
            refresh(display);
            return;
        }
        WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(
                display.entityId, vector(location), location.getYaw(), location.getPitch(), false);
        for (UUID viewerId : List.copyOf(display.viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) send(viewer, packet);
        }
    }

    public void remove(Handle handle) {
        requirePrimaryThread();
        Display display = find(handle);
        if (display == null) return;
        displays.remove(display.handleId);
        destroyForKnownViewers(display);
    }

    public void refreshViewer(Player viewer) {
        requirePrimaryThread();
        for (Display display : displays.values()) {
            reconcile(display, viewer);
        }
    }

    public int size() {
        return displays.size();
    }

    private void refreshAll() {
        requirePrimaryThread();
        for (Display display : List.copyOf(displays.values())) {
            refresh(display);
        }
    }

    private void refresh(Display display) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            reconcile(display, viewer);
        }
        display.viewers.removeIf(viewerId -> Bukkit.getPlayer(viewerId) == null);
    }

    private void reconcile(Display display, Player viewer) {
        boolean shouldSee = canSee(display, viewer);
        boolean sees = display.viewers.contains(viewer.getUniqueId());
        if (shouldSee && !sees) {
            spawn(viewer, display);
            display.viewers.add(viewer.getUniqueId());
        } else if (!shouldSee && sees) {
            destroy(viewer, display.entityId);
            display.viewers.remove(viewer.getUniqueId());
        }
    }

    private boolean canSee(Display display, Player viewer) {
        if (!viewer.isOnline() || !viewer.getWorld().equals(display.location.getWorld())) return false;
        double range = Math.max(8.0, plugin.getConfigManager().config().getDouble("virtual-displays.view-distance", 64.0));
        if (viewer.getLocation().distanceSquared(display.location) > range * range) return false;
        try {
            return display.visibility.test(viewer);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Virtual display visibility check failed: " + error.getMessage());
            return false;
        }
    }

    private void spawn(Player viewer, Display display) {
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                display.entityId,
                Optional.of(display.entityUuid),
                EntityTypes.TEXT_DISPLAY,
                vector(display.location),
                display.location.getPitch(),
                display.location.getYaw(),
                display.location.getYaw(),
                0,
                Optional.of(new Vector3d(0, 0, 0))
        );
        send(viewer, spawn);
        send(viewer, metadata(display));
    }

    private WrapperPlayServerEntityMetadata metadata(Display display) {
        List<EntityData<?>> values = new ArrayList<>();
        values.add(new EntityData<>(META_NO_GRAVITY, EntityDataTypes.BOOLEAN, true));
        values.add(new EntityData<>(META_SCALE, EntityDataTypes.VECTOR3F,
                new com.github.retrooper.packetevents.util.Vector3f(1.0f, 1.0f, 1.0f)));
        values.add(new EntityData<>(META_BILLBOARD, EntityDataTypes.BYTE, CENTER_BILLBOARD));
        values.add(new EntityData<>(META_BRIGHTNESS, EntityDataTypes.INT, FULL_BRIGHTNESS));
        values.add(new EntityData<>(META_VIEW_RANGE, EntityDataTypes.FLOAT, 1.0f));
        values.add(new EntityData<>(META_TEXT, EntityDataTypes.ADV_COMPONENT, display.text));
        values.add(new EntityData<>(META_LINE_WIDTH, EntityDataTypes.INT, 240));
        values.add(new EntityData<>(META_BACKGROUND, EntityDataTypes.INT, 0x40000000));
        values.add(new EntityData<>(META_TEXT_OPACITY, EntityDataTypes.BYTE, (byte) -1));
        values.add(new EntityData<>(META_TEXT_FLAGS, EntityDataTypes.BYTE, TEXT_SHADOW));
        return new WrapperPlayServerEntityMetadata(display.entityId, values);
    }

    private void destroyForKnownViewers(Display display) {
        for (UUID viewerId : List.copyOf(display.viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) destroy(viewer, display.entityId);
        }
        display.viewers.clear();
    }

    private static void destroy(Player viewer, int entityId) {
        send(viewer, new WrapperPlayServerDestroyEntities(entityId));
    }

    private static void send(Player player, Object packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private Display find(Handle handle) {
        return handle == null ? null : displays.get(handle.id());
    }

    private static Vector3d vector(Location location) {
        return new Vector3d(location.getX(), location.getY(), location.getZ());
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Virtual displays may only be changed on the server thread");
        }
    }

    public record Handle(UUID id) {}

    private static final class Display {
        private final UUID handleId;
        private final int entityId;
        private final UUID entityUuid;
        private final Predicate<Player> visibility;
        private final Set<UUID> viewers = new HashSet<>();
        private Location location;
        private Component text;

        private Display(UUID handleId, int entityId, UUID entityUuid, Location location,
                        Component text, Predicate<Player> visibility) {
            this.handleId = handleId;
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            this.location = location;
            this.text = text;
            this.visibility = visibility;
        }
    }
}
