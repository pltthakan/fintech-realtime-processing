import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { transactionService, accountService } from '../services/api';
import { PipelineTracker, StatusBadge, LoadingState } from '../components/ui';
import { formatMoney, TX_TYPE_CONFIG } from '../utils/helpers';
import { Check } from 'lucide-react';
import toast from 'react-hot-toast';

export default function TransferPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState([]);
  const [form, setForm] = useState({
    sourceAccountId: '', targetAccountId: '',
    amount: '', currency: 'TRY', type: 'TRANSFER', description: '',
  });
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [pageLoading, setPageLoading] = useState(true);

  // Kullanıcının hesaplarını yükle
  useEffect(() => {
    (async () => {
      try {
        const res = await accountService.getByUserId(user.id);
        setAccounts(res.data.data || []);
      } catch (err) {
        console.error(err);
      } finally {
        setPageLoading(false);
      }
    })();
  }, [user]);

  const handleSubmit = async () => {
    setError('');
    if (!form.sourceAccountId || !form.amount) {
      setError('Kaynak hesap ve tutar zorunludur');
      return;
    }
    if (form.type === 'TRANSFER' && !form.targetAccountId) {
      setError('Transfer için hedef hesap zorunludur');
      return;
    }
    if (Number(form.amount) <= 0) {
      setError('Tutar 0\'dan büyük olmalıdır');
      return;
    }
    setLoading(true);
    try {
      const body = {
        sourceAccountId: Number(form.sourceAccountId),
        targetAccountId: (form.type === 'DEPOSIT') ? Number(form.sourceAccountId) :
            (form.type === 'TRANSFER' && form.targetAccountId) ? Number(form.targetAccountId) : null,
        amount: Number(form.amount),
        currency: form.currency,
        type: form.type,
        description: form.description || null,
        idempotencyKey: `txn-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      };
      const res = await transactionService.create(body);
      setResult(res.data.data);
      toast.success('İşlem başarıyla oluşturuldu!');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'İşlem oluşturulamadı');
    } finally {
      setLoading(false);
    }
  };

  const set = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  if (pageLoading) return <LoadingState />;

  // ── Sonuç ekranı ──
  if (result) {
    return (
        <div className="animate-fade-in max-w-lg mx-auto mt-16 text-center">
          <div className="w-16 h-16 rounded-full bg-emerald-50 flex items-center justify-center mx-auto mb-5">
            <Check className="w-8 h-8 text-emerald-500" strokeWidth={2.5} />
          </div>
          <h2 className="text-xl font-extrabold text-slate-900 mb-2">İşlem Oluşturuldu!</h2>
          <p className="text-sm text-slate-500 mb-6">İşleminiz Kafka pipeline'ına gönderildi</p>

          <div className="bg-white rounded-xl p-6 border border-slate-200 text-left mb-6">
            <PipelineTracker status={result.status} />
            <div className="mt-5 space-y-3">
              <DetailRow label="İşlem ID" value={(result.transactionId || '').slice(0, 20) + '...'} mono />
              <DetailRow label="Referans No" value={result.referenceNumber} mono />
              <DetailRow label="Tutar" value={formatMoney(result.amount, result.currency)} />
              <DetailRow label="Durum" value={<StatusBadge status={result.status} />} />
            </div>
          </div>

          <div className="flex gap-3 justify-center">
            <button
                onClick={() => { setResult(null); setForm({ sourceAccountId: '', targetAccountId: '', amount: '', currency: 'TRY', type: 'TRANSFER', description: '' }); }}
                className="px-6 py-2.5 rounded-lg bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 transition-colors cursor-pointer"
            >
              Yeni İşlem
            </button>
            <button
                onClick={() => navigate('/transactions')}
                className="px-6 py-2.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 transition-colors cursor-pointer"
            >
              İşlemleri Gör
            </button>
          </div>
        </div>
    );
  }

  // ── Hesap yoksa uyarı ──
  if (accounts.length === 0) {
    return (
        <div className="animate-fade-in">
          <div className="mb-7">
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Yeni İşlem</h1>
            <p className="text-sm text-slate-500 mt-1">Transfer, ödeme, para yatırma veya çekme işlemi başlatın</p>
          </div>
          <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center max-w-lg">
            <h3 className="text-lg font-bold text-slate-800 mb-2">Hesap bulunamadı</h3>
            <p className="text-sm text-slate-500 mb-5">İşlem yapabilmek için önce bir hesap oluşturmanız gerekiyor.</p>
            <button
                onClick={() => navigate('/accounts')}
                className="px-5 py-2.5 rounded-lg bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 transition-colors cursor-pointer"
            >
              Hesap Oluşturmaya Git →
            </button>
          </div>
        </div>
    );
  }

  // ── Form ──
  return (
      <div className="animate-fade-in">
        <div className="mb-7">
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Yeni İşlem</h1>
          <p className="text-sm text-slate-500 mt-1">Transfer, ödeme, para yatırma veya çekme işlemi başlatın</p>
        </div>

        <div className="max-w-lg bg-white rounded-2xl p-7 border border-slate-200">
          {error && (
              <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-red-600 text-sm mb-5">{error}</div>
          )}

          {/* İşlem Türü */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-slate-700 mb-2">İşlem Türü</label>
            <div className="grid grid-cols-4 gap-2">
              {Object.entries(TX_TYPE_CONFIG).map(([key, conf]) => (
                  <button
                      key={key}
                      onClick={() => set('type', key)}
                      className={`py-2.5 px-2 rounded-lg border-[1.5px] text-center text-xs font-semibold transition-all cursor-pointer
                  ${form.type === key
                          ? 'border-blue-500 bg-blue-50 text-blue-600'
                          : 'border-slate-200 text-slate-500 hover:border-slate-300'
                      }`}
                  >
                    <div className="text-lg mb-0.5">{conf.icon}</div>
                    {conf.label}
                  </button>
              ))}
            </div>
          </div>

          {/* Kaynak Hesap */}
          <div className="mb-4">
            <label className="block text-sm font-semibold text-slate-700 mb-1.5">Kaynak Hesap *</label>
            <select value={form.sourceAccountId} onChange={e => set('sourceAccountId', e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none pr-9 appearance-none cursor-pointer">
              <option value="">Hesap seçin...</option>
              {accounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.accountName} — {formatMoney(a.balance, a.currency)}
                  </option>
              ))}
            </select>
          </div>

          {/* Hedef Hesap (sadece TRANSFER) */}
          {form.type === 'TRANSFER' && (
              <div className="mb-4">
                <label className="block text-sm font-semibold text-slate-700 mb-1.5">Hedef Hesap *</label>
                <select value={form.targetAccountId} onChange={e => set('targetAccountId', e.target.value)}
                        className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none pr-9 appearance-none cursor-pointer">
                  <option value="">Hesap seçin...</option>
                  {accounts.filter(a => String(a.id) !== form.sourceAccountId).map(a => (
                      <option key={a.id} value={a.id}>
                        {a.accountName} — {a.accountNumber}
                      </option>
                  ))}
                </select>
              </div>
          )}

          {/* Tutar + Para Birimi */}
          <div className="grid grid-cols-3 gap-3 mb-4">
            <div className="col-span-2">
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Tutar *</label>
              <input type="number" step="0.01" min="0.01" value={form.amount} onChange={e => set('amount', e.target.value)}
                     placeholder="0.00"
                     className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-lg font-bold font-mono outline-none" />
            </div>
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Birim</label>
              <select value={form.currency} onChange={e => set('currency', e.target.value)}
                      className="w-full px-3 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none pr-9 appearance-none cursor-pointer">
                <option value="TRY">₺ TRY</option>
                <option value="USD">$ USD</option>
                <option value="EUR">€ EUR</option>
                <option value="GBP">£ GBP</option>
              </select>
            </div>
          </div>

          {/* Açıklama */}
          <div className="mb-6">
            <label className="block text-sm font-semibold text-slate-700 mb-1.5">Açıklama</label>
            <input value={form.description} onChange={e => set('description', e.target.value)}
                   placeholder="İşlem açıklaması (opsiyonel)"
                   className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none" />
          </div>

          <button
              onClick={handleSubmit}
              disabled={loading}
              className="w-full py-3 rounded-lg bg-gradient-to-r from-blue-600 to-violet-600 text-white text-[15px] font-semibold
                     hover:shadow-lg hover:shadow-blue-600/25 active:scale-[0.98] transition-all disabled:opacity-60 cursor-pointer"
          >
            {loading ? 'İşlem gönderiliyor...' : 'İşlemi Başlat →'}
          </button>

          <div className="mt-4 p-3 rounded-lg bg-slate-50 text-xs text-slate-400 leading-relaxed">
            İşleminiz Kafka pipeline üzerinden sırasıyla Transaction Service (A) → Fraud Detection (B) →
            Account Service (C) → Notification (D) aşamalarından geçecektir.
          </div>
        </div>
      </div>
  );
}

function DetailRow({ label, value, mono }) {
  return (
      <div className="flex justify-between items-center">
        <span className="text-sm text-slate-500">{label}</span>
        <span className={`text-sm text-slate-800 ${mono ? 'font-mono' : ''}`}>{value}</span>
      </div>
  );
}
