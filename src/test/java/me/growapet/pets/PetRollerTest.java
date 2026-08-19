package me.growapet.pets;

import me.growapet.models.Pet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fast")
final class PetRollerTest {
    @Test
    void seededRarityRollsAreRepeatableAndHonorLuckyUpgrade() {
        Map<Pet.Rarity, Double> weights = new EnumMap<>(Pet.Rarity.class);
        weights.put(Pet.Rarity.COMMON, 90.0);
        weights.put(Pet.Rarity.RARE, 10.0);

        RandomGenerator first = RandomGeneratorFactory.of("L64X128MixRandom").create(42);
        RandomGenerator second = RandomGeneratorFactory.of("L64X128MixRandom").create(42);
        assertEquals(PetRoller.rarity(first, weights, false), PetRoller.rarity(second, weights, false));
        assertEquals(Pet.Rarity.EPIC, PetRoller.rarity(RandomGeneratorFactory.of("L64X128MixRandom").create(42),
                Map.of(Pet.Rarity.RARE, 1.0), true));
    }

    @Test
    void emptyWeightsFallBackToCommonAndSizesStayInRarityRanges() {
        assertEquals(Pet.Rarity.COMMON, PetRoller.rarity(RandomGenerator.getDefault(), Map.of(), false));
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(7);
        for (Pet.Rarity rarity : Pet.Rarity.values()) {
            int size = PetRoller.size(random, rarity);
            assertTrue(size >= lowerBound(rarity) && size <= upperBound(rarity), rarity + " size out of range");
        }
    }

    @Test
    void runtimePetRandomUsesAJavaBaseGeneratorAvailableInsidePaperPluginClassLoaders() {
        RandomGenerator random = PetManager.createRuntimeRandom();
        assertTrue(random instanceof SplittableRandom);
        assertEquals(Pet.Rarity.COMMON, PetRoller.rarity(random, Map.of(), false));
    }

    @Test
    void sizeTierBoundariesRemainStable() {
        assertEquals("Tiny", Pet.sizeTierName(1));
        assertEquals("Small", Pet.sizeTierName(10));
        assertEquals("Normal", Pet.sizeTierName(25));
        assertEquals("Large", Pet.sizeTierName(60));
        assertEquals("Huge", Pet.sizeTierName(120));
        assertEquals("Massive", Pet.sizeTierName(250));
        assertEquals("Titan", Pet.sizeTierName(400));
        assertEquals("Colossal", Pet.sizeTierName(600));
        assertEquals("Mythical", Pet.sizeTierName(800));
    }

    private static int lowerBound(Pet.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 101;
            case RARE -> 251;
            case EPIC -> 451;
            case LEGENDARY -> 651;
            case MYTHIC -> 851;
            case DIVINE -> 931;
            case SECRET -> 971;
            case EXCLUSIVE -> 996;
        };
    }

    private static int upperBound(Pet.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 100;
            case UNCOMMON -> 250;
            case RARE -> 450;
            case EPIC -> 650;
            case LEGENDARY -> 850;
            case MYTHIC -> 930;
            case DIVINE -> 970;
            case SECRET -> 995;
            case EXCLUSIVE -> 1000;
        };
    }
}
