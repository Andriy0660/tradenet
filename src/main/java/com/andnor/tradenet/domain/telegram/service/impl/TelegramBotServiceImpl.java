package com.andnor.tradenet.domain.telegram.service.impl;

import com.andnor.tradenet.domain.telegram.service.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

import static com.andnor.tradenet.core.util.TelegramConstants.ABOUT_BOT;
import static com.andnor.tradenet.core.util.TelegramConstants.BOT_NAME;
import static org.telegram.telegrambots.meta.api.methods.ParseMode.HTML;

@Service
@Slf4j
public class TelegramBotServiceImpl extends TelegramLongPollingBot implements TelegramBotService {
  private final static String START = "/start";

  private final static List<Long> activeChats = List.of(1894823688L);

  public TelegramBotServiceImpl(@Value("${telegram.bot.token}") String botToken) {
    super(botToken);
  }

  @Override
  public String getBotUsername() {
    return BOT_NAME;
  }

  @Override
  public void broadcastMessage(String text) {
    for (Long chatId : activeChats) {
      SendMessage message = new SendMessage();
      message.setChatId(chatId.toString());
      message.setText(text);
      message.setParseMode(HTML);
      sendMessage(message);
    }
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      var message = update.getMessage().getText();
      var chatId = update.getMessage().getChatId();

      if (START.equals(message)) {
        startCommand(chatId);
      }
    }
  }

  private void startCommand(Long chatId) {
    SendMessage message = new SendMessage();
    message.setChatId(chatId.toString());
    message.setText(ABOUT_BOT);
    message.setParseMode(HTML);
    sendMessage(message);
  }


  private void sendMessage(SendMessage sendMessage) {
    try {
      execute(sendMessage);
    } catch (TelegramApiException e) {
      log.error(e.getMessage());
    }
  }

}
