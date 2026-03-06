package com.trading.bot.strategy;

import com.trading.bot.strategy.model.MarketTick;
import com.trading.bot.strategy.model.Signal;
import com.trading.bot.strategy.model.Signal.SignalType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Moving-Average Crossover Strategy.
 *
 * <p>Generates a {@link SignalType#BUY} signal when the short-term Simple Moving
 * Average (SMA) crosses <em>above</em> the long-term SMA, and a
 * {@link SignalType#SELL} signal on the reverse crossover.
 *
 * <p><b>Reactive mechanics</b>
 * <ul>
 *   <li>{@code buffer(longPeriod, 1)} – slides a window of {@code longPeriod} ticks
 *       one tick at a time, so every incoming tick produces a new window.  This
 *       avoids storing the full price history in memory; only the window contents
 *       are kept.</li>
 *   <li>SMA(short) and SMA(long) are computed from the tail of each window.
 *       Comparing consecutive windows lets us detect a crossover.</li>
 * </ul>
 *
 * <p>Backpressure is delegated to the upstream
 * {@link com.trading.bot.strategy.engine.StrategyEngine}.
 */
@Component
public class MovingAverageCrossoverStrategy implements TradingStrategy {

    private static final String STRATEGY_NAME = "MovingAverageCrossover";

    private final int shortPeriod;
    private final int longPeriod;

    /**
     * Creates a strategy with configurable window sizes.
     *
     * @param shortPeriod number of ticks for the fast/short SMA (e.g. 5)
     * @param longPeriod  number of ticks for the slow/long SMA (e.g. 20)
     */
    public MovingAverageCrossoverStrategy(int shortPeriod, int longPeriod) {
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException(
                    "shortPeriod must be less than longPeriod");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    /** Convenience constructor with default 5/20 windows. */
    public MovingAverageCrossoverStrategy() {
        this(5, 20);
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public Flux<Signal> analyze(Flux<MarketTick> tickStream) {
        /*
         * buffer(longPeriod, 1): emit overlapping windows of size `longPeriod`,
         * advancing by 1 tick each time.  Each emitted List<MarketTick> represents
         * the current rolling window — no unbounded state is kept outside it.
         *
         * scan: carry the previous SMA pair so we can detect a crossover by
         * comparing the current values against the prior ones.
         */
        return tickStream
                .buffer(longPeriod, 1)                          // sliding window
                .filter(window -> window.size() == longPeriod)  // wait for full window
                .map(this::computeSmaPair)                       // (shortSma, longSma)
                .scan(new SmaPair(0, 0, null, null),             // seed with a neutral pair
                        (prev, curr) -> curr.withPrev(prev))     // carry previous pair
                .skip(1)                                         // drop the seed
                .filter(pair -> pair.prev() != null)             // need two pairs to compare
                .flatMap(this::toCrossoverSignal);               // emit signal if crossover
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Compute both SMAs for a full window and remember the last tick for context. */
    private SmaPair computeSmaPair(List<MarketTick> window) {
        double longSma = window.stream()
                .mapToDouble(MarketTick::price)
                .average()
                .orElse(0);

        double shortSma = window.subList(longPeriod - shortPeriod, longPeriod)
                .stream()
                .mapToDouble(MarketTick::price)
                .average()
                .orElse(0);

        MarketTick lastTick = window.get(window.size() - 1);
        return new SmaPair(shortSma, longSma, lastTick, null);
    }

    /**
     * Determine if a golden cross (BUY) or death cross (SELL) occurred and
     * emit the corresponding {@link Signal}, or nothing if no crossover.
     */
    private Flux<Signal> toCrossoverSignal(SmaPair current) {
        SmaPair prev = current.prev();
        if (prev == null) {
            return Flux.empty();
        }

        MarketTick tick = current.lastTick();
        boolean bullishCross = prev.shortSma() <= prev.longSma()
                && current.shortSma() > current.longSma();
        boolean bearishCross = prev.shortSma() >= prev.longSma()
                && current.shortSma() < current.longSma();

        if (bullishCross) {
            return Flux.just(signal(tick, SignalType.BUY,
                    String.format("Golden cross: SMA%d (%.4f) crossed above SMA%d (%.4f)",
                            shortPeriod, current.shortSma(),
                            longPeriod, current.longSma())));
        }
        if (bearishCross) {
            return Flux.just(signal(tick, SignalType.SELL,
                    String.format("Death cross: SMA%d (%.4f) crossed below SMA%d (%.4f)",
                            shortPeriod, current.shortSma(),
                            longPeriod, current.longSma())));
        }
        return Flux.empty();
    }

    private Signal signal(MarketTick tick, SignalType type, String message) {
        return new Signal(
                tick.symbol(),
                tick.timestamp(),   // use event time, not processing time
                type,
                STRATEGY_NAME,
                tick.price(),
                message);
    }

    // ── internal value objects ────────────────────────────────────────────────

    /**
     * Carries the computed SMA pair for one window tick along with the
     * previous pair so crossover detection can be done without external state.
     */
    private record SmaPair(
            double shortSma,
            double longSma,
            MarketTick lastTick,
            SmaPair prev
    ) {
        SmaPair withPrev(SmaPair previous) {
            return new SmaPair(shortSma, longSma, lastTick, previous);
        }
    }
}
