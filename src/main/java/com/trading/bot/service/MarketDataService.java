package com.trading.bot.service;

import com.trading.bot.config.MarketProperties;
import com.trading.bot.config.TradingProperties;
import com.trading.bot.domain.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataService {

    private final WebSocketClient binanceWebSocketClient;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MarketProperties marketProps;
    private final PythonAgentClient pythonAgentClient;
    private final TradingProperties tradingProperties;

    @Getter
    private volatile MarketTickDto lastTick;

    @Getter
    private volatile AgentDecisionDto lastDecision;

    @PostConstruct
    public void startStreaming() {
        connectToBinance();
    }

    private void connectToBinance() {
        URI uri = URI.create(marketProps.getStreamUrl());

        binanceWebSocketClient
                .execute(uri, session ->
                        session.receive()
                                .map(WebSocketMessage::getPayloadAsText)
                                .map(this::parseBinanceMessage)
                                .filter(msg -> msg != null && msg.getKline() != null)
                                .map(this::convertToTick)
                                .flatMap(this::handleTick)
                                .onErrorResume(ex -> {
                                    log.error("Error in Binance WS stream", ex);
                                    return Mono.empty();
                                })
                                .then()
                )
                .doOnError(ex -> log.error("Binance WS connection error", ex))
                .repeat()
                .subscribe();
    }

    private BinanceKlineMessage parseBinanceMessage(String json) {
        try {
            return objectMapper.readValue(json, BinanceKlineMessage.class);
        } catch (Exception e) {
            log.warn("Failed to parse Binance kline message: {}", json, e);
            return null;
        }
    }

    private MarketTickDto convertToTick(BinanceKlineMessage msg) {
        BinanceKlineMessage.Kline k = msg.getKline();

        double open = parseDouble(k.getOpenPrice());
        double high = parseDouble(k.getHighPrice());
        double low = parseDouble(k.getLowPrice());
        double close = parseDouble(k.getClosePrice());
        double volume = parseDouble(k.getVolume());

        LocalDateTime ts = Instant.ofEpochMilli(k.getCloseTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        return new MarketTickDto(
                k.getSymbol(),
                ts,
                open,
                high,
                low,
                close,
                volume
        );
    }

    private double parseDouble(String v) {
        try {
            return v == null ? 0.0 : Double.parseDouble(v);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Mono<Void> handleTick(MarketTickDto tick) {
        this.lastTick = tick;
        messagingTemplate.convertAndSend("/topic/market", tick);

        return pythonAgentClient.sendTickAndGetDecision(tick)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(botDecision -> {
                    AgentDecisionDto uiDecision = buildAgentDecisionDto(botDecision, tick);
                    this.lastDecision = uiDecision;

                    messagingTemplate.convertAndSend("/topic/agent/decision", uiDecision);

                    log.info("Agent decision: action={}, symbol={}, qty={}, price={}, balance={}, equity={}, roi={}",
                            uiDecision.getAction(), uiDecision.getSymbol(),
                            uiDecision.getQuantity(), uiDecision.getPrice(),
                            uiDecision.getBalance(), uiDecision.getEquity(), uiDecision.getRoiPct());
                })
                .then();
    }

    private AgentDecisionDto buildAgentDecisionDto(BotDecisionDto botDecision, MarketTickDto tick) {
        AccountStateDto account = botDecision.getAccount();
        List<TradeEventDto> trades = botDecision.getTrades();

        TradeEventDto lastTrade = (trades != null && !trades.isEmpty())
                ? trades.get(trades.size() - 1)
                : null;

        String action = "HOLD";
        String reason = null;
        String symbol = tick.getSymbol();
        Double quantity = null;
        Double price = tick.getClose();

        if (botDecision.getDebug() != null) {
            Object strategyAction = botDecision.getDebug().get("strategy_action");
            Object strategyReason = botDecision.getDebug().get("strategy_reason");

            if (strategyAction != null) {
                action = String.valueOf(strategyAction);
            }
            if (strategyReason != null) {
                reason = String.valueOf(strategyReason);
            }
        }

        if (lastTrade != null) {
            symbol = lastTrade.getSymbol();
            quantity = lastTrade.getVolume();
            price = lastTrade.getPrice();
            if (lastTrade.getReason() != null) {
                reason = lastTrade.getReason();
            }
        }

        double balance = account != null ? account.getBalance() : 0.0;
        double equity = account != null ? account.getEquity() : balance;

        double initialBalance = tradingProperties.getInitialBalance();
        Double roiPct = null;
        if (initialBalance > 0) {
            roiPct = (equity - initialBalance) / initialBalance * 100.0;
        }

        Double realizedPnl = null;
        if (account != null) {
            realizedPnl = account.getRealizedPnl();

        }

        AgentDecisionDto dto = new AgentDecisionDto();
        dto.setAction(action);
        dto.setSymbol(symbol);
        dto.setQuantity(quantity);
        dto.setPrice(price);
        dto.setReason(reason);
        dto.setBalance(balance);
        dto.setEquity(equity);
        dto.setRealizedPnl(realizedPnl);
        dto.setRoiPct(roiPct);

        if (account != null) {
            dto.setPositionSide(account.getPositionSide());
            dto.setPositionSize(account.getPositionSize());
            dto.setPositionOpenTime(account.getPositionOpenTime());
            dto.setTakeProfitPrice(account.getTakeProfitPrice());
            dto.setStopLossPrice(account.getStopLossPrice());
            dto.setPositionNotional(account.getPositionNotional());
            dto.setAvgEntryPrice(account.getAvgEntryPrice());
        }

        return dto;
    }

    public void fetchAndBroadcastMarketData() {}
}
