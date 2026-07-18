import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { transactionService, accountService } from '../services/api';
import { StatusBadge, PipelineTracker, LoadingState } from '../components/ui';
import {
  formatMoney,
  formatDate,
  TX_TYPE_CONFIG,
  transactionAmountMeta,
  transactionDisplayLabel,
} from '../utils/helpers';
import { RefreshCw, ChevronRight } from 'lucide-react';

export default function TransactionsPage() {
  const { user } = useAuth();
  const [txns, setTxns] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [selectedAccountId, setSelectedAccountId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState(null);

  // Önce kullanıcının hesaplarını çek
  useEffect(() => {
    (async () => {
      try {
        const res = await accountService.getByUserId(user.id);
        const userAccounts = res.data.data || [];
        setAccounts(userAccounts);
        // İlk hesabı otomatik seç
        if (userAccounts.length > 0) {
          setSelectedAccountId(userAccounts[0].id);
        }
      } catch (err) {
        console.error(err);
      }
    })();
  }, [user]);

  // Seçili hesabın işlemlerini çek
  useEffect(() => {
    if (selectedAccountId) {
      fetchTransactions(selectedAccountId);
    } else {
      setLoading(false);
    }
  }, [selectedAccountId]);

  const fetchTransactions = async (accountId) => {
    setLoading(true);
    try {
      const res = await transactionService.getByAccount(accountId, 0, 50);
      setTxns(res.data.data?.content || res.data.data || []);
    } catch (err) {
      console.error(err);
      setTxns([]);
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="animate-fade-in">
        <div className="flex items-center justify-between mb-7">
          <div>
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">İşlemler</h1>
            <p className="text-sm text-slate-500 mt-1">Tüm finansal işlem geçmişiniz</p>
          </div>
          <button onClick={() => selectedAccountId && fetchTransactions(selectedAccountId)}
                  className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg border border-slate-200 bg-white text-slate-500 text-sm font-medium hover:bg-slate-50 transition-colors cursor-pointer">
            <RefreshCw className="w-3.5 h-3.5" /> Yenile
          </button>
        </div>

        {/* Hesap Seçici */}
        {accounts.length > 0 && (
            <div className="flex gap-2 mb-5 overflow-x-auto pb-1">
              {accounts.map(acc => (
                  <button
                      key={acc.id}
                      onClick={() => setSelectedAccountId(acc.id)}
                      className={`flex-shrink-0 px-4 py-2.5 rounded-lg border-[1.5px] text-sm font-medium transition-all cursor-pointer
                ${selectedAccountId === acc.id
                          ? 'border-blue-500 bg-blue-50 text-blue-700'
                          : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                      }`}
                  >
                    <span className="font-semibold">{acc.accountName}</span>
                    <span className="text-xs ml-2 opacity-70">({formatMoney(acc.balance, acc.currency)})</span>
                  </button>
              ))}
            </div>
        )}

        {/* Hesap yoksa uyarı */}
        {accounts.length === 0 && !loading && (
            <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
              <p className="text-slate-500 font-medium mb-2">Henüz hesabınız yok</p>
              <p className="text-sm text-slate-400">İşlemleri görmek için önce Hesaplarım sayfasından hesap oluşturun.</p>
            </div>
        )}

        {/* İşlem Listesi */}
        {loading ? <LoadingState message="İşlemler yükleniyor..." /> : (
            accounts.length > 0 && (
                <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
                  {txns.length === 0 ? (
                      <div className="text-center py-16 text-slate-400">Bu hesapta henüz işlem yok</div>
                  ) : txns.map((tx, i) => {
                    const typeConf = TX_TYPE_CONFIG[tx.type] || {};
                    const amountMeta = transactionAmountMeta(tx);
                    const transactionLabel = transactionDisplayLabel(tx);
                    const isExpanded = expandedId === (tx.transactionId || tx.id);

                    return (
                        <div key={tx.transactionId || tx.id} className={i < txns.length - 1 ? 'border-b border-slate-50' : ''}>
                          <div
                              onClick={() => setExpandedId(isExpanded ? null : (tx.transactionId || tx.id))}
                              className="flex items-center px-5 py-3.5 cursor-pointer hover:bg-slate-50/50 transition-colors"
                          >
                            <div className={`w-9 h-9 rounded-[10px] flex items-center justify-center text-base mr-3.5 flex-shrink-0 ${typeConf.iconBg || 'bg-blue-50'} ${typeConf.iconColor || 'text-blue-600'}`}>
                              {typeConf.icon || '↔'}
                            </div>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-semibold text-slate-800">
                                {transactionLabel}{tx.description ? ` — ${tx.description}` : ''}
                              </p>
                              <p className="text-xs text-slate-400 font-mono mt-0.5">{tx.referenceNumber}</p>
                            </div>
                            <div className="text-right mr-4">
                              <p className={`text-[15px] font-bold font-mono ${amountMeta.color}`}>
                                {amountMeta.sign}
                                {formatMoney(tx.amount, tx.currency)}
                              </p>
                              <p className="text-[11px] text-slate-400 mt-0.5">{formatDate(tx.createdAt)}</p>
                            </div>
                            <StatusBadge status={tx.status} />
                            <ChevronRight className={`w-4 h-4 text-slate-300 ml-3 transition-transform duration-200 ${isExpanded ? 'rotate-90' : ''}`} />
                          </div>

                          {isExpanded && (
                              <div className="px-5 pb-5 border-t border-slate-100 animate-slide-down">
                                <div className="pt-4 pb-2">
                                  <PipelineTracker status={tx.status} />
                                </div>
                                <div className="grid grid-cols-3 gap-3 mt-3">
                                  <InfoBox label="İşlem ID" value={tx.transactionId || tx.id} mono />
                                  <InfoBox label="Kaynak Hesap" value={tx.sourceAccountNumber || `ID: ${tx.sourceAccountId}`} mono />
                                  <InfoBox label="Fraud Skoru"
                                           value={<span className={`text-base font-bold font-mono
                            ${tx.fraudScore > 50 ? 'text-red-600' : tx.fraudScore > 25 ? 'text-amber-600' : 'text-emerald-600'}`}>
                            {tx.fraudScore ?? 0}/100
                          </span>}
                                  />
                                  {tx.targetAccountNumber && (
                                      <InfoBox label="Hedef Hesap" value={tx.targetAccountNumber} mono />
                                  )}
                                  {tx.completedAt && (
                                      <InfoBox label="Tamamlanma" value={formatDate(tx.completedAt)} />
                                  )}
                                </div>
                              </div>
                          )}
                        </div>
                    );
                  })}
                </div>
            )
        )}
      </div>
  );
}

function InfoBox({ label, value, mono, className = '' }) {
  return (
      <div className={`p-3 rounded-lg bg-slate-50 ${className}`}>
        <p className="text-[11px] text-slate-400 mb-1">{label}</p>
        <div className={`text-xs text-slate-800 break-all ${mono ? 'font-mono' : ''}`}>{value}</div>
      </div>
  );
}
