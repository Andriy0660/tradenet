package com.andnor.tradenet.domain.telegram.service;

import com.andnor.tradenet.domain.position.persistence.PositionEntity;

public interface MessageFormatter {
  String formatPositionClosure(PositionEntity position);
  String formatPositionOpening(PositionEntity position);
}
