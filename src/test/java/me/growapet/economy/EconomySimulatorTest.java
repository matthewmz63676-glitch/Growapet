package me.growapet.economy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("fast")
final class EconomySimulatorTest {
    @Test void fixedSeedProducesTheSameReport() {
        EconomySimulator.Inputs input = new EconomySimulator.Inputs(100, 20, 1, 3, 10, 100, 0, 250, 1000, 50, 7);
        assertEquals(EconomySimulator.simulate(input, 42), EconomySimulator.simulate(input, 42));
    }

    @Test void paidAccelerationAboveCapIsRejected() {
        EconomySimulator.Inputs input = new EconomySimulator.Inputs(100, 20, 1, 3, 10, 100, 0, 250, 1000, 51, 7);
        assertThrows(IllegalArgumentException.class, () -> EconomySimulator.simulate(input, 42));
    }
}
