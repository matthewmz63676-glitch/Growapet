package me.growapet.commerce;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommerceVerifierTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    void verifiesCompleteTebexPackageShape() {
        String body = "{\"txn_id\":\"txn-1\",\"player\":{\"uuid\":\"" + PLAYER + "\"}," +
                "\"packages\":[{\"id\":42,\"quantity\":2}],\"status\":{\"id\":1,\"description\":\"Complete\"}}";
        assertTrue(CommerceVerifier.parse(body, "txn-1", PLAYER, "42", 2).valid());
    }

    @Test
    void rejectsWrongPlayerOrQuantity() {
        String body = "{\"player\":{\"uuid\":\"00000000-0000-0000-0000-000000000001\"},\"packages\":[{\"id\":42,\"quantity\":1}],\"status\":{\"id\":1,\"description\":\"Complete\"}}";
        assertFalse(CommerceVerifier.parse(body, "txn-1", PLAYER, "42", 2).valid());
    }

    @Test
    void acceptsOnlyProviderRefundStatuses() {
        assertTrue(CommerceVerifier.parseStatus("{\"status\":{\"id\":4,\"description\":\"Refunded\"}}", "txn").valid());
        assertFalse(CommerceVerifier.parseStatus("{\"status\":{\"id\":1,\"description\":\"Complete\"}}", "txn").valid());
    }
}
