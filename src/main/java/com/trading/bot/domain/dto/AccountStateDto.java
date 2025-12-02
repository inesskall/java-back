package com.trading.bot.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccountStateDto {
    private double balance;
    private double equity;

    @JsonProperty("position_side")
    private String positionSide;

    @JsonProperty("position_size")
    private double positionSize;

    @JsonProperty("avg_entry_price")
    private double avgEntryPrice;

    @JsonProperty("last_price")
    private Double lastPrice;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("realized_pnl")
    private double realizedPnl;

    @JsonProperty("position_open_time")
    private LocalDateTime positionOpenTime;

    @JsonProperty("take_profit_price")
    private Double takeProfitPrice;

    @JsonProperty("stop_loss_price")
    private Double stopLossPrice;

    @JsonProperty("position_notional")
    private Double positionNotional;
}
