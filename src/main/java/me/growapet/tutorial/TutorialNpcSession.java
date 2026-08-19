package me.growapet.tutorial;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.growapet.GrowAPet;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Viewer-only PacketEvents player NPC with packet-only speech. No Bukkit entity is created. */
final class TutorialNpcSession {
    private static final double SPEECH_Y_OFFSET = 2.15D;

    private final GrowAPet plugin;
    private final Player target;
    private final World world;
    private final int entityId;
    private final UUID fakeUuid = UUID.randomUUID();
    private final double blocksPerTick;
    private double x, y, z;
    private float yaw, pitch;
    private VirtualTextDisplayService.Handle speech;
    private BukkitTask movement;
    private boolean spawned;
    private boolean ended;
    private TutorialStage stage;

    TutorialNpcSession(GrowAPet plugin, Player target, org.bukkit.Location start, double blocksPerTick, TutorialStage stage) {
        this.plugin = plugin;
        this.target = target;
        this.world = start.getWorld();
        this.entityId = SpigotReflectionUtil.generateEntityId(world);
        this.x = start.getX(); this.y = start.getY(); this.z = start.getZ();
        this.yaw = start.getYaw(); this.pitch = start.getPitch();
        this.blocksPerTick = Math.max(0.05D, Math.min(1.0D, blocksPerTick));
        this.stage = stage;
    }

    void spawn() {
        requireMain();
        if (spawned || ended) return;
        spawned = true;
        UserProfile profile = profile();
        send(new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile)));
        send(new WrapperPlayServerSpawnEntity(entityId, Optional.of(fakeUuid), EntityTypes.PLAYER,
                vector(), pitch, yaw, yaw, 0, Optional.empty()));
        speech = plugin.getVirtualTextDisplays().create(speechLocation(), net.kyori.adventure.text.Component.empty(),
                viewer -> viewer.getUniqueId().equals(target.getUniqueId()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!ended && target.isOnline()) send(new WrapperPlayServerPlayerInfoRemove(List.of(fakeUuid)));
        }, 8L);
    }

    private UserProfile profile() {
        UserProfile profile = new UserProfile(fakeUuid, target.getName());
        PlayerTextures textures = target.getPlayerProfile().getTextures();
        if (textures.getSkin() != null) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textures.getSkin() + "\"}}}";
            String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            profile.setTextureProperties(List.of(new TextureProperty("textures", value, "")));
        }
        return profile;
    }

    void say(String miniMessage, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        requireMain();
        if (!ended && speech != null) plugin.getVirtualTextDisplays().update(speech, Messages.parse(miniMessage, resolvers));
    }

    void swingArm() {
        if (!ended && target.isOnline()) send(new WrapperPlayServerEntityAnimation(entityId,
                WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));
    }

    void walkTo(org.bukkit.Location destination, Runnable onArrive) {
        requireMain();
        if (ended || destination == null || destination.getWorld() == null || !world.equals(destination.getWorld())) return;
        if (movement != null) movement.cancel();
        double dx = destination.getX() - x, dz = destination.getZ() - z;
        double distance = Math.hypot(dx, dz);
        if (distance < 0.05D) { onArrive.run(); return; }
        double stepX = dx / distance * blocksPerTick, stepZ = dz / distance * blocksPerTick;
        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)); pitch = 0;
        send(new WrapperPlayServerEntityHeadLook(entityId, yaw));
        int total = (int) Math.ceil(distance / blocksPerTick);
        int[] taken = {0};
        movement = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (ended || !target.isOnline()) { cancelMovement(); return; }
            taken[0]++;
            boolean last = taken[0] >= total;
            if (last) { x = destination.getX(); y = destination.getY(); z = destination.getZ(); }
            else { x += stepX; z += stepZ; if (taken[0] % 6 == 0) swingArm(); }
            send(new WrapperPlayServerEntityPositionSync(entityId,
                    new EntityPositionData(vector(), Vector3d.zero(), yaw, pitch), true));
            if (speech != null) plugin.getVirtualTextDisplays().teleport(speech, speechLocation());
            if (last) { cancelMovement(); onArrive.run(); }
        }, 1L, 1L);
    }

    void despawn() {
        requireMain();
        if (ended) return;
        ended = true;
        cancelMovement();
        if (target.isOnline()) {
            try {
                send(new WrapperPlayServerDestroyEntities(entityId));
                send(new WrapperPlayServerPlayerInfoRemove(List.of(fakeUuid)));
            } catch (RuntimeException error) {
                plugin.getLogger().fine("Tutorial NPC packet cleanup skipped for " + target.getUniqueId() + ": " + error.getMessage());
            }
        }
        if (speech != null) plugin.getVirtualTextDisplays().remove(speech);
        speech = null;
    }

    private void cancelMovement() { if (movement != null) movement.cancel(); movement = null; }
    private void send(PacketWrapper<?> packet) { PacketEvents.getAPI().getPlayerManager().sendPacket(target, packet); }
    private Vector3d vector() { return new Vector3d(x, y, z); }
    private org.bukkit.Location speechLocation() { return new org.bukkit.Location(world, x, y + SPEECH_Y_OFFSET, z); }
    TutorialStage stage() { return stage; }
    void stage(TutorialStage stage) { this.stage = stage; }
    boolean ended() { return ended; }
    Player target() { return target; }
    private static void requireMain() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Tutorial NPC changes must run on the server thread"); }
}
