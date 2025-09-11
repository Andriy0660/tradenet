package com.andnor.tradenet.domain.position.persistence;

import com.andnor.tradenet.domain.position.model.PositionStatus;
import com.andnor.tradenet.domain.position.model.PositionType;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "positions")
public class PositionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "trading_pair_id")
    private TradingPairEntity tradingPair;

    @Column(name = "grid_level_price")
    private BigDecimal gridLevelPrice;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private PositionType type;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private PositionStatus status;

    @Column(name = "end_price")
    private BigDecimal endPrice;

    @Column(name = "stop_loss_price")
    private BigDecimal stopLossPrice;

    @Column(name = "take_profit_price")
    private BigDecimal takeProfitPrice;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
