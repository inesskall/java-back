package com.trading.bot.strategy;

import com.trading.bot.strategy.model.MarketTick;
import com.trading.bot.strategy.model.Signal;
import com.trading.bot.strategy.model.Signal.SignalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link MovingAverageCrossoverStrategy}.
 *
 * <p>Uses Project Reactor's {@link StepVerifier} to drive and assert the
 * reactive pipeline without spinning up the full Spring context.
 */
class MovingAverageCrossoverStrategyTest {

    /** Strategy under test: short=3, long=5 windows for compact test data. */
    private final MovingAverageCrossoverStrategy strategy =
            new MovingAverageCrossoverStrategy(3, 5);

    // ── BUY (golden cross) ───────────────────────────────────────────────────

    @Test
    @DisplayName("Emits BUY signal when short SMA crosses above long SMA (golden cross)")
    void shouldEmitBuySignalOnGoldenCross() {
        /*
         * Construct a price series where the first windows have shortSMA ≤ longSMA
         * and then the price rises so that shortSMA > longSMA.
         *
         * Windows of size 5, step 1 (requires ≥5 ticks before any window is full):
         *
         *   Tick prices:  10, 10, 10, 10, 10,  <- flat baseline (window 1: all 10)
         *                 10, 10, 10, 30, 30    <- last 3 values are 10,30,30
         *
         * Window 1 (indices 0-4): prices [10,10,10,10,10]
         *   longSMA  = 10.0
         *   shortSMA = avg(10,10,10) = 10.0   → no cross (equal)
         *
         * Window 2 (indices 1-5): prices [10,10,10,10,10]  same → no cross
         * Window 3 (indices 2-6): prices [10,10,10,10,10]  same → no cross
         * Window 4 (indices 3-7): prices [10,10,10,10,10]  same → no cross
         * Window 5 (indices 4-8): prices [10,10,10,10,30]
         *   longSMA  = (10+10+10+10+30)/5 = 14.0
         *   shortSMA = avg(10,10,30)      = 16.67  → shortSMA > longSMA NOW
         *   prev shortSMA (10) <= prev longSMA (10) → GOLDEN CROSS → BUY
         */
        List<Double> prices = List.of(10.0, 10.0, 10.0, 10.0, 10.0,
                                      10.0, 10.0, 10.0, 10.0, 30.0);
        Flux<MarketTick> ticks = toTickFlux("BTCUSDT", prices);

        Flux<Signal> signals = strategy.analyze(ticks);

        StepVerifier.create(signals)
                .assertNext(signal -> {
                    assert signal.type() == SignalType.BUY :
                            "Expected BUY but got " + signal.type();
                    assert "BTCUSDT".equals(signal.symbol()) :
                            "Expected BTCUSDT but got " + signal.symbol();
                    assert "MovingAverageCrossover".equals(signal.strategyName()) :
                            "Unexpected strategy name: " + signal.strategyName();
                })
                .verifyComplete();
    }

    // ── SELL (death cross) ───────────────────────────────────────────────────

    @Test
    @DisplayName("Emits SELL signal when short SMA crosses below long SMA (death cross)")
    void shouldEmitSellSignalOnDeathCross() {
        /*
         * Mirror of the BUY test: start with short > long, then drop price so
         * short SMA falls below long SMA.
         *
         * Tick prices: 30,30,30,30,30, 30,30,30,10,10
         *
         * Window 1 (0-4): [30,30,30,30,30]
         *   longSMA=30, shortSMA=30 → no cross (equal)
         *
         * Window 5 (4-8): [30,30,30,30,10]
         *   longSMA=(30+30+30+30+10)/5=26, shortSMA=avg(30,30,10)=23.33
         *   prev shortSMA(30) >= prev longSMA(30) → DEATH CROSS → SELL
         */
        List<Double> prices = List.of(30.0, 30.0, 30.0, 30.0, 30.0,
                                      30.0, 30.0, 30.0, 30.0, 10.0);
        Flux<MarketTick> ticks = toTickFlux("ETHUSDT", prices);

        Flux<Signal> signals = strategy.analyze(ticks);

        StepVerifier.create(signals)
                .assertNext(signal -> {
                    assert signal.type() == SignalType.SELL :
                            "Expected SELL but got " + signal.type();
                    assert "ETHUSDT".equals(signal.symbol());
                })
                .verifyComplete();
    }

    // ── No signal ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Emits no signal for a flat price series (no crossover)")
    void shouldEmitNoSignalForFlatPrices() {
        List<Double> prices = List.of(10.0, 10.0, 10.0, 10.0, 10.0,
                                      10.0, 10.0, 10.0, 10.0, 10.0);
        Flux<MarketTick> ticks = toTickFlux("BTCUSDT", prices);

        StepVerifier.create(strategy.analyze(ticks))
                .verifyComplete();
    }

    // ── Too few ticks ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Emits no signal when tick count is less than longPeriod")
    void shouldEmitNoSignalWhenNotEnoughTicks() {
        // Only 4 ticks; long period is 5 → no full window ever formed
        List<Double> prices = List.of(10.0, 20.0, 30.0, 40.0);
        Flux<MarketTick> ticks = toTickFlux("BTCUSDT", prices);

        StepVerifier.create(strategy.analyze(ticks))
                .verifyComplete();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Build a finite {@code Flux<MarketTick>} from a plain list of close prices. */
    private Flux<MarketTick> toTickFlux(String symbol, List<Double> prices) {
        List<MarketTick> ticks = new ArrayList<>();
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < prices.size(); i++) {
            ticks.add(new MarketTick(symbol, base.plusSeconds(i), prices.get(i), 1_000.0));
        }
        return Flux.fromIterable(ticks);
    }
}
