package me.growapet.tutorial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("fast")
final class TutorialStageTest {
    @Test void routeAdvancesOnlyInDependencyOrder() {
        assertEquals(TutorialAction.MOB_KILL, TutorialStage.MOB.expected());
        assertEquals(TutorialStage.SHOP, TutorialStage.MOB.next());
        assertEquals(TutorialAction.SHOP_PURCHASE, TutorialStage.SHOP.expected());
        assertEquals(TutorialStage.EGG, TutorialStage.SHOP.next());
        assertEquals(TutorialAction.EGG_HATCH, TutorialStage.EGG.expected());
        assertEquals(TutorialStage.COMPLETE, TutorialStage.EGG.next());
        assertEquals(TutorialStage.COMPLETE, TutorialStage.COMPLETE.next());
    }
}
