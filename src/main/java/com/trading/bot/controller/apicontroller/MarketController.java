package com.trading.bot.controller.apicontroller;

import com.trading.bot.domain.dto.AgentDecisionDto;
import com.trading.bot.domain.dto.MarketTickDto;
import com.trading.bot.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketDataService marketDataService;

    @GetMapping("/current")
    public ResponseEntity<MarketTickDto> getCurrentPrice() {
        MarketTickDto tick = marketDataService.getLastTick();
        if (tick == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(tick);
    }

    @GetMapping("/last-decision")
    public ResponseEntity<AgentDecisionDto> getLastDecision() {
        AgentDecisionDto decision = marketDataService.getLastDecision();
        if (decision == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(decision);
    }

    @PostMapping("/force-update")
    public ResponseEntity<String> forceUpdate() {
        marketDataService.fetchAndBroadcastMarketData();
        return ResponseEntity.ok("Update request sent to background service");
    }
}