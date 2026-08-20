package me.growapet.tags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class TagValidationTest {
    @Test void customTagBoundaryRejectsUnsafeMarkup() {
        assertTrue(TagService.safeId("pet_lover"));
        assertFalse(TagService.safeId("<click>"));
        assertTrue(TagService.safeMarkup("<gold>[Pet]</gold>"));
        assertFalse(TagService.safeMarkup("<click:run_command:/op @s>bad</click>"));
    }
}
