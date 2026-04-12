import { useState, useEffect } from 'react';
import { reportService } from '../services/api';
import { StatusBadge, LoadingState, EmptyState } from '../components/ui';
import { formatMoney, formatDate, TX_TYPE_CONFIG } from '../utils/helpers';
import { X, AlertTriangle } from 'lucide-react';

export default function FraudPage() {
  const [tab, setTab] = useState('suspicious');
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const res = tab === 'suspicious'
          ? await reportService.getSuspicious()
          : await reportService.getBlocked();
        setData(res.data.data || []);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    })();
  }, [tab]);

  return (
    <div className="animate-fade-in">
      <div className="mb-7">
        <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Fraud & AML Kontrol</h1>
        <p className="text-sm text-slate-500 mt-1">Şüpheli ve engellenen işlemlerin incelenmesi</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 p-1 rounded-lg w-fit mb-5">
        {[
          { id: 'suspicious', label: 'Şüpheli İşlemler' },
          { id: 'blocked', label: 'Engellenen İşlemler' },
        ].map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`px-4 py-2 rounded-md text-sm font-semibold transition-all cursor-pointer
              ${tab === t.id ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {loading ? <LoadingState /> : data.length === 0 ? <EmptyState title="Kayıt bulunamadı" /> : (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
          {data.map((tx, i) => (
            <div key={tx.transactionId} className={`flex items-center gap-4 px-5 py-4 ${i < data.length - 1 ? 'border-b border-slate-50' : ''}`}>
              {/* Icon */}
              <div className={`w-10 h-10 rounded-[10px] flex items-center justify-center flex-shrink-0
                ${tx.isBlocked ? 'bg-red-50 text-red-500' : 'bg-amber-50 text-amber-500'}`}>
                {tx.isBlocked ? <X className="w-5 h-5" /> : <AlertTriangle className="w-5 h-5" />}
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-slate-800 mb-1">
                  {TX_TYPE_CONFIG[tx.type]?.label} — {formatMoney(tx.amount, tx.currency)}
                </p>
                <p className="text-xs text-slate-500">{tx.fraudCheckMessage}</p>
                <p className="text-[11px] text-slate-400 font-mono mt-1">{tx.transactionId?.slice(0, 24)}...</p>
              </div>

              {/* Fraud Score */}
              <div className="text-right">
                <p className={`text-2xl font-extrabold font-mono
                  ${tx.fraudScore > 60 ? 'text-red-600' : 'text-amber-500'}`}>
                  {tx.fraudScore}
                </p>
                <p className="text-[10px] text-slate-400">Fraud Skoru</p>
              </div>

              {/* Status */}
              <StatusBadge status={tx.status} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
