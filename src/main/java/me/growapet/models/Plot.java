package me.growapet.models;

import java.util.UUID;

/**
 * One player's plot allocation. There is no physical location or size here — every player's plot
 * is the same single shared WorldGuard region ("plot"), so a plot is just an owner plus their
 * placement caps (pet/egg slots); see PlotManager for the shared location and PlotVisitManager for
 * whose placed pets/eggs a given viewer currently sees.
 */
public class Plot {
    private final UUID owner;
    private final int id;
    private int petLimit = 5;
    private int eggLimit = 3;

    public Plot(UUID owner, int id) {
        this.owner = owner;
        this.id = id;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public int getId() {
        return this.id;
    }

    public int getPetLimit() {
        return this.petLimit;
    }

    public int getEggLimit() {
        return this.eggLimit;
    }

    public void setPetLimit(int petLimit) {
        this.petLimit = petLimit;
    }

    public void setEggLimit(int eggLimit) {
        this.eggLimit = eggLimit;
    }
}
