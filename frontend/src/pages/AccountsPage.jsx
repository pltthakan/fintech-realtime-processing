import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { accountService } from '../services/api';
import { LoadingState } from '../components/ui';
import { formatMoney, formatShortDate, ACCOUNT_TYPE_LABELS, CURRENCY_SYMBOLS } from '../utils/helpers';
import { Plus, X } from 'lucide-react';
import toast from 'react-hot-toast';

export default function AccountsPage() {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState(null);
  const [showCreate, setShowCreate] = useState(false);

  const fetchAccounts = async () => {
    setLoading(true);
    try {
      const res = await accountService.getByUserId(user.id);
      setAccounts(res.data.data || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchAccounts(); }, [user]);

  if (loading) return <LoadingState />;

  return (
      <div className="animate-fade-in">
        <div className="flex items-center justify-between mb-7">
          <div>
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Hesaplarım</h1>
            <p className="text-sm text-slate-500 mt-1">Banka hesaplarınız ve bakiye bilgileri</p>
          </div>
          <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-gradient-to-r from-blue-600 to-violet-600 text-white text-sm font-semibold hover:shadow-lg hover:shadow-blue-600/25 transition-all cursor-pointer"
          >
            <Plus className="w-4 h-4" /> Yeni Hesap
          </button>
        </div>

        {/* Hesap Kartları */}
        {accounts.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {accounts.map(acc => (
                  <div
                      key={acc.id}
                      onClick={() => setSelectedId(selectedId === acc.id ? null : acc.id)}
                      className={`bg-white rounded-2xl p-6 border-[1.5px] cursor-pointer transition-all relative overflow-hidden
                ${selectedId === acc.id ? 'border-blue-400 shadow-md shadow-blue-600/5' : 'border-slate-200 hover:border-slate-300'}`}
                  >
                    <div className="absolute top-0 right-0 w-28 h-28 rounded-bl-[100px] bg-gradient-to-bl from-blue-500/5 to-violet-500/10" />
                    <div className="relative">
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
                      <p className="text-sm text-slate-500 mb-1">{acc.accountName}</p>
                      <p className="text-3xl font-extrabold text-slate-900 tracking-tight mb-3">
                        {formatMoney(acc.balance, acc.currency)}
                      </p>
                      <p className="font-mono text-xs text-slate-400 tracking-wide">{acc.accountNumber}</p>

                      {selectedId === acc.id && (
                          <div className="mt-4 pt-4 border-t border-slate-100 animate-slide-down">
                            <div className="grid grid-cols-2 gap-3">
                              <div>
                                <p className="text-[11px] text-slate-400 mb-0.5">Günlük Limit</p>
                                <p className="text-sm font-bold text-slate-800">{formatMoney(acc.dailyLimit, acc.currency)}</p>
                              </div>
                              <div>
                                <p className="text-[11px] text-slate-400 mb-0.5">Hesap ID</p>
                                <p className="text-sm font-semibold text-slate-800 font-mono">{acc.id}</p>
                              </div>
                            </div>
                          </div>
                      )}
                    </div>
                  </div>
              ))}
            </div>
        ) : (
            <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center">
              <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center mx-auto mb-4">
                <Plus className="w-7 h-7 text-slate-400" />
              </div>
              <h3 className="text-lg font-bold text-slate-800 mb-2">Henüz hesabınız yok</h3>
              <p className="text-sm text-slate-500 mb-5">İşlem yapabilmek için en az bir hesap oluşturmanız gerekiyor.</p>
              <button
                  onClick={() => setShowCreate(true)}
                  className="px-5 py-2.5 rounded-lg bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 transition-colors cursor-pointer"
              >
                İlk Hesabımı Oluştur
              </button>
            </div>
        )}

        {/* Hesap Oluşturma Modalı */}
        {showCreate && (
            <CreateAccountModal
                onClose={() => setShowCreate(false)}
                onCreated={() => { setShowCreate(false); fetchAccounts(); }}
            />
        )}
      </div>
  );
}

function CreateAccountModal({ onClose, onCreated }) {
  const [form, setForm] = useState({
    accountName: '',
    accountType: 'CHECKING',
    currency: 'TRY',
    initialBalance: '0',
  });
  const [loading, setLoading] = useState(false);

  const set = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  const handleSubmit = async () => {
    setLoading(true);
    try {
      await accountService.create({
        accountName: form.accountName || null,
        accountType: form.accountType,
        currency: form.currency,
        initialBalance: Number(form.initialBalance) || 0,
      });
      toast.success('Hesap başarıyla oluşturuldu!');
      onCreated();
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
        <div className="bg-white rounded-2xl p-7 w-[420px] shadow-2xl animate-slide-up">
          <div className="flex items-center justify-between mb-5">
            <h2 className="text-lg font-bold text-slate-900">Yeni Hesap Oluştur</h2>
            <button onClick={onClose} className="p-1 rounded-md hover:bg-slate-100 transition-colors cursor-pointer">
              <X className="w-5 h-5 text-slate-400" />
            </button>
          </div>

          <div className="mb-4">
            <label className="block text-sm font-semibold text-slate-700 mb-1.5">Hesap Adı</label>
            <input
                value={form.accountName}
                onChange={e => set('accountName', e.target.value)}
                placeholder="Örn: Vadesiz TL Hesabı"
                className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none"
            />
          </div>

          <div className="grid grid-cols-2 gap-3 mb-4">
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Hesap Türü</label>
              <select value={form.accountType} onChange={e => set('accountType', e.target.value)}
                      className="w-full px-3 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none pr-9 appearance-none cursor-pointer">
                <option value="CHECKING">Vadesiz</option>
                <option value="SAVINGS">Birikim</option>
                <option value="INVESTMENT">Yatırım</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Para Birimi</label>
              <select value={form.currency} onChange={e => set('currency', e.target.value)}
                      className="w-full px-3 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none pr-9 appearance-none cursor-pointer">
                <option value="TRY">₺ TRY</option>
                <option value="USD">$ USD</option>
                <option value="EUR">€ EUR</option>
                <option value="GBP">£ GBP</option>
              </select>
            </div>
          </div>

          <div className="mb-6">
            <label className="block text-sm font-semibold text-slate-700 mb-1.5">Başlangıç Bakiyesi</label>
            <input
                type="number"
                step="0.01"
                min="0"
                value={form.initialBalance}
                onChange={e => set('initialBalance', e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none font-mono"
            />
          </div>

          <div className="flex gap-3">
            <button onClick={onClose}
                    className="flex-1 py-2.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 transition-colors cursor-pointer">
              İptal
            </button>
            <button onClick={handleSubmit} disabled={loading}
                    className="flex-1 py-2.5 rounded-lg bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 disabled:opacity-60 transition-colors cursor-pointer">
              {loading ? 'Oluşturuluyor...' : 'Oluştur'}
            </button>
          </div>
        </div>
      </div>
  );
}
