package com.trading.bot.strategy.model;

import java.time.Instant;

/**
 * Trading signal produced by a {@link com.trading.bot.strategy.TradingStrategy}.
 * Downstream consumers (message broker, DB persistence layer, etc.) receive a
 * merged {@code Flux<Signal>} from the {@link com.trading.bot.strategy.engine.StrategyEngine}.
 *
 * @param symbol       Ticker symbol the signal applies to
 * @param timestamp    Time the signal was generated
 * @param type         Signal classification (BUY / SELL / HOLD)
 * @param strategyName Human-readable name of the originating strategy
 * @param price        Reference price at signal generation time
 * @param message      Optional description of the signal rationale
 */
public record Signal(
        String symbol,
        Instant timestamp,
        SignalType type,
        String strategyName,
        double price,
        String message
) {

    /** Supported signal directions. */
    public enum SignalType {
        BUY, SELL, HOLD
    }
}
