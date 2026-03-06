package com.trading.bot.strategy;

import com.trading.bot.strategy.model.MarketTick;
import com.trading.bot.strategy.model.Signal;
import com.trading.bot.strategy.model.Signal.SignalType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Volume Spike Strategy.
 *
 * <p>Emits a {@link SignalType#BUY} signal when the current tick's volume
 * exceeds the rolling average volume by a configurable multiplier (default ×3).
 * A sudden spike in volume often precedes a significant price move.
 *
 * <p><b>Reactive mechanics</b>
 * <ul>
 *   <li>{@code buffer(windowSize, 1)} – overlapping sliding window used to compute
 *       average volume without accumulating unbounded history in memory.</li>
 *   <li>The <em>last</em> tick in the window is the candidate; the <em>rest</em>
 *       form the baseline average.</li>
 * </ul>
 *
 * <p>Backpressure is handled by the upstream
 * {@link com.trading.bot.strategy.engine.StrategyEngine}.
 */
@Component
public class VolumeSpikeStrategy implements TradingStrategy {

    private static final String STRATEGY_NAME = "VolumeSpike";

    private final int windowSize;
    private final double spikeMultiplier;

    /**
     * @param windowSize      number of ticks used to compute the average volume baseline
     * @param spikeMultiplier volume must exceed avg × multiplier to trigger a signal
     */
    public VolumeSpikeStrategy(int windowSize, double spikeMultiplier) {
        if (windowSize < 2) {
            throw new IllegalArgumentException("windowSize must be at least 2");
        }
        if (spikeMultiplier <= 1.0) {
            throw new IllegalArgumentException("spikeMultiplier must be greater than 1.0");
        }
        this.windowSize = windowSize;
        this.spikeMultiplier = spikeMultiplier;
    }

    /** Convenience constructor: 20-tick window, 3× multiplier. */
    public VolumeSpikeStrategy() {
        this(20, 3.0);
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public Flux<Signal> analyze(Flux<MarketTick> tickStream) {
        /*
         * buffer(windowSize, 1): sliding window of `windowSize` ticks.
         * For each full window:
         *   - baseline avg = mean of ticks[0..windowSize-2]
         *   - candidate    = ticks[windowSize-1]  (latest tick)
         * If candidate.volume > baseline * spikeMultiplier → BUY signal.
         */
        return tickStream
                .buffer(windowSize, 1)                          // sliding window
                .filter(window -> window.size() == windowSize)  // only full windows
                .flatMap(this::detectSpike);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Flux<Signal> detectSpike(List<MarketTick> window) {
        MarketTick candidate = window.get(window.size() - 1);

        double baselineAvg = window.subList(0, window.size() - 1)
                .stream()
                .mapToDouble(MarketTick::volume)
                .average()
                .orElse(0);

        if (baselineAvg > 0 && candidate.volume() > baselineAvg * spikeMultiplier) {
            String message = String.format(
                    "Volume spike detected: %.2f vs avg %.2f (×%.1f)",
                    candidate.volume(), baselineAvg, candidate.volume() / baselineAvg);

            return Flux.just(new Signal(
                    candidate.symbol(),
                    candidate.timestamp(),  // use event time, not processing time
                    SignalType.BUY,
                    STRATEGY_NAME,
                    candidate.price(),
                    message));
        }
        return Flux.empty();
    }
}
