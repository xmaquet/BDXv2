import { Bluetooth, BluetoothConnected, Info, Wifi } from 'lucide-react';
import { Button } from './ui/button';
import type { TransportStatus } from '../../transport/types';

interface BluetoothManagerProps {
  status: TransportStatus;
  connecting: boolean;
  canUseNativeBle: boolean;
  onConnect: () => Promise<void>;
  onDisconnect: () => Promise<void>;
}

export function BluetoothManager({
  status,
  connecting,
  canUseNativeBle,
  onConnect,
  onDisconnect,
}: BluetoothManagerProps) {
  const isConnected = status.connected;
  const simulationMode = status.mode === 'simulation' && isConnected;

  const handleConnect = async () => {
    try {
      await onConnect();
    } catch {
      // L’erreur est déjà remontée via le transport (logs + lastError).
    }
  };

  return (
    <div className="flex items-center justify-between gap-4 p-4 bg-gray-900 border-b-2 border-gray-700">
      <div className="flex items-center gap-3">
        <div className="w-12 h-12 bg-gray-800 border-2 border-gray-600 rounded-lg flex items-center justify-center">
          <span className="text-2xl">🤖</span>
        </div>
        <div className="flex flex-col">
          <span className="text-sm font-bold text-gray-300">BDXv2</span>
          <span className="text-xs text-gray-500">Lot 3a — dump TX / RX</span>
        </div>
      </div>

      <div className="flex flex-col items-center gap-2">
        <div className="flex items-center gap-3">
          {isConnected ? (
            simulationMode ? (
              <Wifi className="w-6 h-6 text-purple-400" />
            ) : (
              <BluetoothConnected className="w-6 h-6 text-blue-400" />
            )
          ) : (
            <Bluetooth className="w-6 h-6 text-gray-400" />
          )}

          {isConnected ? (
            <Button onClick={() => void onDisconnect()} className="bg-gray-700 hover:bg-gray-600">
              Déconnecter
            </Button>
          ) : (
            <Button
              onClick={() => void handleConnect()}
              disabled={connecting}
              className={
                canUseNativeBle
                  ? 'bg-blue-600 hover:bg-blue-700 disabled:bg-gray-700'
                  : 'bg-purple-600 hover:bg-purple-700 disabled:bg-gray-700'
              }
            >
              {connecting
                ? 'Recherche…'
                : canUseNativeBle
                  ? 'Connecter BLE'
                  : 'Mode simulation'}
            </Button>
          )}
        </div>

        {isConnected && status.deviceName && (
          <span className="text-xs text-green-400">
            {simulationMode ? '🔧 ' : '📡 '}
            {status.deviceName}
          </span>
        )}
        {connecting && !isConnected && (
          <span className="text-xs text-yellow-400">Scan du service GATT (jusqu’à 10 s)…</span>
        )}
      </div>

      <div className="flex items-center gap-2 min-w-[8rem] justify-end">
        {simulationMode && (
          <div className="flex items-center gap-1.5 px-2 py-1 bg-purple-950/50 border border-purple-700 rounded-md">
            <Info className="w-3 h-3 text-purple-400" />
            <span className="text-xs text-purple-300">Simulation</span>
          </div>
        )}
        {!canUseNativeBle && !isConnected && (
          <div className="flex items-center gap-1.5 px-2 py-1 bg-blue-950/50 border border-blue-700 rounded-md">
            <Info className="w-3 h-3 text-blue-400" />
            <span className="text-xs text-blue-300">BLE natif : APK tablette</span>
          </div>
        )}
      </div>
    </div>
  );
}
