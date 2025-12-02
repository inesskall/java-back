package com.trading.bot.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BotDecisionDto {
    private List<TradeEventDto> trades;
    private AccountStateDto account;
    private Map<String, Object> debug;
}
