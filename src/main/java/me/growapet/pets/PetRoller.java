package me.growapet.pets;

import me.growapet.models.Pet;

import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Deterministic pet-roll policy; the manager supplies configuration and event state. */
public final class PetRoller {
    private PetRoller() {}

    public static Pet.Rarity rarity(RandomGenerator random, Map<Pet.Rarity, Double> weights, boolean luckyEgg) {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(weights, "weights");
        double total = 0;
        for (Pet.Rarity rarity : Pet.Rarity.values()) total += weight(weights, rarity);
        if (total <= 0) return Pet.Rarity.COMMON;

        double roll = random.nextDouble(total);
        for (Pet.Rarity rarity : Pet.Rarity.values()) {
            roll -= weight(weights, rarity);
            if (roll <= 0) {
                return luckyEgg
                        ? Pet.Rarity.values()[Math.min(Pet.Rarity.values().length - 1, rarity.ordinal() + 1)]
                        : rarity;
            }
        }
        return Pet.Rarity.COMMON;
    }

    public static int size(RandomGenerator random, Pet.Rarity rarity) {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(rarity, "rarity");
        int[] range = switch (rarity) {
            case COMMON -> new int[]{1, 100};
            case UNCOMMON -> new int[]{101, 250};
            case RARE -> new int[]{251, 450};
            case EPIC -> new int[]{451, 650};
            case LEGENDARY -> new int[]{651, 850};
            case MYTHIC -> new int[]{851, 930};
            case DIVINE -> new int[]{931, 970};
            case SECRET -> new int[]{971, 995};
            case EXCLUSIVE -> new int[]{996, 1000};
        };
        return random.nextInt(range[0], range[1] + 1);
    }

    private static double weight(Map<Pet.Rarity, Double> weights, Pet.Rarity rarity) {
        Double value = weights.get(rarity);
        return value != null && Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
