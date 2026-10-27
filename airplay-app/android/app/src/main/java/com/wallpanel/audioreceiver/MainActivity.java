package com.wallpanel.audioreceiver;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
  private BluetoothSinkModule bluetoothSink;
  private TextView tvBluetooth;
  private Button btnBluetoothPair;
  private PrefStore prefs;
  private ReceiverState state;
  private Handler uiHandler;

  private EditText etName;
  private Switch swEnabled;
  private Switch swAutostart;
  private SeekBar sbVolume;
  private EditText etHaPort;
  private Switch swHaEnabled;
  private EditText etHaToken;
  private EditText etRtspPort;
  private SeekBar sbLatency;
  private TextView tvLatencyValue;
  private int latencyOffsetMs;
  private boolean timingSavePending;
  private final Runnable saveTiming = () -> { timingSavePending=false; prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS,latencyOffsetMs); };
  private void saveTimingNow() {
    uiHandler.removeCallbacks(saveTiming);timingSavePending=false;
    prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS,latencyOffsetMs);
  }
  private void saveTimingSoon() {
    if(!timingSavePending){timingSavePending=true;uiHandler.postDelayed(saveTiming,80);}
  }
  private EditText etSuCommand;
  private TextView tvStatus;
  private Button btnApply;
  private Button btnCheckRoot;
  private Button btnLogs;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    prefs = new PrefStore(this);
    state = ReceiverState.INSTANCE;
    uiHandler = new Handler(Looper.getMainLooper());

    bluetoothSink = new BluetoothSinkModule(this);
    bluetoothSink.startObserving();
    bindViews();
    loadPrefs();
    setupListeners();
    startStatusUpdater();
  }

  private void bindViews() {
    tvBluetooth = findViewById(R.id.tvBluetooth);
    btnBluetoothPair = findViewById(R.id.btnBluetoothPair);
    etName = findViewById(R.id.etName);
    swEnabled = findViewById(R.id.swEnabled);
    swAutostart = findViewById(R.id.swAutostart);
    sbVolume = findViewById(R.id.sbVolume);
    etHaPort = findViewById(R.id.etHaPort);
    swHaEnabled = findViewById(R.id.swHaEnabled);
    etHaToken = findViewById(R.id.etHaToken);
    etRtspPort = findViewById(R.id.etRtspPort);
    sbLatency = findViewById(R.id.sbLatency);
    tvLatencyValue = findViewById(R.id.tvLatencyValue);
    etSuCommand = findViewById(R.id.etSuCommand);
    tvStatus = findViewById(R.id.tvStatus);
    btnApply = findViewById(R.id.btnApply);
    btnCheckRoot = findViewById(R.id.btnCheckRoot);
    btnLogs = findViewById(R.id.btnLogs);
  }

  private void loadPrefs() {
    etName.setText(prefs.getReceiverName());
    swEnabled.setChecked(prefs.isEnabled());
    swAutostart.setChecked(prefs.isAutostart());
    sbVolume.setProgress(prefs.getVolumeTrim());
    etHaPort.setText(String.valueOf(prefs.getHaPort()));
    swHaEnabled.setChecked(prefs.isHaEnabled());
    etHaToken.setText(prefs.getHaToken());
    etRtspPort.setText(String.valueOf(prefs.getRtspPort()));
    setLatencyOffset(prefs.getLatencyOffsetMs());
    etSuCommand.setVisibility(View.GONE);
  }

  private void setLatencyOffset(int value) {
    latencyOffsetMs = Math.max(-500, Math.min(500, value));
    if (sbLatency.getProgress() != latencyOffsetMs + 500) {
      sbLatency.setProgress(latencyOffsetMs + 500);
    }
    String signed = (latencyOffsetMs > 0 ? "+" : "") + latencyOffsetMs + " ms";
    String direction = latencyOffsetMs > 0 ? " (later)" : latencyOffsetMs < 0 ? " (earlier)" : " (no offset)";
    tvLatencyValue.setText(signed + direction);
    sbLatency.setContentDescription("Audio timing " + signed + direction);
  }

  private void setupListeners() {
    findViewById(R.id.btnBluetoothEnable).setOnClickListener(v -> openBluetoothIntent(new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)));
    findViewById(R.id.btnBluetoothSettings).setOnClickListener(v -> openBluetoothIntent(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)));
    btnBluetoothPair.setOnClickListener(v -> {
      Intent discover = new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
      discover.putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
      openBluetoothIntent(discover);
    });
    sbLatency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        setLatencyOffset(progress - 500);
        if(fromUser)saveTimingSoon();
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) { saveTimingNow(); }
    });
    findViewById(R.id.btnLatencyMinus).setOnClickListener(v -> {setLatencyOffset(latencyOffsetMs - 5);saveTimingNow();});
    findViewById(R.id.btnLatencyPlus).setOnClickListener(v -> {setLatencyOffset(latencyOffsetMs + 5);saveTimingNow();});
    findViewById(R.id.btnLatencyReset).setOnClickListener(v -> {setLatencyOffset(0);saveTimingNow();});
    btnApply.setOnClickListener(v -> {
      String receiverName = etName.getText().toString().trim();
      if (receiverName.isEmpty() || receiverName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 248) {
        etName.setError("Enter a name of 1-248 UTF-8 bytes");
        return;
      }
      prefs.put(PrefStore.KEY_RECEIVER_NAME, receiverName);
      prefs.put(PrefStore.KEY_ENABLED, swEnabled.isChecked());
      prefs.put(PrefStore.KEY_AUTOSTART, swAutostart.isChecked());
      prefs.put(PrefStore.KEY_VOLUME_TRIM, sbVolume.getProgress());
      prefs.put(PrefStore.KEY_HA_PORT, Integer.parseInt(etHaPort.getText().toString()));
      prefs.put(PrefStore.KEY_HA_ENABLED, swHaEnabled.isChecked());
      prefs.put(PrefStore.KEY_HA_TOKEN, etHaToken.getText().toString());
      prefs.put(PrefStore.KEY_RTSP_PORT, Integer.parseInt(etRtspPort.getText().toString()));
      prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS, latencyOffsetMs);
      prefs.put(PrefStore.KEY_SU_COMMAND, etSuCommand.getText().toString());

      Intent intent = new Intent(this, ReceiverService.class);
      intent.setAction(swEnabled.isChecked() ? ReceiverService.ACTION_START : ReceiverService.ACTION_STOP);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent);
      } else {
        startService(intent);
      }
    });

    btnCheckRoot.setOnClickListener(v -> new Thread(() -> {boolean ok=PrivilegeChannel.available();uiHandler.post(()->btnCheckRoot.setText(ok?"PTP helper available":"PTP helper unavailable"));}).start());
    btnLogs.setOnClickListener(v -> {
      java.io.File logFile = new java.io.File(getFilesDir(), "app.log");
      try {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(logFile));
        StringBuilder sb = new StringBuilder();
        String line;
        int lineCount = 0;
        while ((line = br.readLine()) != null) {
          sb.append(line).append("\n");
          lineCount++;
          if (lineCount > 100) break;
        }
        br.close();
        new android.app.AlertDialog.Builder(this)
            .setTitle("Recent Logs")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
      } catch (Exception e) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage("Failed to read logs: " + e.getMessage())
            .setPositiveButton("OK", null)
            .show();
      }
    });
  }

  private void startStatusUpdater() {
    uiHandler.post(new Runnable() {
      @Override
      public void run() {
        updateStatus();
        uiHandler.postDelayed(this, 1000);
      }
    });
  }

  private void openBluetoothIntent(Intent intent) {
    try { startActivity(intent); }
    catch (android.content.ActivityNotFoundException e) {
      new android.app.AlertDialog.Builder(this).setMessage("Bluetooth settings are unavailable on this firmware.").setPositiveButton("OK", null).show();
    }
  }

  private void updateStatus() {
    java.util.Map<String, Object> bt = bluetoothSink.snapshot();
    boolean configured = Boolean.TRUE.equals(bt.get("sinkConfigured"));
    boolean enabled = Boolean.TRUE.equals(bt.get("enabled"));
    String btText = "Bluetooth: " + bt.get("state");
    if (bt.get("name") != null) btText += "\nPanel name: " + bt.get("name");
    if (!enabled) btText += "\nShared name will apply when Bluetooth is on.";
    else if (!Boolean.TRUE.equals(bt.get("nameMatchesReceiver"))) btText += "\nShared name pending: " + bt.get("desiredName");
    if (bt.get("peerName") != null) btText += "\nPhone: " + bt.get("peerName");
    if (Boolean.FALSE.equals(bt.get("sinkConfigured"))) btText += "\nReceiving music is disabled in this panel's firmware.";
    else if (!configured) btText += "\nFirmware sink support could not be determined.";
    else if (enabled) btText += "\nPair from your phone, then select this panel for media audio.";
    if (bt.get("error") != null) btText += "\n" + bt.get("error");
    tvBluetooth.setText(btText);
    btnBluetoothPair.setEnabled(configured && enabled);
    StringBuilder sb = new StringBuilder();
    sb.append("State: ").append(state.getState().name()).append("\n");
    sb.append("PTP lease: ").append(state.isRootAvailable() ? "✓" : "✗").append("\n");
    sb.append("IP: ").append(state.getLocalIp() != null ? state.getLocalIp() : "unknown").append("\n");
    sb.append("Multicast Lock: ").append(state.isMulticastLockHeld() ? "✓" : "✗").append("\n");
    if (state.isPlaying()) {
      sb.append("\n--- Streaming ---\n");
      if (state.getTitle() != null) sb.append("Title: ").append(state.getTitle()).append("\n");
      if (state.getArtist() != null) sb.append("Artist: ").append(state.getArtist()).append("\n");
      if (state.getClientModel() != null) sb.append("From: ").append(state.getClientModel()).append("\n");
      sb.append("Underruns: ").append(state.getUnderruns()).append("\n");
      sb.append("Drift Corrections: ").append(state.getDriftCorrections()).append("\n");
    }
    sb.append("\nUptime: ").append((System.currentTimeMillis() - state.getUptimeStartMs()) / 1000).append("s\n");
    java.util.Map<String,Object> timing=state.getAudioTiming();
    if(timing.containsKey("clock")) {
      sb.append("Playback timing: ").append(Boolean.TRUE.equals(timing.get("timestampValid")) ? "Audio timestamp available" : "Estimating; timestamp not stable").append("\n");
      sb.append("Output underruns: ").append(timing.get("underruns")).append("\n");
    }
    sb.append("Restarts: ").append(state.getRestarts()).append("\n");
    if (state.getErrorMessage() != null) {
      sb.append("\nError: ").append(state.getErrorMessage()).append("\n");
    }
    tvStatus.setText(sb.toString());
  }

  @Override
  protected void onDestroy() {
    if(timingSavePending)saveTimingNow();
    uiHandler.removeCallbacksAndMessages(null);
    bluetoothSink.close();
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    loadPrefs();
  }
}
