package me.growapet.trade;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class TradeOffer {
    long coins;
    long gems;
    final Set<UUID> pets = new LinkedHashSet<>();
    String eggInstanceId;
    boolean confirmed;
}
