package com.xmaquet.open_duck_mini_runtime;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
  private static final int REQ_BLE = 42;

  private enum Screen {
    HOME,
    PILOT,
    TESTS,
    SHUTDOWN,
    VIDEO
  }

  private ViewGroup root;
  private FrameLayout screenContainer;
  private View bleBanner;
  private View bleDot;
  private TextView bleBannerText;
  private Button bleBannerConnect;

  private RobotBlePlugin ble;
  private TextView statusView;
  private TextView rxLine;
  private TextView testsHint;

  private VirtualStickView stickLeft;
  private VirtualStickView stickRight;

  private boolean pause;
  private boolean son;
  private boolean proj;
  private boolean tete;
  private boolean rythme;
  private boolean dpadUp;
  private boolean dpadDown;
  private boolean antD;
  private boolean antG;
  private boolean estop;

  private Screen current = Screen.HOME;
  private boolean connected;
  private boolean connecting;
  private int seq;
  private final Handler txHandler = new Handler(Looper.getMainLooper());

  private final Runnable txTick =
      new Runnable() {
        @Override
        public void run() {
          if (current == Screen.PILOT && connected && ble != null && ble.isLinkReady()) {
            ble.sendNative(buildPadFrame(seq++));
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

    root = (ViewGroup) getLayoutInflater().inflate(R.layout.activity_root, null);
    ViewGroup content = findViewById(android.R.id.content);
    content.addView(
        root,
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    screenContainer = root.findViewById(R.id.screen_container);
    bleBanner = root.findViewById(R.id.ble_banner);
    bleDot = root.findViewById(R.id.ble_dot);
    bleBannerText = root.findViewById(R.id.ble_banner_text);
    bleBannerConnect = root.findViewById(R.id.ble_banner_connect);
    bleBannerConnect.setOnClickListener(
        v -> {
          if (connected || connecting) {
            disconnectBle();
          } else {
            connectBle();
          }
        });
    refreshBleBanner();

    PluginHandle handle = getBridge() != null ? getBridge().getPlugin("RobotBle") : null;
    if (handle != null && handle.getInstance() instanceof RobotBlePlugin) {
      ble = (RobotBlePlugin) handle.getInstance();
      ble.setNativeOnStatus(this::onBleStatus);
      ble.setNativeOnRx(this::onBleRx);
      ble.setNativeOnLog(msg -> Log.i("BDXv2", msg));
    }

    showScreen(Screen.HOME);
    txHandler.post(txTick);
  }

  @Override
  public void onBackPressed() {
    if (current != Screen.HOME) {
      showScreen(Screen.HOME);
      return;
    }
    super.onBackPressed();
  }

  private void showScreen(Screen screen) {
    current = screen;
    clearBindings();
    screenContainer.removeAllViews();
    switch (screen) {
      case HOME:
        bindHome();
        break;
      case PILOT:
        bindPilot();
        break;
      case TESTS:
        bindTests();
        break;
      case SHUTDOWN:
        bindShutdown();
        break;
      case VIDEO:
        bindSimple(R.layout.screen_video);
        break;
    }
  }

  private void bindHome() {
    View view = getLayoutInflater().inflate(R.layout.screen_home, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.home_pilot).setOnClickListener(v -> showScreen(Screen.PILOT));
    view.findViewById(R.id.home_tests).setOnClickListener(v -> showScreen(Screen.TESTS));
    view.findViewById(R.id.home_shutdown).setOnClickListener(v -> showScreen(Screen.SHUTDOWN));
    view.findViewById(R.id.home_video).setOnClickListener(v -> showScreen(Screen.VIDEO));
  }

  private void bindPilot() {
    View view = getLayoutInflater().inflate(R.layout.screen_pilot, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.btn_back_home).setOnClickListener(v -> showScreen(Screen.HOME));
    statusView = view.findViewById(R.id.pilot_status);
    rxLine = view.findViewById(R.id.pilot_rx_line);
    stickLeft = view.findViewById(R.id.stick_left);
    stickRight = view.findViewById(R.id.stick_right);
    bindHold(view.findViewById(R.id.btn_pause), v -> pause = v);
    bindHold(view.findViewById(R.id.btn_son), v -> son = v);
    bindHold(view.findViewById(R.id.btn_proj), v -> proj = v);
    bindHold(view.findViewById(R.id.btn_tete), v -> tete = v);
    bindHold(view.findViewById(R.id.btn_rythme), v -> rythme = v);
    bindHold(view.findViewById(R.id.btn_tempo_plus), v -> dpadUp = v);
    bindHold(view.findViewById(R.id.btn_tempo_minus), v -> dpadDown = v);
    bindHold(view.findViewById(R.id.btn_ant_d), v -> antD = v);
    bindHold(view.findViewById(R.id.btn_ant_g), v -> antG = v);
    bindHold(view.findViewById(R.id.btn_stop), v -> estop = v);
    refreshPilotStatus();
  }

  private void bindTests() {
    View view = getLayoutInflater().inflate(R.layout.screen_tests, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.btn_back_home).setOnClickListener(v -> showScreen(Screen.HOME));
    testsHint = view.findViewById(R.id.tests_hint);
    view.findViewById(R.id.test_eyes_steady).setOnClickListener(v -> sendTest("eyes_steady"));
    view.findViewById(R.id.test_eyes_blink).setOnClickListener(v -> sendTest("eyes_blink"));
    view.findViewById(R.id.test_projector).setOnClickListener(v -> sendTest("projector"));
    view.findViewById(R.id.test_speaker).setOnClickListener(v -> sendTest("speaker"));
    view.findViewById(R.id.test_antennas_wiggle).setOnClickListener(v -> sendTest("antennas_wiggle"));
    view.findViewById(R.id.test_antennas_pulse).setOnClickListener(v -> sendTest("antennas_pulse"));
  }

  private void bindSimple(int layout) {
    View view = getLayoutInflater().inflate(layout, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.btn_back_home).setOnClickListener(v -> showScreen(Screen.HOME));
  }

  private void bindShutdown() {
    View view = getLayoutInflater().inflate(R.layout.screen_shutdown, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.btn_back_home).setOnClickListener(v -> showScreen(Screen.HOME));
    CheckBox confirm = view.findViewById(R.id.shutdown_confirm);
    Button send = view.findViewById(R.id.shutdown_send);
    TextView result = view.findViewById(R.id.shutdown_result);
    send.setEnabled(false);
    confirm.setOnCheckedChangeListener((box, checked) -> send.setEnabled(checked));
    send.setOnClickListener(
        v ->
            result.setText(
                "Rien n’a été envoyé. Le message d’arrêt n’est pas encore figé dans protocol.md. Filet : SSH sudo poweroff."));
  }

  private void sendTest(String action) {
    if (ble == null || !connected || !ble.isLinkReady()) {
      if (testsHint != null) {
        testsHint.setText("BLE non prêt — connecte d’abord (bandeau du haut).");
      }
      return;
    }
    try {
      JSONObject o = new JSONObject();
      o.put("type", "test");
      o.put("v", 1);
      o.put("action", action);
      ble.sendNative(o.toString());
      if (testsHint != null) {
        testsHint.setText("Envoyé : " + action);
      }
    } catch (Exception e) {
      if (testsHint != null) {
        testsHint.setText("Envoi impossible");
      }
    }
  }

  private void bindHold(View button, HoldListener listener) {
    button.setOnTouchListener(
        (v, event) -> {
          int action = event.getActionMasked();
          if (action == MotionEvent.ACTION_DOWN) {
            listener.onHold(true);
            v.setPressed(true);
            return true;
          }
          if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            listener.onHold(false);
            v.setPressed(false);
            return true;
          }
          return true;
        });
  }

  private interface HoldListener {
    void onHold(boolean down);
  }

  private void clearBindings() {
    statusView = null;
    rxLine = null;
    testsHint = null;
    stickLeft = null;
    stickRight = null;
    pause = false;
    son = false;
    proj = false;
    tete = false;
    rythme = false;
    dpadUp = false;
    dpadDown = false;
    antD = false;
    antG = false;
    estop = false;
  }

  private void refreshBleBanner() {
    if (bleBanner == null || bleBannerText == null || bleBannerConnect == null) {
      return;
    }
    if (connecting) {
      bleBanner.setBackgroundResource(R.drawable.ble_banner_off);
      bleDot.setBackgroundResource(R.drawable.ble_dot_off);
      bleBannerText.setText("BLE RECHERCHE…");
      bleBannerConnect.setText("Annuler");
      bleBannerConnect.setEnabled(true);
      return;
    }
    bleBannerConnect.setEnabled(true);
    if (connected) {
      bleBanner.setBackgroundResource(R.drawable.ble_banner_on);
      bleDot.setBackgroundResource(R.drawable.ble_dot_on);
      bleBannerText.setText("BLE CONNECTÉ");
      bleBannerConnect.setText("Couper");
    } else {
      bleBanner.setBackgroundResource(R.drawable.ble_banner_error);
      bleDot.setBackgroundResource(R.drawable.ble_dot_off);
      bleBannerText.setText("BLE DÉCONNECTÉ");
      bleBannerConnect.setText("Connecter");
    }
  }

  private void refreshPilotStatus() {
    if (statusView == null) {
      return;
    }
    statusView.setText(connected ? "lien OK" : "sans lien");
  }

  private void connectBle() {
    if (ble == null) {
      return;
    }
    if (!hasBlePermissions()) {
      requestBlePermissions();
      return;
    }
    connecting = true;
    refreshBleBanner();
    ble.connectNative(
        name ->
            runOnUiThread(
                () -> {
                  connecting = false;
                  connected = true;
                  refreshBleBanner();
                  refreshPilotStatus();
                }),
        err ->
            runOnUiThread(
                () -> {
                  connecting = false;
                  connected = false;
                  refreshBleBanner();
                  refreshPilotStatus();
                  if (bleBannerText != null) {
                    bleBannerText.setText("BLE ÉCHEC");
                  }
                }));
  }

  private void disconnectBle() {
    connecting = false;
    connected = false;
    if (ble != null) {
      ble.disconnectNative();
    }
    refreshBleBanner();
    refreshPilotStatus();
  }

  private void onBleStatus(boolean isConnected) {
    runOnUiThread(
        () -> {
          connected = isConnected;
          if (!isConnected) {
            connecting = false;
          }
          refreshBleBanner();
          refreshPilotStatus();
        });
  }

  private void onBleRx(String text) {
    runOnUiThread(
        () -> {
          if (rxLine != null) {
            rxLine.setText(text);
          }
          if (testsHint != null
              && (text.contains("Yeux")
                  || text.contains("Projecteur")
                  || text.contains("Haut-parleur")
                  || text.contains("Antennes"))) {
            testsHint.setText(text);
          }
        });
  }

  private boolean hasBlePermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return true;
    }
    return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
  }

  private void requestBlePermissions() {
    ActivityCompat.requestPermissions(
        this,
        new String[] {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
        REQ_BLE);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_BLE && hasBlePermissions()) {
      connectBle();
    }
  }

  private String buildPadFrame(int seqValue) {
    float lx = 0f;
    float ly = 0f;
    float rx = 0f;
    float ry = 0f;
    if (stickLeft != null) {
      lx = -stickLeft.getNx();
      ly = -stickLeft.getNy();
    }
    if (stickRight != null) {
      rx = -stickRight.getNx();
      ry = -stickRight.getNy();
    }
    try {
      JSONObject o = new JSONObject();
      o.put("v", 1);
      o.put("ts_ms", System.currentTimeMillis());
      o.put("seq", seqValue);
      o.put("axes", new JSONObject().put("lx", lx).put("ly", ly).put("rx", rx).put("ry", ry));
      o.put("triggers", new JSONObject().put("lt", antD ? 1 : 0).put("rt", antG ? 1 : 0));
      o.put(
          "buttons",
          new JSONObject()
              .put("A", pause)
              .put("B", son)
              .put("X", proj)
              .put("Y", tete)
              .put("LB", rythme)
              .put("RB", false));
      o.put("dpad", new JSONObject().put("up", dpadUp).put("down", dpadDown));
      o.put("safety", new JSONObject().put("estop", estop));
      return o.toString();
    } catch (Exception e) {
      return "{}";
    }
  }
}
