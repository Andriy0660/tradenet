package com.andnor.tradenet.domain.position.persistence;

import com.andnor.tradenet.domain.position.model.PositionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    @Query("SELECT p FROM PositionEntity p " +
           "WHERE p.tradingPair.symbol = :symbol " +
           "AND p.status = 'OPEN' " +
           "AND ((p.type = 'LONG' AND p.takeProfitPrice <= :level) " +
           "OR (p.type = 'SHORT' AND p.takeProfitPrice >= :level))")
    List<PositionEntity> findPositionsToClose(@Param("symbol") String symbol, @Param("level") BigDecimal level);


    @Query("SELECT COUNT(p) > 0 FROM PositionEntity p WHERE p.tradingPair.symbol = :symbol AND p.gridLevelPrice = :level AND p.type = :type AND p.status = 'OPEN'")
    boolean existsOpenPositionAtLevel(@Param("symbol") String symbol, @Param("level") BigDecimal level, @Param("type") PositionType type);

    @Query("SELECT COUNT(p) > 0 FROM PositionEntity p WHERE p.tradingPair.symbol = :symbol AND p.status = 'OPEN'")
    boolean existsByTradingPairSymbol(@Param("symbol") String symbol);

}
