package com.andnor.tradenet.domain.telegram.service.impl;

import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.telegram.model.MessageType;
import com.andnor.tradenet.domain.telegram.service.MessageFormatter;
import com.andnor.tradenet.domain.telegram.service.MessageService;
import com.andnor.tradenet.domain.telegram.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
  private final TelegramBotService telegramBotService;
  private final MessageFormatter messageFormatter;

  @Override
  public void broadcastMessage(String message) {
    telegramBotService.broadcastMessage(message);
  }

  @Override
  public void broadcastPositionMessage(MessageType messageType, PositionEntity position) {
    String formattedMessage;

    switch (messageType) {
    case SUCCESSFULLY_CLOSED_POSITION:
      formattedMessage = messageFormatter.formatPositionClosure(position);
      break;
    case SUCCESSFULLY_OPENED_POSITION:
      formattedMessage = messageFormatter.formatPositionOpening(position);
      break;
    default:
      formattedMessage = String.format("❓ Unknown message type for position %d", position.getId());
    }

    telegramBotService.broadcastMessage(formattedMessage);
  }
}