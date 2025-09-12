package com.andnor.tradenet.domain.telegram.service;

import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.telegram.model.MessageType;

public interface MessageService {
  void broadcastMessage(String  message);
  void broadcastPositionMessage(MessageType messageType, PositionEntity position);
}

