import { STATUS_CONFIG } from '../utils/helpers';
import { Check, X, Loader2 } from 'lucide-react';

// ── Status Badge ──
export function StatusBadge({ status }) {
  const s = STATUS_CONFIG[status] || { label: status, color: 'text-gray-500', bg: 'bg-gray-100', dot: 'bg-gray-400' };
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ${s.color} ${s.bg}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${s.dot}`} />
      {s.label}
    </span>
  );
}

// ── Kafka Pipeline Tracker ──
export function PipelineTracker({ status }) {
  const stages = [
    { key: 'PENDING',    label: 'Oluşturuldu', service: 'Gateway' },
    { key: 'VALIDATED',  label: 'Doğrulandı',  service: 'Service A' },
    { key: 'CHECKED',    label: 'Fraud Kontrol', service: 'Service B' },
    { key: 'PROCESSED',  label: 'Bakiye',      service: 'Service C' },
    { key: 'COMPLETED',  label: 'Bildirim',    service: 'Service D' },
  ];

  const stageOrder = stages.map(s => s.key);
  const currentIdx = stageOrder.indexOf(status);
  const isBlocked = status === 'BLOCKED' || status === 'FAILED';

  return (
    <div className="flex items-center w-full">
      {stages.map((stage, i) => {
        const done = currentIdx >= i;
        const active = currentIdx === i;
        const blocked = isBlocked && i === 2;

        return (
          <div key={stage.key} className={`flex items-center ${i < stages.length - 1 ? 'flex-1' : ''}`}>
            <div className="flex flex-col items-center gap-1">
              <div
                className={`w-7 h-7 rounded-full flex items-center justify-center transition-all duration-300
                  ${blocked ? 'bg-red-500' : done ? 'bg-emerald-500' : 'bg-slate-200'}
                  ${active ? 'ring-2 ring-blue-500 ring-offset-2' : ''}`}
              >
                {blocked ? <X className="w-3.5 h-3.5 text-white" /> :
                  done ? <Check className="w-3.5 h-3.5 text-white" /> :
                  <span className="text-[10px] font-bold text-slate-400">{i + 1}</span>}
              </div>
              <span className={`text-[10px] font-semibold whitespace-nowrap ${done ? 'text-slate-700' : 'text-slate-400'}`}>
                {stage.label}
              </span>
              <span className="text-[9px] text-slate-400 font-mono">{stage.service}</span>
            </div>
            {i < stages.length - 1 && (
              <div className={`flex-1 h-0.5 mx-2 mb-7 transition-colors duration-300 ${currentIdx > i ? 'bg-emerald-500' : 'bg-slate-200'}`} />
            )}
          </div>
        );
      })}
    </div>
  );
}

// ── Stat Card ──
export function StatCard({ title, value, sub, icon: Icon, trend, trendLabel }) {
  return (
    <div className="bg-white rounded-xl p-5 border border-slate-200 relative overflow-hidden group hover:border-slate-300 transition-colors">
      <div className="absolute -top-5 -right-5 w-20 h-20 rounded-full bg-blue-500/5 group-hover:bg-blue-500/10 transition-colors" />
      <div className="relative">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">{title}</p>
            <p className="text-2xl font-extrabold text-slate-900 tracking-tight">{value}</p>
            {sub && <p className="text-xs text-slate-400 mt-1.5">{sub}</p>}
          </div>
          {trend !== undefined && (
            <div className={`flex items-center gap-1 px-2 py-1 rounded-md text-xs font-semibold
              ${trend > 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'}`}>
              {trend > 0 ? '↑' : '↓'} {Math.abs(trend)}%
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Loading State ──
export function LoadingState({ message = 'Yükleniyor...' }) {
  return (
    <div className="flex items-center justify-center py-16">
      <div className="flex flex-col items-center gap-3">
        <Loader2 className="w-8 h-8 text-blue-600 animate-spin" />
        <span className="text-sm text-slate-400 font-medium">{message}</span>
      </div>
    </div>
  );
}

// ── Empty State ──
export function EmptyState({ title = 'Veri bulunamadı', description }) {
  return (
    <div className="text-center py-12">
      <p className="text-slate-500 font-medium">{title}</p>
      {description && <p className="text-sm text-slate-400 mt-1">{description}</p>}
    </div>
  );
}
