package com.andnor.tradenet.domain.position.persistence;

import com.andnor.tradenet.domain.position.model.PositionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    @Query("SELECT p FROM PositionEntity p WHERE p.tradingPair.symbol = :symbol AND p.takeProfitPrice = :level AND p.status = 'OPEN'")
    List<PositionEntity> findOpenPositionsWithTakeProfitAt(@Param("symbol") String symbol, @Param("level") BigDecimal level);

    @Query("SELECT COUNT(p) > 0 FROM PositionEntity p WHERE p.tradingPair.symbol = :symbol AND p.gridLevel = :level AND p.type = :type AND p.status = 'OPEN'")
    boolean existsOpenPositionAtLevel(@Param("symbol") String symbol, @Param("level") BigDecimal level, @Param("type") PositionType type);}
