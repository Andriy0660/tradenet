package com.andnor.tradenet.domain.position.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
@Setter
public class StopLossOrderInfo {
  private Boolean shouldClosePosition;
  private Long stopLossOrderId;
  private BigDecimal endPriceIfForceClosed;
}
