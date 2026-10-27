package com.wallpanel.audioreceiver;

import java.util.LinkedHashMap;
import java.util.Map;

/** Adapts existing AirPlay state without changing its process or audio lifecycle. */
public final class AirPlaySourceModule implements AudioSourceModule {
  private final ReceiverState state;
  public AirPlaySourceModule(ReceiverState state) { this.state = state; }
  public String sourceId() { return "airplay"; }
  public void startObserving() {}
  public void close() {}
  public Map<String, Object> snapshot() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", sourceId());
    result.put("state", state.getState().name());
    result.put("playing", state.isPlaying());
    result.put("volumeControl", "app_pcm_trim");
    return result;
  }
}
