package me.growapet.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("fast")
@Tag("regression")
final class GrowAPetTabCompleterTest {
    @Test void filtersCaseInsensitivelyAndNeverReturnsPlayerPlaceholder(){
        List<String> result=GrowAPetTabCompleter.filter(List.of("Alice","alex","Bob","%PLAYER%"),"al");
        assertEquals(List.of("alex","Alice"),result);
        assertFalse(result.stream().anyMatch("%PLAYER%"::equalsIgnoreCase));
    }
}
