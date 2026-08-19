package me.growapet.models;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("fast")
final class PetProgressionTest {
    @Test
    void multiLevelExperienceRecomputesMultipliers() {
        Pet pet = new Pet(UUID.randomUUID(), UUID.randomUUID(), EntityType.CAT, Pet.Rarity.RARE, 251);
        long amount = Pet.expToLevelUp(1) + Pet.expToLevelUp(2) + 5;

        pet.addExp(amount);

        assertEquals(3, pet.getLevel());
        assertEquals(5, pet.getExp());
        assertEquals(1.02, pet.getDamageMultiplier(), 0.000001);
        assertEquals(1.01, pet.getCoinMultiplier(), 0.000001);
        assertEquals(1.01, pet.getGemMultiplier(), 0.000001);
    }

    @Test
    void invalidStoredValuesAreClampedWithoutNegativeProgression() {
        Pet pet = new Pet(UUID.randomUUID(), UUID.randomUUID(), EntityType.CAT, Pet.Rarity.COMMON, 1);
        pet.addExp(-1);
        pet.setLevel(0);
        pet.setExp(-4);
        pet.setDamageMultiplier(Double.NaN);
        pet.setCoinMultiplier(Double.POSITIVE_INFINITY);

        assertEquals(1, pet.getLevel());
        assertEquals(0, pet.getExp());
        assertEquals(1.0, pet.getDamageMultiplier());
        assertEquals(1.0, pet.getCoinMultiplier());
    }
}
