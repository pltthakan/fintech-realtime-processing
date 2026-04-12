import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { reportService } from '../services/api';
import { StatCard, PipelineTracker, StatusBadge, LoadingState } from '../components/ui';
import { formatMoney, formatDate, TX_TYPE_CONFIG } from '../utils/helpers';
import { RefreshCw } from 'lucide-react';

export default function DashboardPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await reportService.getDashboard();
      setData(res.data.data);
    } catch (err) {
      console.error('Dashboard fetch error:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  if (loading) return <LoadingState message="Dashboard yükleniyor..." />;
  if (!data) return <div className="text-center py-16 text-slate-400">Veri yüklenemedi</div>;

  return (
    <div className="animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-7">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Dashboard</h1>
          <p className="text-sm text-slate-500 mt-1">Sistem genel durumu ve son işlemler</p>
        </div>
        <button onClick={fetchData}
          className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg border border-slate-200 bg-white text-slate-500 text-sm font-medium hover:bg-slate-50 transition-colors cursor-pointer">
          <RefreshCw className="w-3.5 h-3.5" /> Yenile
        </button>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        <StatCard title="Toplam İşlem" value={data.totalTransactions?.toLocaleString()} sub={`${data.completedTransactions} tamamlandı`} trend={12} />
        <StatCard title="Toplam Hacim" value={formatMoney(data.totalVolume)} sub={`Ort: ${formatMoney(data.averageAmount)}`} trend={8} />
        <StatCard title="Engellenen" value={data.blockedTransactions} sub={`${data.suspiciousTransactions} şüpheli`} />
        <StatCard title="Ort. Süre" value={`${data.averageProcessingTimeMs?.toFixed(0) || 0}ms`} sub="Pipeline süresi" trend={-5} />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <MiniBarChart title="İşlem Türü Dağılımı" data={data.transactionsByType}
          colorMap={{ TRANSFER: '#2563eb', PAYMENT: '#8b5cf6', DEPOSIT: '#10b981', WITHDRAWAL: '#f59e0b' }} />
        <MiniBarChart title="Para Birimi Dağılımı" data={data.transactionsByCurrency}
          colorMap={{ TRY: '#2563eb', USD: '#10b981', EUR: '#8b5cf6', GBP: '#f59e0b' }} />
      </div>

      {/* Pipeline Status */}
      <div className="bg-white rounded-xl p-6 border border-slate-200 mb-6">
        <h3 className="text-sm font-bold text-slate-900 mb-1">Kafka Pipeline Durumu</h3>
        <p className="text-xs text-slate-400 mb-5">Son işlemin pipeline üzerindeki ilerleyişi</p>
        <PipelineTracker status={data.recentTransactions?.[0]?.status || 'PENDING'} />
      </div>

      {/* Recent Transactions Table */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <h3 className="text-sm font-bold text-slate-900">Son İşlemler</h3>
          <button onClick={() => navigate('/transactions')}
            className="text-xs text-blue-600 font-semibold hover:underline cursor-pointer">Tümünü Gör →</button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50">
                {['İşlem ID', 'Tür', 'Tutar', 'Durum', 'Fraud', 'Süre', 'Tarih'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(data.recentTransactions || []).map((tx, i) => {
                const typeConf = TX_TYPE_CONFIG[tx.type];
                return (
                  <tr key={i} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs text-blue-600">{tx.transactionId?.slice(0, 8)}...</td>
                    <td className="px-4 py-3">
                      <span className="flex items-center gap-1">
                        <span className="text-sm">{typeConf?.icon}</span>
                        <span className="text-slate-700">{typeConf?.label}</span>
                      </span>
                    </td>
                    <td className="px-4 py-3 font-bold font-mono text-[13px]">{formatMoney(tx.amount, tx.currency)}</td>
                    <td className="px-4 py-3"><StatusBadge status={tx.status} /></td>
                    <td className="px-4 py-3">
                      <span className={`font-mono text-xs font-semibold
                        ${tx.fraudScore > 50 ? 'text-red-600' : tx.fraudScore > 25 ? 'text-amber-600' : 'text-emerald-600'}`}>
                        {tx.fraudScore}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-400">
                      {tx.totalProcessingTimeMs ? `${tx.totalProcessingTimeMs}ms` : '—'}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-400 whitespace-nowrap">
                      {formatDate(tx.completedTimestamp || tx.rawTimestamp)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

// ── Mini Bar Chart ──
function MiniBarChart({ title, data, colorMap }) {
  if (!data) return null;
  const max = Math.max(...Object.values(data), 1);

  return (
    <div className="bg-white rounded-xl p-5 border border-slate-200">
      <h3 className="text-sm font-bold text-slate-900 mb-4">{title}</h3>
      <div className="flex items-end gap-2 h-24 px-1">
        {Object.entries(data).map(([key, val]) => (
          <div key={key} className="flex-1 flex flex-col items-center gap-1.5">
            <span className="text-[11px] font-bold text-slate-700 font-mono">{val}</span>
            <div
              className="w-full rounded-t-md transition-all duration-500"
              style={{
                height: `${Math.max((val / max) * 70, 4)}px`,
                background: colorMap?.[key] || '#2563eb',
              }}
            />
            <span className="text-[10px] text-slate-400 font-semibold">{key}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
