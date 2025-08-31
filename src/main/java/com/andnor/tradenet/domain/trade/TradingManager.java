package com.andnor.tradenet.domain.trade;

import com.andnor.tradenet.domain.exchange.impl.BinanceService;
import com.andnor.tradenet.domain.trade.thread.TradingThread;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingManager {
    private final TradingPairRepository tradingPairRepository;
    private final BinanceService binanceService;
    private final Map<String, TradingThread> activeThreads = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final TradingService tradingService;

    @PostConstruct
    public void initializeActiveTrading() {
        List<TradingPairEntity> activePairs = tradingPairRepository.findByIsActiveTrue();
        for (TradingPairEntity pair : activePairs) {
            startTrading(pair);
        }
    }

    public void startTrading(TradingPairEntity pair) {
        if (activeThreads.containsKey(pair.getSymbol())) {
            log.error("Trading already active for {}", pair.getSymbol());
            return;
        }

        TradingThread tradingThread = new TradingThread(pair, binanceService, tradingService);
        activeThreads.put(pair.getSymbol(), tradingThread);
        executorService.submit(tradingThread);

        log.info("Started trading thread for {}", pair.getSymbol());
    }

    public void stopTrading(String symbol) {
        TradingThread thread = activeThreads.remove(symbol);
        if (thread != null) {
            thread.stop();
            log.info("Stopped trading thread for {}", symbol);
        }
    }

    public void stopAllTrading() {
        activeThreads.values().forEach(TradingThread::stop);
        activeThreads.clear();
        executorService.shutdown();
        log.info("Stopped all trading threads");
    }

    @PreDestroy
    public void cleanup() {
        stopAllTrading();
    }

}
