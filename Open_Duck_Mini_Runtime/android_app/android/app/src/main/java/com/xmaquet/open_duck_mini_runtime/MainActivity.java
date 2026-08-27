package com.xmaquet.open_duck_mini_runtime;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
  private static final int REQ_BLE = 42;
  private static final int MAX_LOGS = 80;

  private RobotBlePlugin ble;
  private Button connectBtn;
  private TextView statusView;
  private TextView txView;
  private TextView txSub;
  private TextView rxView;
  private TextView rxSub;
  private TextView logsView;
  private ScrollView logScroll;

  private boolean connected;
  private boolean connecting;
  private int seq;
  private int txSent;
  private int rxNotifies;
  private int txWindowCount;
  private int rxWindowCount;
  private long rateWindowStart = System.currentTimeMillis();
  private float txHz;
  private float rxHz;
  private final StringBuilder logs = new StringBuilder();
  private final Handler txHandler = new Handler(Looper.getMainLooper());
  private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);

  private final Runnable txTick = new Runnable() {
    @Override
    public void run() {
      if (connected && ble != null && ble.isLinkReady()) {
        String frame = buildNeutralFrame(seq++);
        ble.sendNative(frame);
        txSent++;
        txWindowCount++;
        refreshRates();
        if (txSent % 10 == 0) {
          txView.setText(prettyJson(frame));
        }
        txSub.setText(String.format(Locale.FRANCE, "TX %.1f Hz · seq %d", txHz, seq));
      }
      txHandler.postDelayed(this, 50);
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(RobotBlePlugin.class);
    super.onCreate(savedInstanceState);

    if (getBridge() != null && getBridge().getWebView() != null) {
      WebView webView = getBridge().getWebView();
      webView.stopLoading();
      webView.setVisibility(View.GONE);
      ViewGroup parent = (ViewGroup) webView.getParent();
      if (parent != null) {
        parent.removeView(webView);
      }
    }

    View nativeUi = getLayoutInflater().inflate(R.layout.lot3a_native, null);
    ViewGroup content = findViewById(android.R.id.content);
    content.addView(
      nativeUi,
      new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    );

    connectBtn = nativeUi.findViewById(R.id.lot3a_connect);
    statusView = nativeUi.findViewById(R.id.lot3a_status);
    txView = nativeUi.findViewById(R.id.lot3a_tx);
    txSub = nativeUi.findViewById(R.id.lot3a_tx_sub);
    rxView = nativeUi.findViewById(R.id.lot3a_rx);
    rxSub = nativeUi.findViewById(R.id.lot3a_rx_sub);
    logsView = nativeUi.findViewById(R.id.lot3a_logs);
    logScroll = nativeUi.findViewById(R.id.lot3a_log_scroll);

    PluginHandle handle = getBridge() != null ? getBridge().getPlugin("RobotBle") : null;
    if (handle != null && handle.getInstance() instanceof RobotBlePlugin) {
      ble = (RobotBlePlugin) handle.getInstance();
      ble.setNativeOnStatus(this::onBleStatus);
      ble.setNativeOnRx(this::onBleRx);
      ble.setNativeOnLog(this::appendLog);
    } else {
      appendLog("Plugin RobotBle introuvable");
    }

    connectBtn.setOnClickListener(v -> {
      if (connected || connecting) {
        disconnectBle();
      } else {
        connectBle();
      }
    });

    appendLog("Écran natif prêt. WebView masquée.");
    txHandler.post(txTick);
  }

  private void connectBle() {
    if (ble == null) {
      appendLog("BLE indisponible");
      return;
    }
    if (!hasBlePermissions()) {
      requestBlePermissions();
      return;
    }
    connecting = true;
    connectBtn.setEnabled(false);
    connectBtn.setText("Recherche…");
    statusView.setText("Scan GATT (jusqu’à 10 s)…");
    appendLog("Scan BLE…");
    ble.connectNative(
      name -> runOnUiThread(() -> {
        connecting = false;
        connected = true;
        connectBtn.setEnabled(true);
        connectBtn.setText("Déconnecter");
        statusView.setText("Connecté · " + name);
        appendLog("Connexion BLE établie : " + name);
      }),
      err -> runOnUiThread(() -> {
        connecting = false;
        connected = false;
        connectBtn.setEnabled(true);
        connectBtn.setText("Connecter BLE");
        statusView.setText("Échec : " + err);
        appendLog("Erreur : " + err);
      })
    );
  }

  private void disconnectBle() {
    connecting = false;
    connected = false;
    if (ble != null) {
      ble.disconnectNative();
    }
    connectBtn.setEnabled(true);
    connectBtn.setText("Connecter BLE");
    statusView.setText("Lot 3a — dump TX / RX · non connecté");
    txSub.setText("0.0 Hz · seq —");
    rxSub.setText("0.0 Hz · 0 notify");
    txSent = 0;
    rxNotifies = 0;
    txWindowCount = 0;
    rxWindowCount = 0;
    txHz = 0f;
    rxHz = 0f;
    appendLog("Déconnecté");
  }

  private void onBleStatus(boolean isConnected) {
    runOnUiThread(() -> {
      connected = isConnected;
      if (!isConnected && !connecting) {
        connectBtn.setText("Connecter BLE");
        statusView.setText("Lot 3a — dump TX / RX · non connecté");
        appendLog("Lien GATT perdu");
      }
    });
  }

  private void onBleRx(String text) {
    runOnUiThread(() -> {
      rxNotifies++;
      rxWindowCount++;
      refreshRates();
      rxView.setText(prettyJson(text));
      rxSub.setText(String.format(Locale.FRANCE, "RX %.1f Hz · %d notify", rxHz, rxNotifies));
      if (rxNotifies <= 3 || rxNotifies % 5 == 0) {
        appendLog("RX #" + rxNotifies + " " + text);
      }
    });
  }

  private void refreshRates() {
    long now = System.currentTimeMillis();
    long elapsed = now - rateWindowStart;
    if (elapsed >= 1000) {
      txHz = txWindowCount * 1000f / elapsed;
      rxHz = rxWindowCount * 1000f / elapsed;
      txWindowCount = 0;
      rxWindowCount = 0;
      rateWindowStart = now;
    }
  }

  private boolean hasBlePermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return true;
    }
    return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
      && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
  }

  private void requestBlePermissions() {
    ActivityCompat.requestPermissions(
      this,
      new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT },
      REQ_BLE
    );
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode != REQ_BLE) {
      return;
    }
    if (hasBlePermissions()) {
      connectBle();
    } else {
      appendLog("Permission Bluetooth refusée");
    }
  }

  private void appendLog(String message) {
    String line = clock.format(new Date()) + "  " + message + "\n";
    logs.append(line);
    String all = logs.toString();
    String[] rows = all.split("\n");
    if (rows.length > MAX_LOGS) {
      logs.setLength(0);
      for (int i = rows.length - MAX_LOGS; i < rows.length; i++) {
        logs.append(rows[i]).append('\n');
      }
    }
    logsView.setText(logs.toString());
    logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    Log.i("BDXv2", message);
  }

  private static String buildNeutralFrame(int seq) {
    try {
      JSONObject o = new JSONObject();
      o.put("v", 1);
      o.put("ts_ms", System.currentTimeMillis());
      o.put("seq", seq);
      o.put("axes", new JSONObject().put("lx", 0).put("ly", 0).put("rx", 0).put("ry", 0));
      o.put("triggers", new JSONObject().put("lt", 0).put("rt", 0));
      o.put(
        "buttons",
        new JSONObject()
          .put("A", false)
          .put("B", false)
          .put("X", false)
          .put("Y", false)
          .put("LB", false)
          .put("RB", false)
      );
      o.put("dpad", new JSONObject().put("up", false).put("down", false));
      o.put("safety", new JSONObject().put("estop", false));
      return o.toString();
    } catch (Exception e) {
      return "{}";
    }
  }

  private static String prettyJson(String raw) {
    try {
      return new JSONObject(raw).toString(2);
    } catch (Exception e) {
      return raw;
    }
  }
}
