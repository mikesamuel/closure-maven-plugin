package com.google.common.html.plugin.plan;

import java.io.Serializable;

public interface KeyedSerializable extends Serializable {
  PlanKey getKey();
}
