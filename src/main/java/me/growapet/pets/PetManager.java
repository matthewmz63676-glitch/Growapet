/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.EntityType
 */
package me.growapet.pets;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import me.growapet.GrowAPet;
import me.growapet.models.Pet;
import org.bukkit.entity.EntityType;

public class PetManager {
    private final GrowAPet plugin;
    private final Map<UUID, List<Pet>> ownerPets = new ConcurrentHashMap<UUID, List<Pet>>();

    public PetManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public List<Pet> getPets(UUID owner) {
        return this.ownerPets.computeIfAbsent(owner, k -> new CopyOnWriteArrayList());
    }

    public Pet hatch(UUID owner, EntityType entityType) {
        Pet.Rarity rarity = this.rollRarity();
        int size = this.rollSize(rarity);
        Pet pet = new Pet(UUID.randomUUID(), owner, entityType, rarity, size);
        this.getPets(owner).add(pet);
        this.save(pet);
        return pet;
    }

    private Pet.Rarity rollRarity() {
        double roll = Math.random() * 100.0;
        double cumulative = 0.0;
        double[] weights = new double[]{45.0, 25.0, 15.0, 8.0, 4.0, 2.0, 0.7, 0.25, 0.05};
        Pet.Rarity[] rarities = Pet.Rarity.values();
        for (int i = 0; i < rarities.length; ++i) {
            if (!(roll <= (cumulative += weights[i]))) continue;
            return rarities[i];
        }
        return Pet.Rarity.COMMON;
    }

    public int rollSize(Pet.Rarity rarity) {
        double exponent = Math.max(2.5, 5.5 - (double)rarity.ordinal() * 0.3);
        double roll = Math.pow(Math.random(), exponent);
        int size = 1 + (int)(999.0 * roll);
        return Math.max(1, Math.min(1000, size));
    }

    public void save(Pet pet) {
        this.plugin.getDatabase().async(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO pets (uuid, owner, entity_type, display_name, rarity, size_int,\nlevel, exp, damage_multiplier, coin_multiplier, gem_multiplier, skin, equipped)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n");){
                ps.setString(1, pet.getUuid().toString());
                ps.setString(2, pet.getOwner().toString());
                ps.setString(3, pet.getEntityType().name());
                ps.setString(4, pet.getDisplayName());
                ps.setString(5, pet.getRarity().name());
                ps.setInt(6, pet.getSize());
                ps.setInt(7, pet.getLevel());
                ps.setLong(8, pet.getExp());
                ps.setDouble(9, pet.getDamageMultiplier());
                ps.setDouble(10, pet.getCoinMultiplier());
                ps.setDouble(11, pet.getGemMultiplier());
                ps.setString(12, pet.getSkin());
                ps.setInt(13, pet.isEquipped() ? 1 : 0);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void loadAll() {
        this.plugin.getDatabase().async(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM pets");
                 ResultSet rs = ps.executeQuery();){
                while (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    Pet pet = new Pet(UUID.fromString(rs.getString("uuid")), owner, EntityType.valueOf((String)rs.getString("entity_type")), Pet.Rarity.valueOf(rs.getString("rarity")), Math.max(1, rs.getInt("size_int")));
                    pet.setDisplayName(rs.getString("display_name"));
                    pet.setLevel(rs.getInt("level"));
                    pet.setExp(rs.getLong("exp"));
                    pet.setDamageMultiplier(rs.getDouble("damage_multiplier"));
                    pet.setCoinMultiplier(rs.getDouble("coin_multiplier"));
                    pet.setGemMultiplier(rs.getDouble("gem_multiplier"));
                    pet.setSkin(rs.getString("skin"));
                    pet.setEquipped(rs.getInt("equipped") == 1);
                    this.getPets(owner).add(pet);
                }
            }
            return null;
        });
    }
}

