package me.growapet.trade;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TradeSession {
    final UUID id = UUID.randomUUID();
    final UUID one;
    final UUID two;
    final TradeOffer first = new TradeOffer();
    final TradeOffer second = new TradeOffer();
    final CompletableFuture<Void> settled = new CompletableFuture<>();
    boolean busy;
    boolean committing;
    boolean cancelRequested;
    boolean closed;
    boolean cacheRestored;

    TradeSession(UUID one, UUID two) {
        this.one = one;
        this.two = two;
    }

    TradeOffer offer(UUID player) {
        return one.equals(player) ? first : second;
    }

    TradeOffer other(UUID player) {
        return one.equals(player) ? second : first;
    }

    UUID counterpart(UUID player) {
        return one.equals(player) ? two : one;
    }
}
