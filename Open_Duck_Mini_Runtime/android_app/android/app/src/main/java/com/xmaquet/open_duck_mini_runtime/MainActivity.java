package com.xmaquet.open_duck_mini_runtime;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends BridgeActivity {
  private static final int REQ_BLE = 42;

  private enum Screen {
    HOME,
    PILOT,
    TESTS,
    SETTINGS,
    WIFI,
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

  private static final int[] STS_BADGE_IDS = {
    10, 11, 12, 13, 14, 20, 21, 22, 23, 24, 30, 31, 32, 33
  };

  private RobotBlePlugin ble;
  private TextView statusView;
  private TextView rxLine;
  private TextView homeSts;
  private LinearLayout homeStsBadges;
  private TextView testsHint;
  private String lastStsBus;
  private String lastStsMsg = "";
  private int lastStsOk;
  private int lastStsN = 14;
  private double lastBusV = Double.NaN;
  private final boolean[] lastStsAlive = new boolean[STS_BADGE_IDS.length];
  private boolean lastStsAliveKnown;
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

  private TextView wifiHint;
  private TextView wifiSsidView;
  private TextView wifiStateView;
  private TextView wifiIpView;
  private LinearLayout wifiList;
  private Button wifiScan;
  private boolean wifiScanning;
  private int wifiScanGen;
  private boolean wifiGotRx;
  private String lastWifiSsid;
  private String lastWifiConnState;
  private String lastWifiIp;
  private int lastWifiRssi = Integer.MIN_VALUE;
  private String lastWifiMessage = "";
  private final ArrayList<JSONObject> lastWifiNets = new ArrayList<>();

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
    if (current == Screen.WIFI) {
      showScreen(Screen.SETTINGS);
      return;
    }
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
      case SETTINGS:
        bindSettings();
        break;
      case WIFI:
        bindWifi();
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
    view.findViewById(R.id.home_video).setOnClickListener(v -> showScreen(Screen.VIDEO));
    view.findViewById(R.id.home_settings).setOnClickListener(v -> showScreen(Screen.SETTINGS));
    view.findViewById(R.id.home_shutdown).setOnClickListener(v -> showScreen(Screen.SHUTDOWN));
    TextView version = view.findViewById(R.id.home_version);
    version.setText(BuildConfig.VERSION_NAME);
    homeSts = view.findViewById(R.id.home_sts);
    homeStsBadges = view.findViewById(R.id.home_sts_badges);
    ensureStsBadges();
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

  private void bindSettings() {
    View view = getLayoutInflater().inflate(R.layout.screen_settings, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.btn_back_home).setOnClickListener(v -> showScreen(Screen.HOME));
    view.findViewById(R.id.settings_wifi).setOnClickListener(v -> showScreen(Screen.WIFI));
  }

  private void bindWifi() {
    View view = getLayoutInflater().inflate(R.layout.screen_wifi, screenContainer, false);
    screenContainer.addView(view);
    view.findViewById(R.id.btn_back_home).setOnClickListener(v -> showScreen(Screen.SETTINGS));
    wifiHint = view.findViewById(R.id.wifi_hint);
    wifiSsidView = view.findViewById(R.id.wifi_ssid);
    wifiStateView = view.findViewById(R.id.wifi_state);
    wifiIpView = view.findViewById(R.id.wifi_ip);
    wifiList = view.findViewById(R.id.wifi_list);
    wifiScan = view.findViewById(R.id.wifi_scan);
    wifiScan.setOnClickListener(v -> sendWifi("scan"));
    paintWifi();
    if (connected) {
      sendWifi("status");
      txHandler.postDelayed(
          () -> {
            if (current == Screen.WIFI && connected) {
              sendWifi("scan");
            }
          },
          400);
    } else if (wifiHint != null) {
      wifiHint.setText("BLE non prêt — connecte d’abord (bandeau du haut).");
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

  private void sendWifi(String action) {
    if (ble == null || !connected || !ble.isLinkReady()) {
      if (wifiHint != null) {
        wifiHint.setText("BLE non prêt — connecte d’abord (bandeau du haut).");
      }
      return;
    }
    try {
      JSONObject o = new JSONObject();
      o.put("type", "wifi");
      o.put("v", 1);
      o.put("action", action);
      ble.sendNative(o.toString());
      if ("scan".equals(action)) {
        wifiScanning = true;
        wifiGotRx = false;
        final int gen = ++wifiScanGen;
        if (wifiScan != null) {
          wifiScan.setEnabled(false);
          wifiScan.setText("Scan…");
        }
        if (wifiHint != null) {
          wifiHint.setText("Recherche des réseaux 2,4 GHz…");
        }
        txHandler.postDelayed(
            () -> {
              if (wifiScanning && gen == wifiScanGen) {
                wifiScanning = false;
                if (lastWifiNets.isEmpty()) {
                  lastWifiMessage =
                      wifiGotRx
                          ? "Scan terminé, aucun réseau 2,4 GHz listé."
                          : "Pas de réponse Wi‑Fi du robot. Firmware ou sudoers pas encore à jour ?";
                }
                paintWifi();
              }
            },
            40000);
      } else if (wifiHint != null) {
        wifiHint.setText("Demande d’état envoyée.");
      }
    } catch (Exception e) {
      if (wifiHint != null) {
        wifiHint.setText("Envoi impossible");
      }
    }
  }

  private void sendWifiJoin(String ssid, String psk) {
    if (ble == null || !connected || !ble.isLinkReady()) {
      if (wifiHint != null) {
        wifiHint.setText("BLE non prêt — connecte d’abord (bandeau du haut).");
      }
      return;
    }
    try {
      JSONObject o = new JSONObject();
      o.put("type", "wifi");
      o.put("v", 1);
      o.put("action", "join");
      o.put("ssid", ssid);
      o.put("confirm", true);
      if (psk != null && !psk.isEmpty()) {
        o.put("psk", psk);
      }
      ble.sendNative(o.toString());
      lastWifiConnState = "connecting";
      lastWifiSsid = ssid;
      lastWifiMessage = "Association demandée…";
      paintWifi();
    } catch (Exception e) {
      if (wifiHint != null) {
        wifiHint.setText("Envoi impossible");
      }
    }
  }

  private void confirmJoin(String ssid, boolean needsPsk) {
    AlertDialog.Builder b = new AlertDialog.Builder(this);
    b.setTitle(ssid);
    final EditText passwordField;
    if (needsPsk) {
      b.setMessage("Mot de passe du réseau. Il n’est pas stocké sur la tablette.");
      LinearLayout box = new LinearLayout(this);
      box.setOrientation(LinearLayout.VERTICAL);
      int pad = Math.round(20 * getResources().getDisplayMetrics().density);
      box.setPadding(pad, pad / 2, pad, 0);
      EditText password = new EditText(this);
      password.setHint("Mot de passe");
      password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      password.setTextColor(ContextCompat.getColor(this, R.color.bs_dark));
      password.setHintTextColor(ContextCompat.getColor(this, R.color.bs_muted));
      box.addView(password);
      b.setView(box);
      passwordField = password;
    } else {
      b.setMessage("Réseau ouvert. Confirmer l’association ?");
      passwordField = null;
    }
    b.setNegativeButton("Annuler", null);
    b.setPositiveButton(
        "Connecter",
        (d, w) -> {
          String pass = passwordField == null ? "" : passwordField.getText().toString();
          sendWifiJoin(ssid, pass);
        });
    b.show();
  }

  private void paintWifi() {
    if (wifiSsidView != null) {
      wifiSsidView.setText(
          lastWifiSsid == null || lastWifiSsid.isEmpty() ? "Réseau —" : lastWifiSsid);
    }
    if (wifiStateView != null) {
      wifiStateView.setText("État · " + wifiStateLabel(lastWifiConnState));
    }
    if (wifiIpView != null) {
      String ip = lastWifiIp == null || lastWifiIp.isEmpty() ? "—" : lastWifiIp;
      String sig =
          lastWifiRssi == Integer.MIN_VALUE ? "—" : String.valueOf(lastWifiRssi);
      wifiIpView.setText("IP " + ip + "  ·  signal " + sig);
    }
    if (wifiHint != null && lastWifiMessage != null && !lastWifiMessage.isEmpty()) {
      wifiHint.setText(lastWifiMessage);
    }
    if (wifiScan != null) {
      wifiScan.setEnabled(!wifiScanning && connected);
      wifiScan.setText(wifiScanning ? "Scan…" : "Actualiser la liste");
    }
    paintWifiList();
  }

  private static String wifiStateLabel(String state) {
    if ("connected".equals(state)) {
      return "connecté";
    }
    if ("connecting".equals(state)) {
      return "connexion…";
    }
    if ("failed".equals(state)) {
      return "échec";
    }
    if ("disconnected".equals(state)) {
      return "coupé";
    }
    return "—";
  }

  private void paintWifiList() {
    if (wifiList == null) {
      return;
    }
    wifiList.removeAllViews();
    int gap = Math.round(8 * getResources().getDisplayMetrics().density);
    for (int i = 0; i < lastWifiNets.size(); i++) {
      JSONObject net = lastWifiNets.get(i);
      String ssid = net.optString("ssid");
      if (ssid.isEmpty()) {
        continue;
      }
      View row = getLayoutInflater().inflate(R.layout.item_wifi_net, wifiList, false);
      TextView name = row.findViewById(R.id.wifi_net_ssid);
      TextView meta = row.findViewById(R.id.wifi_net_meta);
      TextView badge = row.findViewById(R.id.wifi_net_badge);
      name.setText(ssid);
      boolean psk = "psk".equals(net.optString("sec"));
      int rssi = net.optInt("rssi", 0);
      meta.setText((psk ? "Sécurisé" : "Ouvert") + " · signal " + rssi);
      boolean inUse = net.optBoolean("in_use");
      badge.setVisibility(inUse ? View.VISIBLE : View.GONE);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      if (i > 0) {
        lp.topMargin = gap;
      }
      row.setLayoutParams(lp);
      row.setOnClickListener(v -> confirmJoin(ssid, psk));
      wifiList.addView(row);
    }
  }

  private void applyWifiRx(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    try {
      JSONObject o = new JSONObject(text);
      String type = o.optString("type");
      if (type.startsWith("wifi_")) {
        wifiGotRx = true;
      }
      if ("wifi_ack".equals(type)) {
        String message = o.optString("message");
        boolean accepted = o.optBoolean("accepted");
        if (!message.isEmpty()) {
          lastWifiMessage = message;
        }
        if ("scan".equals(o.optString("action")) && !accepted) {
          wifiScanning = false;
        }
        paintWifi();
        return;
      }
      if ("wifi_state".equals(type)) {
        lastWifiSsid = o.isNull("ssid") ? null : o.optString("ssid", null);
        lastWifiConnState = o.optString("state", "");
        lastWifiIp = o.isNull("ip") ? null : o.optString("ip", null);
        if (o.has("rssi") && !o.isNull("rssi")) {
          lastWifiRssi = o.optInt("rssi");
        } else {
          lastWifiRssi = Integer.MIN_VALUE;
        }
        lastWifiMessage = o.optString("message", lastWifiMessage);
        paintWifi();
        return;
      }
      if ("wifi_scan".equals(type)) {
        int i = o.optInt("i", 0);
        int n = o.optInt("n", 1);
        if (i == 0) {
          lastWifiNets.clear();
        }
        JSONArray nets = o.optJSONArray("nets");
        if (nets != null) {
          for (int k = 0; k < nets.length(); k++) {
            JSONObject net = nets.optJSONObject(k);
            if (net != null) {
              lastWifiNets.add(net);
            }
          }
        }
        if (i >= n - 1) {
          wifiScanning = false;
          lastWifiMessage = lastWifiNets.isEmpty()
              ? "Aucun réseau 2,4 GHz visible."
              : lastWifiNets.size() + " réseaux";
        }
        paintWifi();
      }
    } catch (Exception ignored) {
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
    homeStsBadges = null;
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
    wifiHint = null;
    wifiSsidView = null;
    wifiStateView = null;
    wifiIpView = null;
    wifiList = null;
    wifiScan = null;
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
                  if (current == Screen.WIFI) {
                    sendWifi("status");
                    sendWifi("scan");
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
    lastStsAliveKnown = false;
    lastBusV = Double.NaN;
    wifiScanning = false;
    lastWifiMessage = "BLE déconnecté.";
    if (ble != null) {
      ble.disconnectNative();
    }
    refreshBleBanner();
    refreshPilotStatus();
    paintHomeSts();
    paintWifi();
  }

  private void onBleStatus(boolean isConnected) {
    runOnUiThread(
        () -> {
          connected = isConnected;
          if (!isConnected) {
            connecting = false;
            lastStsBus = null;
            lastStsMsg = "";
            lastStsAliveKnown = false;
            lastBusV = Double.NaN;
            wifiScanning = false;
            lastWifiMessage = "BLE déconnecté.";
          }
          refreshBleBanner();
          refreshPilotStatus();
          paintHomeSts();
          paintWifi();
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
          applyWifiRx(text);
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
      lastStsAliveKnown = false;
      for (int i = 0; i < lastStsAlive.length; i++) {
        lastStsAlive[i] = false;
      }
      JSONArray servos = o.optJSONArray("sts");
      if (servos != null) {
        lastStsAliveKnown = true;
        for (int i = 0; i < servos.length(); i++) {
          JSONObject s = servos.optJSONObject(i);
          if (s == null) {
            continue;
          }
          int id = s.optInt("id");
          boolean ok = s.optBoolean("ok");
          for (int j = 0; j < STS_BADGE_IDS.length; j++) {
            if (STS_BADGE_IDS[j] == id) {
              lastStsAlive[j] = ok;
              break;
            }
          }
        }
      }
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
      paintStsBadges();
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
    paintStsBadges();
  }

  private void ensureStsBadges() {
    if (homeStsBadges == null || homeStsBadges.getChildCount() > 0) {
      return;
    }
    float d = getResources().getDisplayMetrics().density;
    int padH = Math.round(6 * d);
    int padV = Math.round(2 * d);
    int gap = Math.round(4 * d);
    for (int id : STS_BADGE_IDS) {
      TextView b = new TextView(this);
      b.setText(String.valueOf(id));
      b.setGravity(android.view.Gravity.CENTER);
      b.setTextSize(12);
      b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
      b.setIncludeFontPadding(false);
      b.setPadding(padH, padV, padH, padV);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.setMargins(0, 0, gap, 0);
      b.setLayoutParams(lp);
      b.setTag(id);
      homeStsBadges.addView(b);
    }
  }

  private void paintStsBadges() {
    if (homeStsBadges == null) {
      return;
    }
    int light = ContextCompat.getColor(this, R.color.bs_light);
    int muted = ContextCompat.getColor(this, R.color.bs_muted);
    boolean live = connected && lastStsAliveKnown;
    for (int i = 0; i < homeStsBadges.getChildCount(); i++) {
      View child = homeStsBadges.getChildAt(i);
      if (!(child instanceof TextView)) {
        continue;
      }
      TextView b = (TextView) child;
      if (!live) {
        b.setBackgroundResource(R.drawable.badge_sts_idle);
        b.setTextColor(muted);
        continue;
      }
      int idx = -1;
      Object tag = b.getTag();
      if (tag instanceof Integer) {
        int id = (Integer) tag;
        for (int j = 0; j < STS_BADGE_IDS.length; j++) {
          if (STS_BADGE_IDS[j] == id) {
            idx = j;
            break;
          }
        }
      }
      boolean ok = idx >= 0 && lastStsAlive[idx];
      b.setBackgroundResource(ok ? R.drawable.badge_sts_ok : R.drawable.badge_sts_ko);
      b.setTextColor(light);
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
