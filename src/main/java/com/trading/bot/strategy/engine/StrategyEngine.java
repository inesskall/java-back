package com.trading.bot.strategy.engine;

import com.trading.bot.strategy.TradingStrategy;
import com.trading.bot.strategy.model.MarketTick;
import com.trading.bot.strategy.model.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

/**
 * Reactive Trading Strategy Engine.
 *
 * <p>The engine acts as a <em>hot-stream fan-out</em>: it accepts the single upstream
 * {@code Flux<MarketTick>} (e.g. from a WebSocket feed), broadcasts every tick to
 * <strong>all</strong> registered {@link TradingStrategy} implementations, and merges
 * their output signals into one unified {@code Flux<Signal>} for downstream consumers.
 *
 * <h2>Backpressure strategy</h2>
 * The engine uses {@link Flux#onBackpressureDrop} before fan-out.  If any strategy
 * pipeline is slower than the incoming tick rate, surplus ticks are silently discarded
 * rather than building up an unbounded buffer or throwing an exception.  Change this
 * to {@code onBackpressureBuffer(N)} if losing ticks is unacceptable for your use-case.
 *
 * <h2>Fan-out via {@link Sinks.Many}</h2>
 * A {@link Sinks.Many#multicast()} sink with
 * {@link Sinks.MulticastSpec#onBackpressureBuffer()} re-publishes ticks to every
 * strategy subscriber independently, so each strategy gets a full copy of the stream.
 */
@Component
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);

    /** Maximum ticks buffered per-subscriber in the multicast sink. */
    private static final int FANOUT_BUFFER_SIZE = 256;

    private final List<TradingStrategy> strategies;

    /**
     * Spring will inject <em>all</em> beans implementing {@link TradingStrategy}
     * via constructor injection.
     *
     * @param strategies list of available strategy implementations
     */
    public StrategyEngine(List<TradingStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * Wires the incoming tick stream into all strategies and returns a merged
     * signal stream.
     *
     * <p>Typical usage:
     * <pre>{@code
     *   Flux<MarketTick> hotTicks = webSocketSource.share();
     *   Flux<Signal>     signals  = engine.process(hotTicks);
     *   signals.subscribe(signal -> messageBroker.publish(signal));
     * }</pre>
     *
     * @param tickStream upstream source of market ticks (hot or cold)
     * @return merged {@code Flux<Signal>} from all strategies; never null
     */
    public Flux<Signal> process(Flux<MarketTick> tickStream) {
        /*
         * 1. onBackpressureDrop: if downstream strategies cannot keep up,
         *    we drop excess ticks rather than crashing or blocking.
         *
         * 2. Sinks.many().multicast().onBackpressureBuffer(FANOUT_BUFFER_SIZE):
         *    A warm multicast sink.  Each strategy subscribes independently via
         *    sink.asFlux(), getting its own backpressure-buffered slice of the stream.
         *
         * 3. Flux.merge: combines all per-strategy signal Fluxes into one.
         *    merge() is used (not concat/zip) because strategies emit independently
         *    and we want interleaved, low-latency delivery.
         */
        Sinks.Many<MarketTick> fanoutSink = Sinks.many()
                .multicast()
                .onBackpressureBuffer(FANOUT_BUFFER_SIZE);

        // Feed the upstream (with backpressure protection) into the sink
        tickStream
                .onBackpressureDrop(dropped ->
                        log.warn("[StrategyEngine] tick dropped due to backpressure: {}", dropped))
                .subscribe(
                        tick -> fanoutSink.tryEmitNext(tick),
                        fanoutSink::tryEmitError,
                        fanoutSink::tryEmitComplete);

        // Let each strategy subscribe to its own view of the shared tick stream
        List<Flux<Signal>> strategySignals = strategies.stream()
                .map(strategy -> strategy.analyze(fanoutSink.asFlux()))
                .toList();

        return Flux.merge(strategySignals);
    }
}
