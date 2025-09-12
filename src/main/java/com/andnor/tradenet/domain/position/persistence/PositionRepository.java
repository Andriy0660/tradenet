package com.andnor.tradenet.domain.position.persistence;

import com.andnor.tradenet.domain.position.model.PositionStatus;
import com.andnor.tradenet.domain.position.model.PositionType;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    @Query("SELECT p FROM PositionEntity p " +
           "WHERE p.tradingPair.id = :id " +
           "AND p.status = 'OPEN' " +
           "AND ((p.type = 'LONG' AND p.takeProfitPrice <= :level) " +
           "OR (p.type = 'SHORT' AND p.takeProfitPrice >= :level))")
    List<PositionEntity> findPositionsToClose(@Param("id") Long id, @Param("level") BigDecimal level);


  @Query("SELECT COUNT(p) > 0 FROM PositionEntity p " +
          "WHERE p.tradingPair.id = :id " +
          "AND p.status = 'OPEN' " +
          "AND p.type = :type " +
          "AND (" +
          "  (:type = 'LONG' AND p.gridLevelPrice <= :level) " +
          "  OR (:type = 'SHORT' AND p.gridLevelPrice >= :level)" +
          ")")
    boolean existsOpenPositionAtLevel(@Param("id") Long id, @Param("level") BigDecimal level, @Param("type") PositionType type);

    @Query("SELECT COUNT(p) > 0 FROM PositionEntity p WHERE p.tradingPair.id = :id AND p.status = 'OPEN'")
    boolean existsByTradingPairId(@Param("id") Long id);

    List<PositionEntity> findAllByTradingPair_Id(Long id);

  List<PositionEntity> getAllByStatusAndTradingPair(PositionStatus status, TradingPairEntity tradingPair);
}
