package com.andnor.tradenet.domain.tradingpair.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradingPairRepository extends JpaRepository<TradingPairEntity, Long> {
    List<TradingPairEntity> findByIsActiveTrue();
}
