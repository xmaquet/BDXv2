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
import android.widget.GridLayout;
import android.widget.TextView;
import org.json.JSONArray;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;
import org.json.JSONObject;
import java.util.Locale;

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

  private static final String[] FALLBACK_SOUNDS = {
    "beep1.wav",
    "beep2.wav",
    "happy1.wav",
    "happy2.wav",
    "happy3.wav",
    "lamp.wav",
    "lamp2.wav",
    "lamp3.wav",
    "motor.wav"
  };

  private RobotBlePlugin ble;
  private TextView statusView;
  private TextView rxLine;
  private TextView homeSts;
  private TextView testsHint;
  private String lastStsBus;
  private String lastStsMsg = "";
  private int lastStsOk;
  private int lastStsN = 14;
  private double lastBusV = Double.NaN;
  private TextView shutdownResult;
  private Button shutdownSend;
  private CheckBox shutdownConfirm;
  private boolean expectingHalt;
  private Button testEyesSteady;
  private Button testEyesBlink;
  private Button testProjector;
  private Button testAntWiggle;
  private Button testAntPulse;
  private GridLayout testSounds;
  private boolean eyesSteadyOn;
  private boolean eyesBlinkOn;
  private boolean projectorOn;
  private String lastSound = "";
  private int testsGen;

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
    TextView version = view.findViewById(R.id.home_version);
    version.setText(BuildConfig.VERSION_NAME);
    homeSts = view.findViewById(R.id.home_sts);
    paintHomeSts();
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
    testsGen++;
    View view = getLayoutInflater().inflate(R.layout.screen_tests, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.btn_back_home).setOnClickListener(v -> showScreen(Screen.HOME));
    testsHint = view.findViewById(R.id.tests_hint);
    testEyesSteady = view.findViewById(R.id.test_eyes_steady);
    testEyesBlink = view.findViewById(R.id.test_eyes_blink);
    testProjector = view.findViewById(R.id.test_projector);
    testAntWiggle = view.findViewById(R.id.test_antennas_wiggle);
    testAntPulse = view.findViewById(R.id.test_antennas_pulse);
    testSounds = view.findViewById(R.id.test_sounds);
    testEyesSteady.setOnClickListener(v -> sendTest("eyes_steady"));
    testEyesBlink.setOnClickListener(v -> sendTest("eyes_blink"));
    testProjector.setOnClickListener(v -> sendTest("projector"));
    testAntWiggle.setOnClickListener(v -> sendTest("antennas_wiggle"));
    testAntPulse.setOnClickListener(v -> sendTest("antennas_pulse"));
    populateSounds(FALLBACK_SOUNDS);
    paintTestControls();
    if (connected) {
      sendTest("list_sounds");
    }
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
    shutdownConfirm = view.findViewById(R.id.shutdown_confirm);
    shutdownSend = view.findViewById(R.id.shutdown_send);
    shutdownResult = view.findViewById(R.id.shutdown_result);
    shutdownSend.setEnabled(false);
    shutdownConfirm.setOnCheckedChangeListener(
        (box, checked) -> shutdownSend.setEnabled(checked && !expectingHalt));
    shutdownSend.setOnClickListener(v -> sendHalt());
    if (expectingHalt) {
      shutdownSend.setEnabled(false);
      shutdownResult.setText("Arrêt demandé — le lien BLE va tomber. Attendre que le Pi soit mort, puis couper l’alimentation.");
    }
  }

  private void sendHalt() {
    if (shutdownConfirm == null || !shutdownConfirm.isChecked()) {
      if (shutdownResult != null) {
        shutdownResult.setText("Coche d’abord la confirmation.");
      }
      return;
    }
    if (ble == null || !connected || !ble.isLinkReady()) {
      if (shutdownResult != null) {
        shutdownResult.setText("BLE non prêt — connecte d’abord (bandeau du haut).");
      }
      return;
    }
    try {
      JSONObject o = new JSONObject();
      o.put("type", "halt");
      o.put("v", 1);
      o.put("confirm", true);
      ble.sendNative(o.toString());
      expectingHalt = true;
      shutdownSend.setEnabled(false);
      if (shutdownResult != null) {
        shutdownResult.setText(
            "Arrêt demandé — le lien BLE va tomber. Attendre que le Pi soit mort, puis couper l’alimentation.");
      }
      refreshBleBanner();
    } catch (Exception e) {
      expectingHalt = false;
      if (shutdownResult != null) {
        shutdownResult.setText("Envoi impossible");
      }
    }
  }

  private void sendTest(String action) {
    sendTest(action, null);
  }

  private void sendTest(String action, String sound) {
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
      if (sound != null && !sound.isEmpty()) {
        o.put("sound", sound);
      }
      ble.sendNative(o.toString());
      markTestSent(action, sound);
      if (testsHint != null) {
        if (sound != null) {
          testsHint.setText("Lecture : " + soundLabel(sound));
        } else if (!"list_sounds".equals(action)) {
          testsHint.setText("Envoyé : " + action);
        }
      }
    } catch (Exception e) {
      if (testsHint != null) {
        testsHint.setText("Envoi impossible");
      }
    }
  }

  private void markTestSent(String action, String sound) {
    if ("eyes_steady".equals(action)) {
      eyesSteadyOn = !eyesSteadyOn;
      if (eyesSteadyOn) {
        eyesBlinkOn = false;
      }
    } else if ("eyes_blink".equals(action)) {
      eyesBlinkOn = !eyesBlinkOn;
      if (eyesBlinkOn) {
        eyesSteadyOn = false;
      }
    } else if ("projector".equals(action)) {
      projectorOn = !projectorOn;
    } else if ("speaker".equals(action) && sound != null) {
      lastSound = sound;
      int gen = testsGen;
      String played = sound;
      txHandler.postDelayed(
          () -> {
            if (gen == testsGen && current == Screen.TESTS && played.equals(lastSound)) {
              lastSound = "";
              paintSoundButtons();
            }
          },
          2000);
    } else if ("antennas_wiggle".equals(action) || "antennas_pulse".equals(action)) {
      paintAntenna(action, true);
      int gen = testsGen;
      txHandler.postDelayed(
          () -> {
            if (gen == testsGen && current == Screen.TESTS) {
              paintAntenna(action, false);
            }
          },
          2000);
      return;
    }
    paintTestControls();
  }

  private void populateSounds(String[] names) {
    if (testSounds == null) {
      return;
    }
    testSounds.removeAllViews();
    int pad = (int) (4 * getResources().getDisplayMetrics().density);
    for (String name : names) {
      if (name == null || name.isEmpty()) {
        continue;
      }
      Button b = new Button(this, null, 0, R.style.Bdx_Btn);
      b.setAllCaps(false);
      b.setText(soundLabel(name));
      b.setTag(name);
      b.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
      GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
      lp.width = 0;
      lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
      lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      lp.setMargins(pad, pad, pad, pad);
      b.setLayoutParams(lp);
      b.setOnClickListener(v -> sendTest("speaker", String.valueOf(v.getTag())));
      testSounds.addView(b);
    }
    paintSoundButtons();
  }

  private static String soundLabel(String name) {
    if (name.endsWith(".wav")) {
      return name.substring(0, name.length() - 4);
    }
    return name;
  }

  private void paintTestControls() {
    paintControl(testEyesSteady, eyesSteadyOn, R.drawable.btn_flat_blue);
    paintControl(testEyesBlink, eyesBlinkOn, R.drawable.btn_flat_blue);
    paintControl(testProjector, projectorOn, R.drawable.btn_flat_teal);
    paintSoundButtons();
  }

  private void paintAntenna(String action, boolean on) {
    if ("antennas_wiggle".equals(action)) {
      paintControl(testAntWiggle, on, R.drawable.btn_flat_gray);
      if (on) {
        paintControl(testAntPulse, false, R.drawable.btn_flat_gray);
      }
    } else if ("antennas_pulse".equals(action)) {
      paintControl(testAntPulse, on, R.drawable.btn_flat_gray);
      if (on) {
        paintControl(testAntWiggle, false, R.drawable.btn_flat_gray);
      }
    }
  }

  private void paintSoundButtons() {
    if (testSounds == null) {
      return;
    }
    for (int i = 0; i < testSounds.getChildCount(); i++) {
      View child = testSounds.getChildAt(i);
      if (child instanceof Button) {
        boolean on = lastSound.equals(child.getTag());
        paintControl((Button) child, on, R.drawable.btn_flat_green);
      }
    }
  }

  private void paintControl(Button button, boolean active, int idleBg) {
    if (button == null) {
      return;
    }
    if (active) {
      button.setBackgroundResource(R.drawable.btn_flat_yellow);
      button.setTextColor(ContextCompat.getColor(this, R.color.bs_dark));
    } else {
      button.setBackgroundResource(idleBg);
      button.setTextColor(ContextCompat.getColor(this, R.color.bs_light));
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
    homeSts = null;
    testsHint = null;
    shutdownResult = null;
    shutdownSend = null;
    shutdownConfirm = null;
    testEyesSteady = null;
    testEyesBlink = null;
    testProjector = null;
    testAntWiggle = null;
    testAntPulse = null;
    testSounds = null;
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
    } else if (expectingHalt) {
      bleBanner.setBackgroundResource(R.drawable.ble_banner_off);
      bleDot.setBackgroundResource(R.drawable.ble_dot_off);
      bleBannerText.setText("ROBOT ÉTEINT");
      bleBannerConnect.setText("Connecter");
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
    expectingHalt = false;
    refreshBleBanner();
    ble.connectNative(
        name ->
            runOnUiThread(
                () -> {
                  connecting = false;
                  connected = true;
                  refreshBleBanner();
                  refreshPilotStatus();
                  if (current == Screen.TESTS) {
                    sendTest("list_sounds");
                  }
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
    lastStsBus = null;
    lastStsMsg = "";
    lastBusV = Double.NaN;
    if (ble != null) {
      ble.disconnectNative();
    }
    refreshBleBanner();
    refreshPilotStatus();
    paintHomeSts();
  }

  private void onBleStatus(boolean isConnected) {
    runOnUiThread(
        () -> {
          connected = isConnected;
          if (!isConnected) {
            connecting = false;
            lastStsBus = null;
            lastStsMsg = "";
            lastBusV = Double.NaN;
          }
          refreshBleBanner();
          refreshPilotStatus();
          paintHomeSts();
        });
  }

  private void onBleRx(String text) {
    runOnUiThread(
        () -> {
          if (rxLine != null) {
            rxLine.setText(text);
          }
          applyStatusRx(text);
          applyTestRx(text);
        });
  }

  private void applyStatusRx(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    try {
      JSONObject o = new JSONObject(text);
      if (!"status".equals(o.optString("type"))) {
        return;
      }
      lastStsBus = o.optString("sts_bus", "down");
      lastStsOk = o.optInt("sts_ok", 0);
      lastStsN = o.optInt("sts_n", 14);
      lastStsMsg = o.optString("sts_msg", "");
      if (o.has("bus_v") && !o.isNull("bus_v")) {
        lastBusV = o.optDouble("bus_v");
      } else {
        lastBusV = Double.NaN;
      }
      paintHomeSts();
    } catch (Exception ignored) {
    }
  }

  private void paintHomeSts() {
    if (homeSts == null) {
      return;
    }
    int muted = ContextCompat.getColor(this, R.color.bs_muted);
    if (!connected || lastStsBus == null) {
      homeSts.setText("Bus STS —");
      homeSts.setTextColor(muted);
      return;
    }
    String volts =
        Double.isNaN(lastBusV)
            ? "—"
            : String.format(Locale.FRANCE, "%.1f V", lastBusV);
    if ("ok".equals(lastStsBus)) {
      homeSts.setText("STS OK · " + volts);
      homeSts.setTextColor(ContextCompat.getColor(this, R.color.bs_green));
    } else if ("partial".equals(lastStsBus)) {
      homeSts.setText("STS " + lastStsOk + "/" + lastStsN + " · " + volts);
      homeSts.setTextColor(ContextCompat.getColor(this, R.color.bs_yellow));
    } else {
      String why;
      if ("no_lib".equals(lastStsMsg)) {
        why = "bibliothèque absente";
      } else if ("no_port".equals(lastStsMsg)) {
        why = "pas d’adaptateur";
      } else if ("no_perm".equals(lastStsMsg)) {
        why = "accès série";
      } else if ("no_reply".equals(lastStsMsg)) {
        why = "servos muets";
      } else {
        why = "—";
      }
      homeSts.setText("STS hors bus · " + why);
      homeSts.setTextColor(ContextCompat.getColor(this, R.color.bs_red));
    }
  }

  private void applyTestRx(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    try {
      JSONObject o = new JSONObject(text);
      String type = o.optString("type");
      if ("halt_ack".equals(type)) {
        boolean accepted = o.optBoolean("accepted");
        expectingHalt = accepted;
        if (shutdownResult != null) {
          String message = o.optString("message");
          if (accepted) {
            shutdownResult.setText(
                (message.isEmpty() ? "Arrêt demandé" : message)
                    + " Attendre que le Pi soit mort, puis couper l’alimentation.");
          } else {
            shutdownResult.setText(
                message.isEmpty() ? "Arrêt refusé" : message);
            if (shutdownSend != null && shutdownConfirm != null) {
              shutdownSend.setEnabled(shutdownConfirm.isChecked());
            }
          }
        }
        refreshBleBanner();
        return;
      }
      if ("test_catalog".equals(type)) {
        JSONArray arr = o.optJSONArray("sounds");
        if (arr != null && arr.length() > 0) {
          String[] names = new String[arr.length()];
          for (int i = 0; i < arr.length(); i++) {
            names[i] = arr.optString(i);
          }
          populateSounds(names);
        }
        if (testsHint != null) {
          int n = arr == null ? 0 : arr.length();
          testsHint.setText(n > 0 ? n + " sons disponibles" : "Aucun son sur le robot");
        }
        return;
      }
      if ("test_state".equals(type)) {
        String action = o.optString("action");
        boolean active = o.optBoolean("active");
        if ("eyes_steady".equals(action)) {
          eyesSteadyOn = active;
          if (active) {
            eyesBlinkOn = false;
          }
        } else if ("eyes_blink".equals(action)) {
          eyesBlinkOn = active;
          if (active) {
            eyesSteadyOn = false;
          }
        } else if ("projector".equals(action)) {
          projectorOn = active;
        } else if ("speaker".equals(action)) {
          String sound = o.optString("sound");
          if (!sound.isEmpty()) {
            lastSound = sound;
          }
        } else if ("antennas_wiggle".equals(action) || "antennas_pulse".equals(action)) {
          paintAntenna(action, active);
        }
        paintTestControls();
        if (testsHint != null) {
          String message = o.optString("message");
          if (!message.isEmpty()) {
            testsHint.setText(message);
          }
        }
        return;
      }
      if ("log".equals(type) && testsHint != null) {
        String message = o.optString("message");
        if (!message.isEmpty()
            && (message.contains("Yeux")
                || message.contains("Projecteur")
                || message.contains("Haut-parleur")
                || message.contains("Antennes")
                || message.contains("son"))) {
          testsHint.setText(message);
        }
      }
    } catch (Exception ignored) {
      if (testsHint != null
          && (text.contains("Yeux")
              || text.contains("Projecteur")
              || text.contains("Haut-parleur")
              || text.contains("Antennes"))) {
        testsHint.setText(text);
      }
    }
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
