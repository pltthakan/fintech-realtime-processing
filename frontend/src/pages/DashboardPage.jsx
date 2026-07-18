import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { reportService, accountService, transactionService } from '../services/api';
import { StatCard, PipelineTracker, StatusBadge, LoadingState } from '../components/ui';
import {
  formatMoney,
  formatDate,
  TX_TYPE_CONFIG,
  transactionAmountMeta,
  transactionDisplayLabel,
} from '../utils/helpers';
import { RefreshCw, Plus } from 'lucide-react';

export default function DashboardPage() {
  const { user, isPrivileged } = useAuth();

  // ADMIN/ANALYST → admin dashboard, USER → kendi hesap özeti
  return isPrivileged ? <AdminDashboard /> : <UserDashboard />;
}

// ─────────────────────────────────────────────
// USER DASHBOARD - kendi hesapları ve işlemleri
// ─────────────────────────────────────────────
function UserDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        // Kullanıcının hesaplarını çek
        const accRes = await accountService.getByUserId(user.id);
        const userAccounts = accRes.data.data || [];
        setAccounts(userAccounts);

        // Tüm hesaplardaki gelen ve giden işlemleri tek akışta çek
        if (userAccounts.length > 0) {
          try {
            const txRes = await transactionService.getByUser(user.id, 0, 5);
            setTransactions(txRes.data.data?.content || txRes.data.data || []);
          } catch {
            setTransactions([]);
          }
        }
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    })();
  }, [user]);

  if (loading) return <LoadingState message="Dashboard yükleniyor..." />;

  const totalBalance = accounts.reduce((sum, a) => {
    if (a.currency === 'TRY') return sum + Number(a.balance);
    return sum;
  }, 0);

  return (
      <div className="animate-fade-in">
        <div className="mb-7">
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">
            Merhaba, {user.firstName || user.username}
          </h1>
          <p className="text-sm text-slate-500 mt-1">Hesap özetiniz ve son işlemleriniz</p>
        </div>

        {/* Hesap yoksa uyarı */}
        {accounts.length === 0 ? (
            <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center">
              <div className="w-16 h-16 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-4">
                <Plus className="w-7 h-7 text-blue-500" />
              </div>
              <h3 className="text-lg font-bold text-slate-800 mb-2">Hoş Geldiniz!</h3>
              <p className="text-sm text-slate-500 mb-5">
                Başlamak için ilk hesabınızı oluşturun. Hesap oluşturduktan sonra
                transfer, ödeme ve diğer işlemlerinizi yapabilirsiniz.
              </p>
              <button
                  onClick={() => navigate('/accounts')}
                  className="px-5 py-2.5 rounded-lg bg-gradient-to-r from-blue-600 to-violet-600 text-white text-sm font-semibold hover:shadow-lg hover:shadow-blue-600/25 transition-all cursor-pointer"
              >
                Hesap Oluştur →
              </button>
            </div>
        ) : (
            <>
              {/* İstatistikler */}
              <div className="grid grid-cols-3 gap-4 mb-6">
                <StatCard
                    title="Toplam Bakiye (TRY)"
                    value={formatMoney(totalBalance, 'TRY')}
                    sub={`${accounts.length} hesap`}
                />
                <StatCard
                    title="Hesap Sayısı"
                    value={accounts.length}
                    sub="Aktif hesaplar"
                />
                <StatCard
                    title="Son İşlemler"
                    value={transactions.length}
                    sub="Son 5 işlem"
                />
              </div>

              {/* Hesap Kartları */}
              <div className="mb-6">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-bold text-slate-900">Hesaplarım</h3>
                  <button onClick={() => navigate('/accounts')}
                          className="text-xs text-blue-600 font-semibold hover:underline cursor-pointer">Tümünü Gör →</button>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {accounts.slice(0, 4).map(acc => (
                      <div key={acc.id} className="bg-white rounded-xl p-4 border border-slate-200">
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-xs font-semibold text-slate-500 uppercase">{acc.accountName}</span>
                          <span className="text-xs font-bold text-slate-300">{acc.currency}</span>
                        </div>
                        <p className="text-xl font-extrabold text-slate-900 tracking-tight">
                          {formatMoney(acc.balance, acc.currency)}
                        </p>
                        <p className="text-[11px] text-slate-400 font-mono mt-1">{acc.accountNumber?.slice(0, 12)}...</p>
                      </div>
                  ))}
                </div>
              </div>

              {/* Son İşlemler */}
              <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
                  <h3 className="text-sm font-bold text-slate-900">Son İşlemler</h3>
                  <button onClick={() => navigate('/transactions')}
                          className="text-xs text-blue-600 font-semibold hover:underline cursor-pointer">Tümünü Gör →</button>
                </div>
                {transactions.length === 0 ? (
                    <div className="text-center py-10 text-slate-400 text-sm">Henüz işlem yok</div>
                ) : (
                    transactions.map((tx, i) => {
                      const typeConf = TX_TYPE_CONFIG[tx.type] || {};
                      const amountMeta = transactionAmountMeta(tx);
                      const transactionLabel = transactionDisplayLabel(tx);
                      return (
                          <div key={tx.transactionId || i} className={`flex items-center px-5 py-3 ${i < transactions.length - 1 ? 'border-b border-slate-50' : ''}`}>
                            <div className={`w-8 h-8 rounded-lg flex items-center justify-center text-sm mr-3 flex-shrink-0 ${typeConf.iconBg || 'bg-blue-50'} ${typeConf.iconColor || 'text-blue-600'}`}>
                              {typeConf.icon || '↔'}
                            </div>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-slate-800">{transactionLabel}{tx.description ? ` — ${tx.description}` : ''}</p>
                              <p className="text-xs text-slate-400 mt-0.5">{formatDate(tx.createdAt)}</p>
                            </div>
                            <div className="text-right mr-3">
                              <p className={`text-sm font-bold font-mono ${amountMeta.color}`}>
                                {amountMeta.sign}
                                {formatMoney(tx.amount, tx.currency)}
                              </p>
                            </div>
                            <StatusBadge status={tx.status} />
                          </div>
                      );
                    })
                )}
              </div>
            </>
        )}
      </div>
  );
}

// ─────────────────────────────────────────────
// ADMIN DASHBOARD - sistem geneli
// ─────────────────────────────────────────────
function AdminDashboard() {
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

  const typeColors = { TRANSFER: '#2563eb', PAYMENT: '#8b5cf6', DEPOSIT: '#10b981', WITHDRAWAL: '#f59e0b' };
  const curColors = { TRY: '#2563eb', USD: '#10b981', EUR: '#8b5cf6', GBP: '#f59e0b' };

  return (
      <div className="animate-fade-in">
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

        <div className="grid grid-cols-4 gap-4 mb-6">
          <StatCard title="Toplam İşlem" value={data.totalTransactions?.toLocaleString()} sub={`${data.completedTransactions} tamamlandı`} trend={12} />
          <StatCard title="Toplam Hacim" value={formatMoney(data.totalVolume)} sub={`Ort: ${formatMoney(data.averageAmount)}`} trend={8} />
          <StatCard title="Engellenen" value={data.blockedTransactions} sub={`${data.suspiciousTransactions} şüpheli`} />
          <StatCard title="Ort. Süre" value={`${data.averageProcessingTimeMs?.toFixed(0) || 0}ms`} sub="Pipeline süresi" trend={-5} />
        </div>

        <div className="grid grid-cols-2 gap-4 mb-6">
          <MiniBarChart title="İşlem Türü Dağılımı" data={data.transactionsByType} colorMap={typeColors} />
          <MiniBarChart title="Para Birimi Dağılımı" data={data.transactionsByCurrency} colorMap={curColors} />
        </div>

        <div className="bg-white rounded-xl p-6 border border-slate-200 mb-6">
          <h3 className="text-sm font-bold text-slate-900 mb-1">Kafka Pipeline Durumu</h3>
          <p className="text-xs text-slate-400 mb-5">Son işlemin pipeline üzerindeki ilerleyişi</p>
          <PipelineTracker status={data.recentTransactions?.[0]?.status || 'PENDING'} />
        </div>

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
