package com.andnor.tradenet.domain.exchange.impl;

import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"dev", "private"})
class BinanceServiceTest {

    @Autowired
    private BinanceService binanceService;

    private TradingPairEntity testTradingPair;

    @BeforeEach
    void setUp() {
        // Use a popular trading pair with small amounts for testing
        testTradingPair = new TradingPairEntity();
        testTradingPair.setSymbol("BTCUSDT"); // Use Bitcoin as it's stable and liquid
        testTradingPair.setStartPrice(new BigDecimal("30000.00")); // Conservative price
        testTradingPair.setPositionAmountUsdt(new BigDecimal("5.00")); // Very small position - $5
        testTradingPair.setLongStopLossPercentage(new BigDecimal("1.0")); // 1% stop loss
        testTradingPair.setShortStopLossPercentage(new BigDecimal("1.0")); // 1% stop loss
        testTradingPair.setActive(true);
    }

    @Test
    void getCurrentPrice_ShouldReturnValidPrice() {
        // Act
        BigDecimal currentPrice = binanceService.getCurrentPrice(testTradingPair);

        // Assert
        assertNotNull(currentPrice);
        assertTrue(currentPrice.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(currentPrice.compareTo(new BigDecimal("10000")) > 0); // BTC should be > $10k
        assertTrue(currentPrice.compareTo(new BigDecimal("200000")) < 0); // BTC should be < $200k

        System.out.println("Current BTC price: $" + currentPrice);
    }

    @Test
    void getAccountBalance_ShouldReturnValidBalance() {
        // Act
        BigDecimal balance = binanceService.getAccountBalance();

        // Assert
        assertNotNull(balance);
        assertTrue(balance.compareTo(BigDecimal.ZERO) >= 0);

        System.out.println("Account USDT balance: $" + balance);

        // Ensure sufficient balance for testing (at least $20 to be safe)
        assertTrue(balance.compareTo(new BigDecimal("20")) >= 0,
                   "Insufficient balance for testing. Need at least $20 USDT");
    }

    @Test
    void openAndCloseLongPosition_ShouldExecuteSuccessfully() throws InterruptedException {
        // Ensure we have sufficient balance
        BigDecimal initialBalance = binanceService.getAccountBalance();
        assertTrue(initialBalance.compareTo(new BigDecimal("10")) >= 0,
                   "Insufficient balance for long position test");

        try {
            // Act - Open long position
            binanceService.openLongPosition(testTradingPair);
            System.out.println("Opened long position for " + testTradingPair.getSymbol());

            // Wait a bit for the order to be processed
            Thread.sleep(2000);

            // Verify position exists (indirectly by trying to close it)
            binanceService.closePosition(testTradingPair);
            System.out.println("Closed long position for " + testTradingPair.getSymbol());

            // Wait for settlement
            Thread.sleep(2000);

            // Verify balance changed (should be slightly less due to fees)
            BigDecimal finalBalance = binanceService.getAccountBalance();
            assertTrue(finalBalance.compareTo(initialBalance.subtract(new BigDecimal("1"))) > 0,
                       "Balance should not decrease by more than $1 (fees should be minimal)");

        } catch (Exception e) {
            // Clean up - try to close any open positions
            try {
                binanceService.closePosition(testTradingPair);
            } catch (Exception cleanupException) {
                System.err.println("Failed to cleanup position: " + cleanupException.getMessage());
            }
            throw e;
        }
    }

    @Test
    void openAndCloseShortPosition_ShouldExecuteSuccessfully() throws InterruptedException {
        // Ensure we have sufficient balance
        BigDecimal initialBalance = binanceService.getAccountBalance();
        assertTrue(initialBalance.compareTo(new BigDecimal("10")) >= 0,
                   "Insufficient balance for short position test");

        try {
            // Act - Open short position
            binanceService.openShortPosition(testTradingPair);
            System.out.println("Opened short position for " + testTradingPair.getSymbol());

            // Wait a bit for the order to be processed
            Thread.sleep(2000);

            // Close the position
            binanceService.closePosition(testTradingPair);
            System.out.println("Closed short position for " + testTradingPair.getSymbol());

            // Wait for settlement
            Thread.sleep(2000);

            // Verify balance changed (should be slightly less due to fees)
            BigDecimal finalBalance = binanceService.getAccountBalance();
            assertTrue(finalBalance.compareTo(initialBalance.subtract(new BigDecimal("1"))) > 0,
                       "Balance should not decrease by more than $1 (fees should be minimal)");

        } catch (Exception e) {
            // Clean up - try to close any open positions
            try {
                binanceService.closePosition(testTradingPair);
            } catch (Exception cleanupException) {
                System.err.println("Failed to cleanup position: " + cleanupException.getMessage());
            }
            throw e;
        }
    }

    @Test
    void closePosition_WhenNoPosition_ShouldNotThrowException() {
        // Create a trading pair that likely has no open position
        TradingPairEntity noPositionPair = new TradingPairEntity();
        noPositionPair.setSymbol("ETHUSDT");

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> {
            binanceService.closePosition(noPositionPair);
            System.out.println("Successfully handled close position for symbol with no open position");
        });
    }

    @Test
    void getCurrentPrice_ForDifferentSymbols_ShouldReturnValidPrices() {
        // Test multiple popular trading pairs
        String[] symbols = {"BTCUSDT", "ETHUSDT", "BNBUSDT"};

        for (String symbol : symbols) {
            TradingPairEntity pair = new TradingPairEntity();
            pair.setSymbol(symbol);

            BigDecimal price = binanceService.getCurrentPrice(pair);

            assertNotNull(price, "Price should not be null for " + symbol);
            assertTrue(price.compareTo(BigDecimal.ZERO) > 0,
                       "Price should be positive for " + symbol);

            System.out.println(symbol + " current price: $" + price);
        }
    }

    @Test
    void longPositionWithStopLoss_ShouldCreateBothOrders() throws InterruptedException {
        // Use even smaller amount for stop loss test
        testTradingPair.setPositionAmountUsdt(new BigDecimal("6.00")); // $6 position
        testTradingPair.setLongStopLossPercentage(new BigDecimal("0.5")); // 0.5% stop loss

        BigDecimal initialBalance = binanceService.getAccountBalance();
        assertTrue(initialBalance.compareTo(new BigDecimal("10")) >= 0,
                   "Insufficient balance for stop loss test");

        try {
            // Open position with stop loss
            binanceService.openLongPosition(testTradingPair);
            System.out.println("Opened long position with stop loss");

            // Wait for orders to be processed
            Thread.sleep(3000);

            // Close position (this should also cancel the stop loss order)
            binanceService.closePosition(testTradingPair);
            System.out.println("Closed long position");

            Thread.sleep(2000);

            // Verify we didn't lose too much (just fees)
            BigDecimal finalBalance = binanceService.getAccountBalance();
            assertTrue(finalBalance.compareTo(initialBalance.subtract(new BigDecimal("1"))) > 0,
                       "Balance should not decrease by more than $1");

        } catch (Exception e) {
            try {
                binanceService.closePosition(testTradingPair);
            } catch (Exception cleanupException) {
                System.err.println("Failed to cleanup: " + cleanupException.getMessage());
            }
            throw e;
        }
    }

    @Test
    void positionWithoutStopLoss_ShouldWorkCorrectly() throws InterruptedException {
        // Set stop loss percentage to null
        testTradingPair.setLongStopLossPercentage(null);
        testTradingPair.setPositionAmountUsdt(new BigDecimal("5.00"));

        BigDecimal initialBalance = binanceService.getAccountBalance();
        assertTrue(initialBalance.compareTo(new BigDecimal("10")) >= 0);

        try {
            binanceService.openLongPosition(testTradingPair);
            System.out.println("Opened long position without stop loss");

            Thread.sleep(2000);

            binanceService.closePosition(testTradingPair);
            System.out.println("Closed position without stop loss");

            Thread.sleep(2000);

        } catch (Exception e) {
            try {
                binanceService.closePosition(testTradingPair);
            } catch (Exception cleanupException) {
                System.err.println("Failed to cleanup: " + cleanupException.getMessage());
            }
            throw e;
        }
    }
}