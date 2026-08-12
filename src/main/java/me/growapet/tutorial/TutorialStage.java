package me.growapet.tutorial;

import java.util.Locale;

public enum TutorialStage {
    MOB(TutorialAction.MOB_KILL),
    SHOP(TutorialAction.SHOP_PURCHASE),
    EGG(TutorialAction.EGG_HATCH),
    COMPLETE(null);

    private final TutorialAction expected;

    TutorialStage(TutorialAction expected) { this.expected = expected; }
    public TutorialAction expected() { return expected; }

    public TutorialStage next() {
        return switch (this) { case MOB -> SHOP; case SHOP -> EGG; case EGG, COMPLETE -> COMPLETE; };
    }

    public static TutorialStage parse(String value) {
        try { return valueOf(value == null ? "MOB" : value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return MOB; }
    }
}
