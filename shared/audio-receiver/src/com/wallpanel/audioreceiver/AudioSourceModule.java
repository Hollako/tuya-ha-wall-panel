package com.wallpanel.audioreceiver;

import java.util.Map;

/** Independent receiver observation. A future coordinator owns selection and call priority. */
public interface AudioSourceModule {
  String sourceId();
  void startObserving();
  Map<String, Object> snapshot();
  void close();
}
