package com.trading.bot.strategy.model;

import java.time.Instant;

/**
 * Immutable snapshot of a single market data point received from an exchange.
 * Using a Java Record ensures value-based equality and thread-safety by design.
 *
 * @param symbol    Ticker symbol, e.g. "BTCUSDT"
 * @param timestamp Point in time this tick was emitted
 * @param price     Last traded price
 * @param volume    Trade volume for this tick interval
 */
public record MarketTick(
        String symbol,
        Instant timestamp,
        double price,
        double volume
) {}
