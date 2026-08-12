package me.growapet.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlotGridTest {
    @Test void firstPlotStartsAtOriginAndRowsDoNotShift() {
        assertArrayEquals(new int[]{0, 0}, PlotManager.gridCoordinates(1, 50, 96));
        assertArrayEquals(new int[]{49 * 96, 0}, PlotManager.gridCoordinates(50, 50, 96));
        assertArrayEquals(new int[]{0, 96}, PlotManager.gridCoordinates(51, 50, 96));
    }

    @Test void rejectsInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> PlotManager.gridCoordinates(0, 50, 96));
    }
}
