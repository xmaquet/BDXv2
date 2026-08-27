interface FrameDumpProps {
  title: string;
  subtitle: string;
  payload: string | null;
  emptyHint: string;
}

export function FrameDump({ title, subtitle, payload, emptyHint }: FrameDumpProps) {
  return (
    <div className="flex-1 min-h-[160px] bg-gray-950 border-2 border-gray-700 rounded-lg flex flex-col overflow-hidden">
      <div className="px-3 py-2 bg-gray-900 border-b border-gray-700">
        <div className="text-sm font-semibold text-gray-300">{title}</div>
        <div className="text-xs text-gray-500">{subtitle}</div>
      </div>
      <pre className="flex-1 overflow-auto p-3 font-mono text-[11px] sm:text-xs text-gray-300 whitespace-pre-wrap break-all">
        {payload ?? emptyHint}
      </pre>
    </div>
  );
}
