package me.growapet.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HudComponentBuilderTest {
    @Test void spritesRequireBothConfigurationAndAConfirmedClientPack() {
        assertTrue(HudComponentBuilder.useSprites(true, true));
        assertFalse(HudComponentBuilder.useSprites(true, false));
        assertFalse(HudComponentBuilder.useSprites(false, true));
    }
}
