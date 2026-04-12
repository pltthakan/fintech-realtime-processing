import { useState, useEffect } from 'react';
import { transactionService } from '../services/api';
import { StatusBadge, PipelineTracker, LoadingState } from '../components/ui';
import { formatMoney, formatDate, TX_TYPE_CONFIG } from '../utils/helpers';
import { RefreshCw, ChevronRight } from 'lucide-react';

export default function TransactionsPage() {
  const [txns, setTxns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      // İlk hesap ID'si ile çek — gerçek uygulamada kullanıcının hesapları üzerinden
      const res = await transactionService.getByAccount(1, 0, 20);
      setTxns(res.data.data?.content || res.data.data || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  if (loading) return <LoadingState message="İşlemler yükleniyor..." />;

  return (
    <div className="animate-fade-in">
      <div className="flex items-center justify-between mb-7">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">İşlemler</h1>
          <p className="text-sm text-slate-500 mt-1">Tüm finansal işlem geçmişiniz</p>
        </div>
        <button onClick={fetchData}
          className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg border border-slate-200 bg-white text-slate-500 text-sm font-medium hover:bg-slate-50 transition-colors cursor-pointer">
          <RefreshCw className="w-3.5 h-3.5" /> Yenile
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        {txns.length === 0 ? (
          <div className="text-center py-16 text-slate-400">Henüz işlem yok</div>
        ) : txns.map((tx, i) => {
          const typeConf = TX_TYPE_CONFIG[tx.type] || {};
          const isExpanded = expandedId === tx.transactionId;

          return (
            <div key={tx.transactionId} className={i < txns.length - 1 ? 'border-b border-slate-50' : ''}>
              {/* Row */}
              <div
                onClick={() => setExpandedId(isExpanded ? null : tx.transactionId)}
                className="flex items-center px-5 py-3.5 cursor-pointer hover:bg-slate-50/50 transition-colors"
              >
                {/* Type icon */}
                <div className={`w-9 h-9 rounded-[10px] flex items-center justify-center text-base mr-3.5 flex-shrink-0 ${typeConf.iconBg} ${typeConf.iconColor}`}>
                  {typeConf.icon}
                </div>

                {/* Description */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-slate-800">
                    {typeConf.label}{tx.description ? ` — ${tx.description}` : ''}
                  </p>
                  <p className="text-xs text-slate-400 font-mono mt-0.5">{tx.referenceNumber}</p>
                </div>

                {/* Amount */}
                <div className="text-right mr-4">
                  <p className={`text-[15px] font-bold font-mono
                    ${tx.type === 'DEPOSIT' ? 'text-emerald-600' : 'text-slate-900'}`}>
                    {tx.type === 'DEPOSIT' ? '+' : tx.type === 'WITHDRAWAL' || tx.type === 'PAYMENT' ? '-' : ''}
                    {formatMoney(tx.amount, tx.currency)}
                  </p>
                  <p className="text-[11px] text-slate-400 mt-0.5">{formatDate(tx.createdAt)}</p>
                </div>

                {/* Status */}
                <StatusBadge status={tx.status} />

                {/* Expand arrow */}
                <ChevronRight className={`w-4 h-4 text-slate-300 ml-3 transition-transform duration-200 ${isExpanded ? 'rotate-90' : ''}`} />
              </div>

              {/* Expanded detail */}
              {isExpanded && (
                <div className="px-5 pb-5 border-t border-slate-100 animate-slide-down">
                  <div className="pt-4 pb-2">
                    <PipelineTracker status={tx.status} />
                  </div>
                  <div className="grid grid-cols-3 gap-3 mt-3">
                    <InfoBox label="İşlem ID" value={tx.transactionId} mono />
                    <InfoBox label="Kaynak Hesap" value={tx.sourceAccountNumber || `ID: ${tx.sourceAccountId}`} mono />
                    <InfoBox label="Fraud Skoru"
                      value={<span className={`text-base font-bold font-mono
                        ${tx.fraudScore > 50 ? 'text-red-600' : tx.fraudScore > 25 ? 'text-amber-600' : 'text-emerald-600'}`}>
                        {tx.fraudScore}/100
                      </span>}
                    />
                    {tx.targetAccountNumber && (
                      <InfoBox label="Hedef Hesap" value={tx.targetAccountNumber} mono />
                    )}
                    {tx.completedAt && (
                      <InfoBox label="Tamamlanma" value={formatDate(tx.completedAt)} />
                    )}
                    {tx.errorMessage && (
                      <InfoBox label="Hata" value={tx.errorMessage} className="col-span-2" />
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function InfoBox({ label, value, mono, className = '' }) {
  return (
    <div className={`p-3 rounded-lg bg-slate-50 ${className}`}>
      <p className="text-[11px] text-slate-400 mb-1">{label}</p>
      <p className={`text-xs text-slate-800 break-all ${mono ? 'font-mono' : ''}`}>{value}</p>
    </div>
  );
}
