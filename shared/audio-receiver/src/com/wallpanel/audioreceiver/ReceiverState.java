package com.wallpanel.audioreceiver;

import java.util.ArrayList;
import java.util.List;

public class ReceiverState {
  public static final ReceiverState INSTANCE = new ReceiverState();

  private State state = State.DISABLED;
  private String errorMessage = null;
  private boolean rootAvailable = false;
  private String suCommand = "su -c";
  private int restarts = 0;
  private long uptimeStartMs = 0;

  // Streaming info
  private boolean playing = false;
  private String clientIp = null;
  private String clientModel = null;
  private String title = null;
  private String artist = null;
  private String album = null;
  private byte[] artwork = null;
  private boolean artworkAvailable = false;

  // Audio stats
  private int volumeTrim = 100;
  private int underruns = 0;
  private int driftCorrections = 0;
  private String outputDescription = "48000/S16_LE/2";

  private java.util.Map<String,Object> audioTiming = new java.util.LinkedHashMap<>();
  private long lastRtpFrame, lastTargetRawNs;
  public synchronized void setAudioTiming(java.util.Map<String,Object> timing) { audioTiming = new java.util.LinkedHashMap<>(timing); }
  public synchronized void setAudioAnchor(long frame, long rawNs) { lastRtpFrame=frame;lastTargetRawNs=rawNs; }
  public synchronized java.util.Map<String,Object> getAudioTiming() {
    java.util.Map<String,Object> copy=new java.util.LinkedHashMap<>(audioTiming);
    copy.put("lastRtpFrame",lastRtpFrame);copy.put("lastTargetRawNs",lastTargetRawNs);
    if(copy.containsKey("measurementMonoNs"))copy.put("feedbackAgeMs",(System.nanoTime()-((Number)copy.get("measurementMonoNs")).longValue())/1e6);
    return copy;
  }
  public synchronized void clearAudioTiming() { audioTiming.clear();lastRtpFrame=0;lastTargetRawNs=0; }

  // Network
  private String localIp = null;
  private boolean multicastLockHeld = false;

  // HA
  private int haPort = 8099;
  private boolean haTokenEnabled = false;

  // Listeners
  private final List<Listener> listeners = new ArrayList<>();

  public interface Listener {
    void onStateChanged();
  }

  public synchronized void addListener(Listener l) {
    listeners.add(l);
  }

  public synchronized void removeListener(Listener l) {
    listeners.remove(l);
  }

  private void notifyListeners() {
    for (Listener l : new ArrayList<>(listeners)) {
      l.onStateChanged();
    }
  }

  // Getters
  public synchronized State getState() { return state; }
  public synchronized String getErrorMessage() { return errorMessage; }
  public synchronized boolean isRootAvailable() { return rootAvailable; }
  public synchronized String getSuCommand() { return suCommand; }
  public synchronized int getRestarts() { return restarts; }
  public synchronized long getUptimeStartMs() { return uptimeStartMs; }
  public synchronized boolean isPlaying() { return playing; }
  public synchronized String getClientIp() { return clientIp; }
  public synchronized String getClientModel() { return clientModel; }
  public synchronized String getTitle() { return title; }
  public synchronized String getArtist() { return artist; }
  public synchronized String getAlbum() { return album; }
  public synchronized byte[] getArtwork() { return artwork; }
  public synchronized boolean isArtworkAvailable() { return artworkAvailable; }
  public synchronized int getVolumeTrim() { return volumeTrim; }
  public synchronized int getUnderruns() { return underruns; }
  public synchronized int getDriftCorrections() { return driftCorrections; }
  public synchronized String getOutputDescription() { return outputDescription; }
  public synchronized String getLocalIp() { return localIp; }
  public synchronized boolean isMulticastLockHeld() { return multicastLockHeld; }
  public synchronized int getHaPort() { return haPort; }
  public synchronized boolean isHaTokenEnabled() { return haTokenEnabled; }

  // Setters with notification
  public synchronized void setState(State s) { this.state = s; notifyListeners(); }
  public synchronized void setErrorMessage(String msg) { this.errorMessage = msg; notifyListeners(); }
  public synchronized void setRootAvailable(boolean b) { this.rootAvailable = b; notifyListeners(); }
  public synchronized void setSuCommand(String cmd) { this.suCommand = cmd; }
  public synchronized void incRestarts() { this.restarts++; notifyListeners(); }
  public synchronized void setUptimeStartMs(long ms) { this.uptimeStartMs = ms; }
  public synchronized void setPlaying(boolean p) { this.playing=p; if(p && (state==State.RUNNING_IDLE||state==State.STARTING))state=State.STREAMING;else if(!p&&state==State.STREAMING)state=State.RUNNING_IDLE; notifyListeners(); }
  public synchronized void setClientIp(String ip) { this.clientIp = ip; notifyListeners(); }
  public synchronized void setClientModel(String model) { this.clientModel = model; notifyListeners(); }
  public synchronized void setTitle(String t) { this.title = t; notifyListeners(); }
  public synchronized void setArtist(String a) { this.artist = a; notifyListeners(); }
  public synchronized void setAlbum(String a) { this.album = a; notifyListeners(); }
  public synchronized void setArtwork(byte[] data) {
    this.artwork = data;
    this.artworkAvailable = (data != null);
    notifyListeners();
  }
  public synchronized void clearArtwork() { this.artwork = null; this.artworkAvailable = false; notifyListeners(); }
  public synchronized void setVolumeTrim(int v) { this.volumeTrim = v; notifyListeners(); }
  public synchronized void setOutputUnderruns(int value) { this.underruns=value; }
  public synchronized void incUnderruns() { this.underruns++; }
  public synchronized void incDriftCorrections() { this.driftCorrections++; }
  public synchronized void setOutputDescription(String desc) { this.outputDescription = desc; notifyListeners(); }
  public synchronized void setLocalIp(String ip) { this.localIp = ip; notifyListeners(); }
  public synchronized void setMulticastLockHeld(boolean b) { this.multicastLockHeld = b; }
  public synchronized void setHaPort(int p) { this.haPort = p; }
  public synchronized void setHaTokenEnabled(boolean b) { this.haTokenEnabled = b; }

  public synchronized void clearStreamingInfo() {
    playing = false;
    clientIp = null;
    clientModel = null;
    title = null;
    artist = null;
    album = null;
    artwork = null;
    artworkAvailable = false;
    underruns = 0;
    driftCorrections = 0;
    notifyListeners();
  }
}
