package com.andnor.tradenet.domain.trade.thread;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.andnor.tradenet.domain.exchange.impl.BinanceService;
import com.andnor.tradenet.domain.trade.TradingService;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradingThread Grid Level Crossing Tests")
class TradingThreadTest {

  @Mock
  private BinanceService binanceService;

  @Mock
  private TradingService tradingService;

  @Mock
  private TradingPairEntity tradingPair;

  private TradingThread tradingThread;

  // Тестові константи
  private static final BigDecimal START_PRICE = new BigDecimal("100.00");
  private static final BigDecimal STEP = new BigDecimal("5.00");

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    tradingThread = new TradingThread(tradingPair, binanceService, tradingService);
  }

  @Nested
  @DisplayName("Single Level Crossing Tests")
  class SingleLevelCrossingTests {

    @Test
    @DisplayName("Should find level 105 when price rises from 102 to 107")
    void shouldFindLevel105WhenPriceRisesFromTo102To107() {
      // Given
      BigDecimal oldPrice = new BigDecimal("102.00");
      BigDecimal newPrice = new BigDecimal("107.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertNotNull(result, "Should find crossed level");
      assertEquals(0, new BigDecimal("105.00").compareTo(result),
              "Should return level 105 as the crossed level");
    }

    @Test
    @DisplayName("Should find level 105 when price falls from 107 to 102")
    void shouldFindLevel105WhenPriceFallsFrom107To102() {
      // Given
      BigDecimal oldPrice = new BigDecimal("107.00");
      BigDecimal newPrice = new BigDecimal("102.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertNotNull(result, "Should find crossed level");
      assertEquals(0, new BigDecimal("105.00").compareTo(result),
              "Should return level 105 as the crossed level when falling");
    }

    @Test
    @DisplayName("Should find level 110 when price rises from 106 to 112")
    void shouldFindLevel110WhenPriceRisesFrom106To112() {
      // Given
      BigDecimal oldPrice = new BigDecimal("106.00");
      BigDecimal newPrice = new BigDecimal("112.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("110.00").compareTo(result),
              "Should cross level 110");
    }

    @Test
    @DisplayName("Should find level 110 when price falls from 113 to 108")
    void shouldFindLevel110WhenPriceFallsFrom113To108() {
      // Given
      BigDecimal oldPrice = new BigDecimal("113.00");
      BigDecimal newPrice = new BigDecimal("108.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("110.00").compareTo(result),
              "Should cross level 110 when falling");
    }
  }

  @Nested
  @DisplayName("Multiple Level Crossing Tests")
  class MultipleLevelCrossingTests {

    @Test
    @DisplayName("Should find highest level 125 when price jumps up 5 levels (102 → 127)")
    void shouldFindHighestLevel125WhenPriceJumps5LevelsUp() {
      // Given
      BigDecimal oldPrice = new BigDecimal("102.00");
      BigDecimal newPrice = new BigDecimal("127.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertNotNull(result, "Should find a crossed level when jumping 5 levels");
      assertEquals(0, new BigDecimal("125.00").compareTo(result),
              "Should return level 125 as the highest crossed level " +
                      "(crossed levels: 105, 110, 115, 120, 125)");
    }

    @Test
    @DisplayName("Should find lowest level 105 when price drops 5 levels (127 → 102)")
    void shouldFindLowestLevel105WhenPriceDrops5Levels() {
      // Given
      BigDecimal oldPrice = new BigDecimal("127.00");
      BigDecimal newPrice = new BigDecimal("102.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertNotNull(result, "Should find a crossed level when dropping 5 levels");
      assertEquals(0, new BigDecimal("105.00").compareTo(result),
              "Should return level 105 as the lowest crossed level " +
                      "(crossed levels: 125, 120, 115, 110, 105)");
    }

    @Test
    @DisplayName("Should find level 140 when price rises from 118 to 142")
    void shouldFindLevel140WhenPriceRisesFrom118To142() {
      // Given
      BigDecimal oldPrice = new BigDecimal("118.00");
      BigDecimal newPrice = new BigDecimal("142.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("140.00").compareTo(result),
              "Should cross levels 120, 125, 130, 135, 140 and return the highest: 140");
    }

    @Test
    @DisplayName("Should find level 110 when price falls from 138 to 108")
    void shouldFindLevel110WhenPriceFallsFrom138To108() {
      // Given
      BigDecimal oldPrice = new BigDecimal("138.00");
      BigDecimal newPrice = new BigDecimal("108.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("110.00").compareTo(result),
              "Should cross levels 135, 130, 125, 120, 115, 110 and return the lowest: 110");
    }

    @Test
    @DisplayName("Should find level 120 when price rises exactly 4 levels (103 → 123)")
    void shouldFindLevel120WhenPriceRisesExactly4Levels() {
      // Given
      BigDecimal oldPrice = new BigDecimal("103.00");
      BigDecimal newPrice = new BigDecimal("123.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("120.00").compareTo(result),
              "Should cross levels 105, 110, 115, 120 and return 120");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should return null when price doesn't change")
    void shouldReturnNullWhenPriceDoesntChange() {
      // Given
      BigDecimal samePrice = new BigDecimal("105.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, samePrice, samePrice);

      // Then
      assertNull(result, "Should return null when price doesn't change");
    }

    @Test
    @DisplayName("Should return null when price moves but doesn't cross any level")
    void shouldReturnNullWhenPriceMovesButDoesntCrossLevel() {
      // Given - ціна рухається в межах одного рівня
      BigDecimal oldPrice = new BigDecimal("103.00");
      BigDecimal newPrice = new BigDecimal("104.50");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertNull(result, "Should return null when no grid level is crossed");
    }

    @Test
    @DisplayName("Should handle price landing exactly on grid level")
    void shouldHandlePriceLandingExactlyOnGridLevel() {
      // Given - нова ціна точно дорівнює рівню сітки
      BigDecimal oldPrice = new BigDecimal("103.00");
      BigDecimal newPrice = new BigDecimal("105.00"); // точно рівень!

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("105.00").compareTo(result),
              "Should return level 105 when price lands exactly on it");
    }

    @Test
    @DisplayName("Should handle price falling exactly to grid level")
    void shouldHandlePriceFallingExactlyToGridLevel() {
      // Given - ціна падає точно до рівня
      BigDecimal oldPrice = new BigDecimal("108.00");
      BigDecimal newPrice = new BigDecimal("105.00"); // точно рівень!

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("105.00").compareTo(result),
              "Should return level 105 when price falls exactly to it");
    }

    @Test
    @DisplayName("Should handle very small price movements")
    void shouldHandleVerySmallPriceMovements() {
      // Given - дуже малі зміни ціни
      BigDecimal oldPrice = new BigDecimal("104.99");
      BigDecimal newPrice = new BigDecimal("105.01");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("105.00").compareTo(result),
              "Should detect level crossing even with tiny price movement");
    }

    @Test
    @DisplayName("Should handle negative start price and levels below start")
    void shouldHandleNegativeStartPriceAndLevelsBelowStart() {
      // Given - стартова ціна і рівні нижче стартової ціни
      BigDecimal customStart = new BigDecimal("50.00");
      BigDecimal oldPrice = new BigDecimal("52.00");
      BigDecimal newPrice = new BigDecimal("47.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(customStart, STEP, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("50.00").compareTo(result),
              "Should handle levels below start price (grid: 45, 50, 55...)");
    }
  }

  @Nested
  @DisplayName("Grid Configuration Tests")
  class GridConfigurationTests {

    @Test
    @DisplayName("Should work with different step sizes")
    void shouldWorkWithDifferentStepSizes() {
      // Given - інший розмір кроку
      BigDecimal customStep = new BigDecimal("2.50");
      BigDecimal oldPrice = new BigDecimal("101.00");
      BigDecimal newPrice = new BigDecimal("106.00");

      // When (grid: 100, 102.5, 105, 107.5, 110...)
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, customStep, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("105.00").compareTo(result),
              "Should find level 105.00 with step 2.50 (crosses 102.5, 105.0)");
    }

    @Test
    @DisplayName("Should work with decimal step values")
    void shouldWorkWithDecimalStepValues() {
      // Given - десятковий крок
      BigDecimal decimalStep = new BigDecimal("0.50");
      BigDecimal oldPrice = new BigDecimal("100.20");
      BigDecimal newPrice = new BigDecimal("101.80");

      // When (grid: 100.0, 100.5, 101.0, 101.5, 102.0...)
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, decimalStep, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("101.50").compareTo(result),
              "Should find level 101.50 with decimal step 0.50");
    }
  }

  @Nested
  @DisplayName("Real Trading Scenarios")
  class RealTradingScenariosTests {

    @Test
    @DisplayName("Bitcoin: Small volatility around support level")
    void bitcoinSmallVolatilityAroundSupportLevel() {
      // Given - BTC коливається навколо рівня підтримки
      BigDecimal btcStart = new BigDecimal("50000.00");
      BigDecimal btcStep = new BigDecimal("500.00"); // 1% grid
      BigDecimal oldPrice = new BigDecimal("49800.00");
      BigDecimal newPrice = new BigDecimal("50200.00");

      // When (grid: 49500, 50000, 50500, 51000...)
      BigDecimal result = tradingThread.findGridLevelPrice(btcStart, btcStep, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("50000.00").compareTo(result),
              "Should cross level 50000 when BTC bounces from support");
    }

    @Test
    @DisplayName("USDT pair: Flash crash scenario")
    void usdtPairFlashCrashScenario() {
      // Given - різке падіння ціни (flash crash)
      BigDecimal pairStart = new BigDecimal("1.0000");
      BigDecimal pairStep = new BigDecimal("0.0100"); // 1% grid
      BigDecimal oldPrice = new BigDecimal("1.0520");
      BigDecimal newPrice = new BigDecimal("0.9980");

      // When (grid: 0.99, 1.00, 1.01, 1.02, 1.03, 1.04, 1.05...)
      BigDecimal result = tradingThread.findGridLevelPrice(pairStart, pairStep, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("1.0000").compareTo(result),
              "Should find level 1.0000 as lowest crossed level in flash crash " +
                      "(crosses: 1.05, 1.04, 1.03, 1.02, 1.01, 1.00)");
    }

    @Test
    @DisplayName("Altcoin pump: 10x price increase")
    void altcoinPump10xPriceIncrease() {
      // Given - альткойн памп
      BigDecimal altStart = new BigDecimal("0.10");
      BigDecimal altStep = new BigDecimal("0.05"); // 50% grid steps
      BigDecimal oldPrice = new BigDecimal("0.12");
      BigDecimal newPrice = new BigDecimal("1.20");

      // When (grid: 0.10, 0.15, 0.20, 0.25... 1.15, 1.20)
      BigDecimal result = tradingThread.findGridLevelPrice(altStart, altStep, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("1.20").compareTo(result),
              "Should find level 1.20 as highest crossed in altcoin pump");
    }
  }

  @Nested
  @DisplayName("Precision and Rounding Tests")
  class PrecisionAndRoundingTests {

    @Test
    @DisplayName("Should handle high precision decimal calculations")
    void shouldHandleHighPrecisionDecimalCalculations() {
      // Given - висока точність
      BigDecimal preciseStart = new BigDecimal("99.99999");
      BigDecimal preciseStep = new BigDecimal("0.00001");
      BigDecimal oldPrice = new BigDecimal("99.99999");
      BigDecimal newPrice = new BigDecimal("100.00004");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(preciseStart, preciseStep, oldPrice, newPrice);

      // Then
      assertNotNull(result, "Should handle high precision calculations");
      assertTrue(result.compareTo(oldPrice) > 0 && result.compareTo(newPrice) <= 0,
              "Result should be within price movement range");
    }

    @Test
    @DisplayName("Should handle very large numbers")
    void shouldHandleVeryLargeNumbers() {
      // Given - дуже великі числа
      BigDecimal largeStart = new BigDecimal("1000000.00");
      BigDecimal largeStep = new BigDecimal("50000.00");
      BigDecimal oldPrice = new BigDecimal("1020000.00");
      BigDecimal newPrice = new BigDecimal("1180000.00");

      // When
      BigDecimal result = tradingThread.findGridLevelPrice(largeStart, largeStep, oldPrice, newPrice);

      // Then
      assertEquals(0, new BigDecimal("1150000.00").compareTo(result),
              "Should handle large numbers correctly");
    }
  }

  @Nested
  @DisplayName("Performance Edge Cases")
  class PerformanceEdgeCasesTests {

    @Test
    @DisplayName("Should handle extreme price gaps efficiently")
    void shouldHandleExtremePriceGapsEfficiently() {
      // Given - екстремальний гап у ціні
      BigDecimal oldPrice = new BigDecimal("100.00");
      BigDecimal newPrice = new BigDecimal("10000.00"); // 100x ріст!
      BigDecimal largeStep = new BigDecimal("100.00");

      // When
      long startTime = System.currentTimeMillis();
      BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, largeStep, oldPrice, newPrice);
      long endTime = System.currentTimeMillis();

      // Then
      assertNotNull(result, "Should handle extreme gaps");
      assertTrue(endTime - startTime < 100, "Should complete calculation quickly");
      assertEquals(0, new BigDecimal("10000.00").compareTo(result),
              "Should find the highest crossed level");
    }

    @Test
    @DisplayName("Should not infinite loop with very small steps")
    void shouldNotInfiniteLoopWithVerySmallSteps() {
      // Given - дуже маленький крок (може призвести до багатьох ітерацій)
      BigDecimal tinyStep = new BigDecimal("0.0001");
      BigDecimal oldPrice = new BigDecimal("105.00");
      BigDecimal newPrice = new BigDecimal("100.00");

      // When
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
        BigDecimal result = tradingThread.findGridLevelPrice(START_PRICE, tinyStep, oldPrice, newPrice);
        assertNotNull(result, "Should find a result with tiny steps");
        return result;
      });
    }
  }
}