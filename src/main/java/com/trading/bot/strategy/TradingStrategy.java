package com.trading.bot.strategy;

import com.trading.bot.strategy.model.MarketTick;
import com.trading.bot.strategy.model.Signal;
import reactor.core.publisher.Flux;

/**
 * Core Strategy Pattern contract for the reactive Trading Strategy Engine.
 *
 * <p>Each implementation receives the same hot {@code Flux<MarketTick>} stream and
 * independently emits {@code Signal} items whenever its trading logic triggers.
 * The reactive pipeline ensures the method is entirely non-blocking.
 *
 * <p>Implementations <strong>must not</strong> perform any blocking I/O inside the
 * reactive chain; use {@code subscribeOn(Schedulers.boundedElastic())} if external
 * I/O is unavoidable.
 */
public interface TradingStrategy {

    /**
     * Analyzes an incoming stream of market ticks and emits trading signals.
     *
     * @param tickStream hot or cold {@code Flux} of {@link MarketTick} items
     * @return {@code Flux<Signal>} – may be empty if no conditions are met
     */
    Flux<Signal> analyze(Flux<MarketTick> tickStream);

    /**
     * Human-readable name used for logging and signal attribution.
     */
    String name();
}
