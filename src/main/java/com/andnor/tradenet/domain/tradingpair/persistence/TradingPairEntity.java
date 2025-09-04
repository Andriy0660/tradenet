package com.andnor.tradenet.domain.tradingpair.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "trading_pairs")
public class TradingPairEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "start_price")
    private BigDecimal startPrice;

    @Column(name = "grid_level_percentage")
    private BigDecimal gridLevelPercentage;

    @Column(name = "long_stop_loss_percentage")
    private BigDecimal longStopLossPercentage;

    @Column(name = "short_stop_loss_percentage")
    private BigDecimal shortStopLossPercentage;

    @Column(name = "position_amount_usdt")
    private BigDecimal positionAmountUsdt;

    @Column(name = "is_active")
    private boolean isActive;
}
