import { useState, useCallback, useEffect, useMemo } from 'react';
import { BluetoothManager } from './components/BluetoothManager';
import { LogViewer } from './components/LogViewer';
import { FrameDump } from './components/FrameDump';
import type { LogLevel, TransportStatus, UiControllerState } from '../transport/types';
import { uiToControllerFrame } from '../transport/convert';
import { AndroidBleTransport } from '../transport/androidBleTransport';
import { MockTransport } from '../transport/mockTransport';
import type { Transport } from '../transport/transport';
import { Capacitor } from '@capacitor/core';

interface LogEntry {
  timestamp: string;
  message: string;
  type: LogLevel;
}

const MAX_LOGS = 150;

const NEUTRAL_UI: UiControllerState = {
  leftStick: { x: 0, y: 0 },
  rightStick: { x: 0, y: 0 },
  buttons: {
    A: false,
    B: false,
    X: false,
    Y: false,
    LB: false,
    RB: false,
    Start: false,
    Select: false,
  },
  dpad: { up: false, down: false, left: false, right: false },
  triggers: { LT: 0, RT: 0 },
};

const INITIAL_STATUS: TransportStatus = { connected: false, mode: 'simulation' };

export default function App() {
  const [status, setStatus] = useState<TransportStatus>(INITIAL_STATUS);
  const [connecting, setConnecting] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [lastTx, setLastTx] = useState<string | null>(null);
  const [lastRx, setLastRx] = useState<string | null>(null);

  const addLog = useCallback((message: string, type: LogEntry['type'] = 'info') => {
    const timestamp = new Date().toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    setLogs((prev) => {
      const next = [...prev, { timestamp, message, type }];
      return next.length > MAX_LOGS ? next.slice(-MAX_LOGS) : next;
    });
  }, []);

  const canUseNativeBle = Capacitor.isNativePlatform();

  const transport: Transport = useMemo(() => {
    return canUseNativeBle ? new AndroidBleTransport() : new MockTransport();
  }, [canUseNativeBle]);

  useEffect(() => {
    document.getElementById('boot')?.remove();
  }, []);

  useEffect(() => {
    setStatus(transport.getStatus());
    transport.setListener((event) => {
      if (event.type === 'status') {
        setStatus(event.status);
      } else if (event.type === 'log') {
        addLog(event.log.message, event.log.level);
      } else if (event.type === 'rx') {
        setLastRx(event.text);
      }
    });
    return () => transport.setListener(null);
  }, [transport, addLog]);

  const handleConnect = useCallback(async () => {
    setConnecting(true);
    try {
      await transport.connect();
    } finally {
      setConnecting(false);
    }
  }, [transport]);

  const handleDisconnect = useCallback(async () => {
    await transport.disconnect();
    setLastTx(null);
    setLastRx(null);
  }, [transport]);

  const handleClearLogs = useCallback(() => {
    setLogs([]);
  }, []);

  useEffect(() => {
    let seq = 0;
    const interval = setInterval(() => {
      if (!transport.getStatus().connected) return;
      const frame = uiToControllerFrame(NEUTRAL_UI, seq++);
      if (frame.seq % 5 === 0) {
        setLastTx(JSON.stringify(frame, null, 2));
      }
      transport.send(frame).catch((err) => {
        addLog(`Erreur transport: ${err instanceof Error ? err.message : String(err)}`, 'error');
      });
    }, 50);
    return () => clearInterval(interval);
  }, [transport, addLog]);

  const txSubtitle = lastTx
    ? `ControllerFrame v1 · ~20 Hz · ${status.connected ? 'envoi actif' : 'arrêté'}`
    : 'Aucune trame envoyée';

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-950 to-gray-900 text-white flex flex-col">
      <BluetoothManager
        status={status}
        connecting={connecting}
        canUseNativeBle={canUseNativeBle}
        onConnect={handleConnect}
        onDisconnect={handleDisconnect}
      />

      <div className="px-3 sm:px-4 pt-3 text-xs text-gray-500">
        Pas de manette Xbox. Lien BLE : dump TX (tablette → robot) et RX (robot → tablette).
        {status.lastError ? (
          <span className="ml-2 text-red-400">Erreur : {status.lastError}</span>
        ) : null}
      </div>

      <div className="flex-1 flex flex-col md:flex-row gap-3 sm:gap-4 p-2 sm:p-3 md:p-4 min-h-0">
        <div className="flex-1 flex flex-col gap-3 min-h-[280px] md:min-h-0">
          <FrameDump
            title="TX — tablette → robot"
            subtitle={txSubtitle}
            payload={lastTx}
            emptyHint="Connecte le BLE pour envoyer des trames neutres (seq, axes à 0)."
          />
          <FrameDump
            title="RX — robot → tablette"
            subtitle={lastRx ? 'Dernier JSON notify GATT' : 'En attente d’un notify'}
            payload={lastRx}
            emptyHint="Le Pi doit renvoyer un JSON type log après réception TX (bdx-ble-robot)."
          />
        </div>

        <div className="flex-1 min-h-[220px] md:min-h-0">
          <LogViewer logs={logs} onClearLogs={handleClearLogs} live={status.connected} />
        </div>
      </div>
    </div>
  );
}
