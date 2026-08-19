package me.growapet.pets;

import me.growapet.GrowAPet;
import me.growapet.display.VirtualPetService;
import me.growapet.models.Pet;
import me.growapet.models.PlayerData;
import me.growapet.models.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;

public final class PetManager {
    private final GrowAPet plugin;
    private final Map<UUID, List<Pet>> ownerPets = new ConcurrentHashMap<>();
    private final NamespacedKey petIdKey;
    private final VirtualPetService virtualPets;
    private final RandomGenerator random;

    public PetManager(GrowAPet plugin) { this(plugin, createRuntimeRandom()); }

    static RandomGenerator createRuntimeRandom() { return new SplittableRandom(); }

    PetManager(GrowAPet plugin, RandomGenerator random) {
        this.plugin = plugin;
        this.random = java.util.Objects.requireNonNull(random, "random");
        this.petIdKey = new NamespacedKey(plugin, "pet_uuid");
        this.virtualPets = new VirtualPetService(plugin);
    }

    public List<Pet> getPets(UUID owner) { return ownerPets.computeIfAbsent(owner, ignored -> new CopyOnWriteArrayList<>()); }
    public Pet getPet(UUID owner, UUID petId) { return getPets(owner).stream().filter(p -> p.getUuid().equals(petId)).findFirst().orElse(null); }
    /** Recognizes and protects legacy physical pet entities during their cleanup window. */
    public boolean isPet(Entity entity) { return entity.getPersistentDataContainer().has(petIdKey, PersistentDataType.STRING); }

    public CompletableFuture<Pet> hatch(UUID owner, EntityType entityType) {
        Pet pet;
        try { pet = createHatchCandidate(owner, entityType); }
        catch (IllegalArgumentException error) { return CompletableFuture.failedFuture(error); }
        return save(pet).thenCompose(ignored -> onMain(() -> {
            registerHatched(pet);
            return pet;
        }));
    }

    public Pet createHatchCandidate(UUID owner, EntityType entityType) {
        requireMain();
        if (!isSupported(entityType)) throw new IllegalArgumentException("Unsupported pet entity " + entityType);
        Pet.Rarity rarity = rollRarity();
        return new Pet(UUID.randomUUID(), owner, entityType, rarity, rollSize(rarity));
    }

    public void registerHatched(Pet pet) { requireMain(); if (getPet(pet.getOwner(), pet.getUuid()) == null) getPets(pet.getOwner()).add(pet); }

    private Pet.Rarity rollRarity() {
        Map<Pet.Rarity, Double> weights = new java.util.EnumMap<>(Pet.Rarity.class);
        for (Pet.Rarity rarity : Pet.Rarity.values()) {
            weights.put(rarity, plugin.getConfigManager().pets().getDouble("rarities." + rarity.name() + ".weight", 0));
        }
        return PetRoller.rarity(random, weights, plugin.getEventManager().isActive(me.growapet.events.EventType.LUCKY_EGG));
    }

    private int rollSize(Pet.Rarity rarity) {
        return PetRoller.size(random, rarity);
    }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            List<PetRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM pets"); ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(PetRow.from(result));
            }
            return rows;
        }).thenCompose(rows -> onMain(() -> {
            ownerPets.clear();
            for (PetRow row : rows) {
                try { getPets(row.owner()).add(row.toPet()); }
                catch (IllegalArgumentException error) { plugin.getLogger().warning("Skipping invalid pet " + row.uuid() + ": " + error.getMessage()); }
            }
        }));
    }

    public boolean equip(UUID owner, UUID petId) {
        requireMain();
        Pet pet = getPet(owner, petId);
        Plot plot = plugin.getPlotManager().getPlot(owner);
        if (pet == null || plot == null || pet.isEquipped() || plugin.getTradeManager().isPetLocked(petId)) return false;
        long equipped = getPets(owner).stream().filter(Pet::isEquipped).count();
        int configured = Math.max(1, plugin.getConfigManager().config().getInt("pets.max-equipped", 3));
        if (equipped >= Math.min(configured, plot.getPetLimit())) return false;
        pet.setEquipped(true);
        showVirtualPet(pet, plot, (int) equipped);
        recalculateOwner(owner); save(pet); return true;
    }

    /** Re-syncs which of this owner's placed pets a specific viewer sees right now (e.g. after /plot visit). */
    public void refreshViewer(org.bukkit.entity.Player viewer) { virtualPets.refreshViewer(viewer); }

    public boolean unequip(UUID owner, UUID petId) {
        requireMain();
        Pet pet = getPet(owner, petId);
        if (pet == null || !pet.isEquipped() || plugin.getTradeManager().isPetLocked(petId)) return false;
        virtualPets.remove(pet.getUuid()); pet.setEquipped(false); pet.setPlaced(false); pet.setEntityUuid(null); recalculateOwner(owner); save(pet); return true;
    }

    public boolean transfer(UUID from, UUID to, UUID petId) {
        requireMain(); Pet pet=getPet(from,petId); if(pet==null||plugin.getTradeManager().isPetLocked(petId))return false;
        if(pet.isEquipped())unequip(from,petId); getPets(from).remove(pet); pet.setOwner(to); getPets(to).add(pet); save(pet); recalculateOwner(from); recalculateOwner(to); return true;
    }

    public void restoreEquipped(UUID owner) {
        requireMain();
        Plot plot = plugin.getPlotManager().getPlot(owner);
        if (plot == null) return;
        int index = 0;
        cleanupLegacyEntities(plot);
        for (Pet pet : getPets(owner)) {
            if (!pet.isEquipped()) continue;
            showVirtualPet(pet, plot, index++);
        }
        recalculateOwner(owner);
    }

    private void cleanupLegacyEntities(Plot plot) {
        Location center = plugin.getPlotManager().plotCenter();
        if (center == null || center.getWorld() == null) return;
        double radius = 32;
        for (Entity entity : new ArrayList<>(center.getWorld().getNearbyEntities(
                center, radius, 32, radius, this::isPet))) entity.remove();
    }

    private void showVirtualPet(Pet pet, Plot plot, int index) {
        Location center = plugin.getPlotManager().plotCenter();
        Location anchor = center == null
                ? plugin.getPlotManager().homeLocation()
                : center.clone().add((index % 3) * 4 - 4, 0, 3 + (index / 3.0) * 4);
        virtualPets.show(pet, anchor);
        pet.setEntityUuid(null);
        pet.setLocation(anchor.getWorld().getName(), anchor.getX(), anchor.getY(), anchor.getZ());
        pet.setPlaced(true);
        save(pet);
    }

    public void grantKillExperience(UUID owner, long experience) {
        requireMain();
        PlayerData data = plugin.getPlayerManager().get(owner);
        int highest = data == null ? 0 : data.getHighestPetLevel();
        for (Pet pet : getPets(owner)) {
            if (!pet.isEquipped()) continue;
            long adjusted=plugin.getEventManager().isActive(me.growapet.events.EventType.PET_FRENZY)&&experience<=Long.MAX_VALUE/2?experience*2:experience;
            pet.addExp(adjusted); highest = Math.max(highest, pet.getLevel()); save(pet);
            virtualPets.update(pet);
        }
        if (data != null && highest > data.getHighestPetLevel()) data.setHighestPetLevel(highest);
        recalculateOwner(owner);
    }

    public void recalculateOwner(UUID owner) {
        PlayerData data = plugin.getPlayerManager().get(owner); if (data == null) return;
        List<Pet> equipped = getPets(owner).stream().filter(Pet::isEquipped).toList();
        data.setPetPower(equipped.stream().mapToDouble(p -> p.getLevel() * p.getDamageMultiplier()).sum());
    }

    public double damageMultiplier(UUID owner) { return getPets(owner).stream().filter(Pet::isEquipped).mapToDouble(Pet::getDamageMultiplier).reduce(1.0, (a,b) -> a*b); }
    public double coinMultiplier(UUID owner) { return getPets(owner).stream().filter(Pet::isEquipped).mapToDouble(Pet::getCoinMultiplier).reduce(1.0, (a,b) -> a*b); }
    public double gemMultiplier(UUID owner) { return getPets(owner).stream().filter(Pet::isEquipped).mapToDouble(Pet::getGemMultiplier).reduce(1.0, (a,b) -> a*b); }

    public CompletableFuture<Void> save(Pet pet) {
        PetRow row = PetRow.capture(pet);
        CompletableFuture<Void> future=plugin.getDatabase().async(connection -> { insert(connection,row); return null; });
        future.exceptionally(error->{plugin.getLogger().severe("Failed to persist pet "+row.uuid()+": "+error.getMessage());return null;});
        return future;
    }

    public void insert(java.sql.Connection connection, Pet pet) throws Exception { insert(connection, PetRow.capture(pet)); }
    private static void insert(java.sql.Connection connection, PetRow row) throws Exception {String sql="INSERT INTO pets(uuid,owner,entity_type,display_name,rarity,size_int,level,exp,damage_multiplier,coin_multiplier,gem_multiplier,skin,equipped,entity_uuid,world,x,y,z) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET owner=excluded.owner,entity_type=excluded.entity_type,display_name=excluded.display_name,rarity=excluded.rarity,size_int=excluded.size_int,level=excluded.level,exp=excluded.exp,damage_multiplier=excluded.damage_multiplier,coin_multiplier=excluded.coin_multiplier,gem_multiplier=excluded.gem_multiplier,skin=excluded.skin,equipped=excluded.equipped,entity_uuid=excluded.entity_uuid,world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z";try(PreparedStatement s=connection.prepareStatement(sql)){int i=1;s.setString(i++,row.uuid().toString());s.setString(i++,row.owner().toString());s.setString(i++,row.type());s.setString(i++,row.name());s.setString(i++,row.rarity());s.setInt(i++,row.size());s.setInt(i++,row.level());s.setLong(i++,row.exp());s.setDouble(i++,row.damage());s.setDouble(i++,row.coins());s.setDouble(i++,row.gems());s.setString(i++,row.skin());s.setInt(i++,row.equipped()?1:0);s.setString(i++,row.entityUuid());s.setString(i++,row.world());s.setDouble(i++,row.x());s.setDouble(i++,row.y());s.setDouble(i,row.z());s.executeUpdate();}}

    public void start() { virtualPets.start(); }
    public void stop() { requireMain(); virtualPets.stop(); }
    private static boolean isSupported(EntityType type) { return type != null && type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER && type != EntityType.ARMOR_STAND; }
    private static void requireMain() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Pet operation off server thread"); }
    private <T> CompletableFuture<T> onMain(java.util.concurrent.Callable<T> action) { CompletableFuture<T> f=new CompletableFuture<>(); Bukkit.getScheduler().runTask(plugin,()->{try{f.complete(action.call());}catch(Throwable t){f.completeExceptionally(t);}});return f; }
    private CompletableFuture<Void> onMain(Runnable action) { return onMain(() -> { action.run(); return null; }); }

    private record PetRow(UUID uuid,UUID owner,String type,String name,String rarity,int size,int level,long exp,double damage,double coins,double gems,String skin,boolean equipped,String entityUuid,String world,double x,double y,double z) {
        static PetRow from(ResultSet r)throws Exception{return new PetRow(UUID.fromString(r.getString("uuid")),UUID.fromString(r.getString("owner")),r.getString("entity_type"),r.getString("display_name"),r.getString("rarity"),Math.max(1,r.getInt("size_int")),r.getInt("level"),r.getLong("exp"),r.getDouble("damage_multiplier"),r.getDouble("coin_multiplier"),r.getDouble("gem_multiplier"),r.getString("skin"),r.getInt("equipped")==1,r.getString("entity_uuid"),r.getString("world"),r.getDouble("x"),r.getDouble("y"),r.getDouble("z"));}
        static PetRow capture(Pet p){return new PetRow(p.getUuid(),p.getOwner(),p.getEntityType().name(),p.getDisplayName(),p.getRarity().name(),p.getSize(),p.getLevel(),p.getExp(),p.getDamageMultiplier(),p.getCoinMultiplier(),p.getGemMultiplier(),p.getSkin(),p.isEquipped(),p.getEntityUuid()==null?null:p.getEntityUuid().toString(),p.getWorld(),p.getX(),p.getY(),p.getZ());}
        Pet toPet(){Pet p=new Pet(uuid,owner,EntityType.valueOf(type),Pet.Rarity.valueOf(rarity),size);p.setDisplayName(name);p.setLevel(level);p.setExp(exp);p.setDamageMultiplier(damage);p.setCoinMultiplier(coins);p.setGemMultiplier(gems);p.setSkin(skin);p.setEquipped(equipped);if(entityUuid!=null&&!entityUuid.isBlank())p.setEntityUuid(UUID.fromString(entityUuid));p.setLocation(world,x,y,z);return p;}
    }
}
