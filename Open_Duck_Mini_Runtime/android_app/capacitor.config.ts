import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.xmaquet.open_duck_mini_runtime',
  appName: 'BDXv2',
  webDir: 'www',
  bundledWebRuntime: false,
  server: {
    // Origine Capacitor par défaut. https://bdxv2 ne termine jamais le
    // chargement sur WebView 150 (evaluateJavascript ne s’exécute pas).
    androidScheme: 'https',
    hostname: 'localhost',
  },
  android: {
    allowMixedContent: true,
    webContentsDebuggingEnabled: true,
  },
};

export default config;

