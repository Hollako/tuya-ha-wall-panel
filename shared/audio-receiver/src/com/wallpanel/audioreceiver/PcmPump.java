package com.wallpanel.audioreceiver;

import android.media.*;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.*;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** App-private native audio backend: framed PCM in, measured presentation feedback out. */
public class PcmPump implements Runnable {
  private static final int MAGIC = 0x57504131, FEEDBACK = 0x57504631;
  private final String socketName;
  private final PowerManager.WakeLock wake;
  private final ReceiverState state;
  private final PrefStore prefs;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile boolean running = true;
  private volatile LocalServerSocket server;
  private volatile LocalSocket peer;
  private final AudioOutputGate outputGate=AudioOutputGate.INSTANCE;
  private final Object audioLock = outputGate;
  private AudioOutputGate.Sink outputSink;
  private long outputSession;
  private AudioTrack track;
  private PresentationClock clock;
  private long generation, submitted, lastTimestampPoll, lastReport;
  private int previousUnderruns, previousRoute = -1;
  private volatile int acknowledgedOffset;
  private volatile IOException feedbackError;

  public PcmPump(String name, AudioManager manager, PowerManager.WakeLock wake, ReceiverState state, PrefStore prefs) {
    this.socketName = name; this.wake = wake; this.state = state; this.prefs = prefs;
  }
  public void stop() {
    running = false;
    try { if (peer != null) peer.close(); } catch (IOException ignored) {}
    // Closing a listening fd from another thread need not release a blocked accept.
    // Wake it with a same-UID local connection before closing the listener.
    if (server != null) {
      try (LocalSocket wakeAccept = new LocalSocket()) {
        wakeAccept.connect(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT));
      } catch (IOException ignored) {}
      try { server.close(); } catch (IOException ignored) {}
    }
  }
  private void reset(long nextGeneration) {
    synchronized (audioLock) {
      if (track != null) { outputGate.unbind(outputSink);track.pause(); track.flush(); track.release(); }
      int min = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
      if (min <= 0) throw new IllegalStateException("Unsupported PCM format");
      track = new AudioTrack(AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_STEREO,
          AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 3840), AudioTrack.MODE_STREAM);
      if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack initialization failed");
      clock = new PresentationClock(); submitted = 0; generation = nextGeneration;
      lastTimestampPoll = 0; previousUnderruns = 0; previousRoute = -1;
      final AudioTrack current=track;
      outputSink=new AudioOutputGate.Sink(){public void audible(boolean value){if(current.setVolume(value?com.wallpanel.multiroom.RoomVolume.gain():0f)!=AudioTrack.SUCCESS)throw new IllegalStateException("Receiver output gate failed");}};
      outputGate.bind(outputSink);
      track.play();
    }
  }
  private void releaseTrack() {
    synchronized (audioLock) {
      if (track != null) { outputGate.unbind(outputSink);try { track.stop(); } catch (IllegalStateException ignored) {} track.release(); track = null; }
    }
  }
  private void feedback(LocalSocket socket) {
    try {
      DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 256));
      ByteArrayOutputStream bytes=new ByteArrayOutputStream(80);
      DataOutputStream packet=new DataOutputStream(bytes);
      AudioTimestamp timestamp = new AudioTimestamp();
      while (running && peer == socket) {
        bytes.reset();
        synchronized (audioLock) {
          if (track != null && clock != null) {
            long now = System.nanoTime();
            clock.head(track.getPlaybackHeadPosition());
            int underruns = track.getUnderrunCount();
            AudioDeviceInfo device = track.getRoutedDevice();
            int route = device == null ? -1 : device.getId();
            if (underruns != previousUnderruns || (previousRoute != -1 && route != previousRoute)) {
              clock.invalidate(); lastTimestampPoll = 0;
            }
            previousUnderruns = underruns; previousRoute = route;
            long interval = clock.valid(now) ? 10_000_000_000L : 250_000_000L;
            if (now - lastTimestampPoll >= interval) {
              lastTimestampPoll = now;
              if (track.getTimestamp(timestamp)) clock.timestamp(timestamp.framePosition, timestamp.nanoTime, now, submitted);
              else if (!clock.valid(now)) clock.invalidate();
            }
            boolean timestampValid = clock.valid(now);
            long played = clock.played(now, submitted);
            packet.writeInt(FEEDBACK); packet.writeLong(generation); packet.writeLong(played); packet.writeLong(now);
            packet.writeLong(clock.timestampFrame); packet.writeLong(clock.timestampNano); packet.writeLong(submitted);
            packet.writeInt(underruns); packet.writeInt(clock.discontinuity ? 0 : timestampValid ? 1 : 2);
            packet.writeInt(prefs.getLatencyOffsetMs()); packet.writeInt(0); packet.writeDouble(clock.rate);
            if (now - lastReport >= 200_000_000L) {
              lastReport = now;
              Map<String, Object> report = new LinkedHashMap<>();
              report.put("backend", "android"); report.put("generation", generation);
              report.put("clock", clock.discontinuity ? "INVALID_COUNTER" : timestampValid ? "AUDIO_TIMESTAMP" : "PLAYBACK_HEAD_ESTIMATE");
              report.put("timestampValid", timestampValid); report.put("submittedFrames", submitted);
              report.put("presentedFrames", played); report.put("queuedFrames", submitted - played);
              report.put("measurementMonoNs", now); report.put("timestampFrame", clock.timestampFrame);
              report.put("timestampMonoNs", clock.timestampNano); report.put("estimatedRateHz", clock.rate);
              report.put("rateErrorPpm", (clock.rate / 48000.0 - 1) * 1e6);
              report.put("underruns", underruns); report.put("routeId", route);
              report.put("requestedOffsetMs", prefs.getLatencyOffsetMs());
              report.put("backendObservedOffsetMs", acknowledgedOffset);
              report.put("synchronizationVerified", false);
              state.setOutputUnderruns(underruns);
              state.setAudioTiming(report);
            }
          }
        }
        // Socket backpressure must never prevent the call lease from muting output.
        if(bytes.size()>0){out.write(bytes.toByteArray());out.flush();}
        Thread.sleep(20);
      }
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    catch (Exception e) {
      if (running && peer == socket) { android.util.Log.e("PcmPump", "Audio feedback failed", e); feedbackError = new IOException("Audio feedback failed", e); try { socket.close(); } catch (IOException ignored) {} }
    }
  }
  public void run() {
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
    try {
      server = new LocalServerSocket(socketName);
      while (running) {
        LocalSocket socket = server.accept();
        if (!running) { socket.close(); break; }
        if (socket.getPeerCredentials().getUid() != android.os.Process.myUid()) { socket.close(); continue; }
        peer = socket; feedbackError = null;
        Thread feedback = new Thread(() -> feedback(socket), "wpar-output-feedback");
        boolean feedbackStarted = false;
        try {
          DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 32768));
          byte[] data = new byte[32768 * 4];
          while (running && peer == socket) {
            int magic;
            try { magic = in.readInt(); } catch (EOFException end) { break; }
            int type = in.readInt(); long gen = in.readLong(); int frames = in.readInt();
            int rate = in.readInt(); long rtp = Integer.toUnsignedLong(in.readInt());
            int kindAndOffset = in.readInt(); long targetRawNs = in.readLong();
            if (magic != MAGIC || frames < 0 || frames > 32768 || rate != 48000) throw new IOException("Invalid audio frame header");
            if (type == 1 || type == 3) {
              if (frames != 0 || gen <= generation && feedbackStarted) throw new IOException("Invalid audio generation");
              if(!feedbackStarted)outputSession=outputGate.beginSession();
              reset(gen);
              if (!feedbackStarted) { feedback.start(); feedbackStarted = true; }
            } else if (type == 2) {
              if (!feedbackStarted || gen != generation) throw new IOException("PCM generation mismatch");
              in.readFully(data, 0, frames * 4);
              acknowledgedOffset = (short) (kindAndOffset >>> 16);
              float gain = Math.max(0, Math.min(100, state.getVolumeTrim())) / 100f;
              for (int i = 0; i < frames * 4; i += 2) {
                short sample = (short) ((data[i] & 255) | (data[i + 1] << 8));
                int scaled = Math.round(sample * gain); data[i] = (byte) scaled; data[i + 1] = (byte) (scaled >> 8);
              }
              if (!wake.isHeld()) wake.acquire();
              for (int offset = 0; running && offset < frames * 4;) {
                int length = Math.min(4096, frames * 4 - offset);
                int wrote;
                synchronized (audioLock) {
                  wrote=track.write(data, offset, length,AudioTrack.WRITE_NON_BLOCKING);
                  if(wrote>0)submitted+=wrote/4;
                }
                if (wrote < 0 || wrote % 4 != 0) throw new IOException("AudioTrack write: " + wrote);
                if(wrote==0){Thread.sleep(2);continue;}
                offset += wrote;
              }
              if ((kindAndOffset & 0xffff) == 1 && !state.isPlaying()) main.post(() -> {if(running&&outputGate.active(outputSession))state.setPlaying(true);});
              if((kindAndOffset & 0xffff)==1)state.setAudioAnchor(rtp, targetRawNs);
            } else throw new IOException("Unknown audio operation");
          }
          if (feedbackError != null) throw feedbackError;
        } catch (IOException disconnected) {
          // A peer may close while feedback is in flight. End only this session;
          // the listener must remain available for the next AirPlay connection.
          if (running) android.util.Log.w("PcmPump", "Audio session ended; awaiting reconnect", disconnected);
        } finally {
          peer = null; try { socket.close(); } catch (IOException ignored) {}
          feedback.interrupt(); if (feedbackStarted) feedback.join(500);
          releaseTrack();outputGate.endSession(outputSession); state.clearAudioTiming();
          main.post(() -> { if (peer == null&&!outputGate.streaming()) state.setPlaying(false); });
          if (wake.isHeld()) wake.release();
        }
      }
    } catch (Exception e) {
      if (running) { android.util.Log.e("PcmPump", "Audio output failed", e); main.post(() -> { state.setErrorMessage("PCM: " + e); state.setState(State.ERROR); }); }
    } finally { stop(); releaseTrack(); if (wake.isHeld()) wake.release(); state.clearAudioTiming(); }
  }
}
