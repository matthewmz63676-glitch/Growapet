package me.growapet.leaderboards;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

final class MoneySpentManagerTest {
    @Test void centsRejectFractionsBeyondCurrencyPrecision() {
        assertEquals(12345, MoneySpentManager.toCents(new BigDecimal("123.45")));
        assertThrows(IllegalArgumentException.class, () -> MoneySpentManager.toCents(new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class, () -> MoneySpentManager.toCents(new BigDecimal("0")));
        assertEquals("$123.45", MoneySpentManager.format(12345));
    }
}
