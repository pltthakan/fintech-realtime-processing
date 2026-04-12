import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { accountService } from '../services/api';
import { LoadingState } from '../components/ui';
import { formatMoney, formatShortDate, ACCOUNT_TYPE_LABELS, CURRENCY_SYMBOLS } from '../utils/helpers';

export default function AccountsPage() {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const res = await accountService.getByUserId(user.id);
        setAccounts(res.data.data || []);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    })();
  }, [user]);

  if (loading) return <LoadingState />;

  return (
    <div className="animate-fade-in">
      <div className="mb-7">
        <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Hesaplarım</h1>
        <p className="text-sm text-slate-500 mt-1">Banka hesaplarınız ve bakiye bilgileri</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {accounts.map(acc => (
          <div
            key={acc.id}
            onClick={() => setSelectedId(selectedId === acc.id ? null : acc.id)}
            className={`bg-white rounded-2xl p-6 border-[1.5px] cursor-pointer transition-all relative overflow-hidden
              ${selectedId === acc.id ? 'border-blue-400 shadow-md shadow-blue-600/5' : 'border-slate-200 hover:border-slate-300'}`}
          >
            {/* Decorative corner */}
            <div className="absolute top-0 right-0 w-28 h-28 rounded-bl-[100px] bg-gradient-to-bl from-blue-500/5 to-violet-500/10" />

            <div className="relative">
              {/* Top row: type + status */}
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                    {ACCOUNT_TYPE_LABELS[acc.accountType] || acc.accountType}
                  </span>
                  <span className={`px-2 py-0.5 rounded text-[10px] font-bold
                    ${acc.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'}`}>
                    {acc.status}
                  </span>
                </div>
                <span className="text-xl font-extrabold text-slate-300">
                  {CURRENCY_SYMBOLS[acc.currency]}
                </span>
              </div>

              {/* Name + Balance */}
              <p className="text-sm text-slate-500 mb-1">{acc.accountName}</p>
              <p className="text-3xl font-extrabold text-slate-900 tracking-tight mb-3">
                {formatMoney(acc.balance, acc.currency)}
              </p>

              {/* IBAN */}
              <p className="font-mono text-xs text-slate-400 tracking-wide">{acc.accountNumber}</p>

              {/* Expanded details */}
              {selectedId === acc.id && (
                <div className="mt-4 pt-4 border-t border-slate-100 animate-slide-down">
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <p className="text-[11px] text-slate-400 mb-0.5">Günlük Limit</p>
                      <p className="text-sm font-bold text-slate-800">{formatMoney(acc.dailyLimit, acc.currency)}</p>
                    </div>
                    <div>
                      <p className="text-[11px] text-slate-400 mb-0.5">Oluşturulma</p>
                      <p className="text-sm font-semibold text-slate-800">{formatShortDate(acc.createdAt)}</p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {accounts.length === 0 && (
        <div className="text-center py-16 text-slate-400">Henüz hesabınız yok</div>
      )}
    </div>
  );
}
