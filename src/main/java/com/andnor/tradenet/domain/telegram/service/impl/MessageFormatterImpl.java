package com.andnor.tradenet.domain.telegram.service.impl;

import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.telegram.service.MessageFormatter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

@Service
public class MessageFormatterImpl implements MessageFormatter {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  @Override
  public String formatPositionClosure(PositionEntity position) {
    BigDecimal entryPrice = position.getStartPrice();
    BigDecimal exitPrice = position.getEndPrice();
    BigDecimal quantity = position.getQuantity();

    BigDecimal profitLoss;
    BigDecimal profitLossPercentage;

    if (position.getType().toString().equals("LONG")) {
      profitLoss = exitPrice.subtract(entryPrice).multiply(quantity);
      profitLossPercentage = exitPrice.subtract(entryPrice)
              .divide(entryPrice, 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100));
    } else {
      profitLoss = entryPrice.subtract(exitPrice).multiply(quantity);
      profitLossPercentage = entryPrice.subtract(exitPrice)
              .divide(entryPrice, 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100));
    }

    boolean isProfit = profitLoss.compareTo(BigDecimal.ZERO) >= 0;
    String profitLossSign = isProfit ? "+" : "";
    String emoji = isProfit ? "💚" : "❌";

    Duration duration = Duration.between(position.getOpenedAt(), position.getClosedAt());
    String durationStr = formatDuration(duration);

    String closeTime = position.getClosedAt()
            .atZone(java.time.ZoneId.systemDefault())
            .format(TIME_FORMATTER);

    String symbol = position.getTradingPair().getSymbol();

    return String.format(
            "🎯 POSITION CLOSED\n" +
                    "📊 %s %s | ID: %d\n" +
                    "💰 %s$%.2f → $%.2f | Qty: %.4f\n" +
                    "📈 P&L: %s$%.2f (%s%.2f%%)\n" +
                    "⏱️ Duration: %s | Closed: %s\n" +
                    "🎚️ Grid Level: $%.2f | USD: $%.2f",
            symbol,
            position.getType(),
            position.getId(),
            position.getType().toString().equals("LONG") ? "📈" : "📉",
            entryPrice,
            exitPrice,
            quantity,
            emoji,
            profitLoss.abs(),
            profitLossSign,
            profitLossPercentage,
            durationStr,
            closeTime,
            position.getGridLevelPrice(),
            position.getUsdAmount()
    );
  }

  @Override
  public String formatPositionOpening(PositionEntity position) {
    String openTime = position.getOpenedAt()
            .atZone(java.time.ZoneId.systemDefault())
            .format(TIME_FORMATTER);

    String symbol = position.getTradingPair().getSymbol();

    StringBuilder message = new StringBuilder();
    message.append("🚀 POSITION OPENED\n")
            .append(String.format("📊 %s %s | ID: %d\n", symbol, position.getType(), position.getId()))
            .append(String.format("💰 Entry: $%.2f | Qty: %.4f\n", position.getStartPrice(), position.getQuantity()));

    if (position.getTakeProfitPrice() != null) {
      message.append(String.format("🎯 Take Profit: $%.2f\n", position.getTakeProfitPrice()));
    }

    if (position.getStopLossPrice() != null) {
      message.append(String.format("🛡️ Stop Loss: $%.2f\n", position.getStopLossPrice()));
    }

    message.append(String.format("⏰ Opened: %s | Grid: $%.2f\n", openTime, position.getGridLevelPrice()))
            .append(String.format("💵 USD Amount: $%.2f", position.getUsdAmount()));

    return message.toString();
  }

  private String formatDuration(Duration duration) {
    long days = duration.toDays();
    long hours = duration.toHoursPart();
    long minutes = duration.toMinutesPart();

    if (days > 0) {
      return String.format("%dd %dh %dm", days, hours, minutes);
    } else if (hours > 0) {
      return String.format("%dh %dm", hours, minutes);
    } else if (minutes > 0) {
      return String.format("%dm", minutes);
    } else {
      return "< 1m";
    }
  }
}