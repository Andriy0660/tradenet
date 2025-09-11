package com.andnor.tradenet.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class SymbolInfo {
    private int quantityPrecision;
    private int pricePrecision;
    private BigDecimal stepSize;
    private BigDecimal tickSize;
}
