package com.trading.bot.service;

import com.trading.bot.domain.dto.BotDecisionDto;
import com.trading.bot.domain.dto.MarketTickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class PythonAgentClient {

    private final WebClient pythonAgentWebClient;

    @Value("${python.agent.timeout-seconds:5}")
    private long timeoutSeconds;

    public Mono<BotDecisionDto> sendTickAndGetDecision(MarketTickDto tick) {
        log.info("Sending tick to Python agent: {}", tick);

        return pythonAgentWebClient.post()
                .uri("/api/agent/on-tick")
                .bodyValue(tick)
                .retrieve()
                .bodyToMono(BotDecisionDto.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnNext(decision ->
                        log.info("Received decision from Python agent: {}", decision)
                )
                .doOnError(WebClientResponseException.class, e ->
                        log.error("Python agent HTTP error: status={} body={}",
                                e.getStatusCode(), e.getResponseBodyAsString(), e)
                )
                .doOnError(e ->
                        log.error("Failed to get decision from Python agent. Tick = {}", tick, e)
                )
                .onErrorResume(e -> Mono.empty());
    }
}
