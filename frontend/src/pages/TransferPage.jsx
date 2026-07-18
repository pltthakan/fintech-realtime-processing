import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { transactionService, accountService, paymentRailService } from '../services/api';
import { PipelineTracker, StatusBadge, LoadingState } from '../components/ui';
import { formatMoney } from '../utils/helpers';
import { Check, Search, ShieldCheck } from 'lucide-react';
import toast from 'react-hot-toast';

const INITIAL_FORM = {
  sourceAccountId: '',
  targetAccountId: '',
  beneficiaryIban: '',
  beneficiaryName: '',
  amount: '',
  type: 'TRANSFER',
  description: '',
};

const OPERATION_MODES = [
  { key: 'OWN', label: 'Kendi Hesaplarım', icon: '↔', type: 'TRANSFER' },
  { key: 'OTHER', label: 'Başka Hesaba', icon: '➤', type: 'TRANSFER' },
  { key: 'PAYMENT', label: 'Ödeme', icon: '→', type: 'PAYMENT' },
  { key: 'WITHDRAWAL', label: 'Çekme', icon: '↑', type: 'WITHDRAWAL' },
];

export default function TransferPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState([]);
  const [operationMode, setOperationMode] = useState('OWN');
  const [form, setForm] = useState(INITIAL_FORM);
  const [resolvedBeneficiary, setResolvedBeneficiary] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [resolving, setResolving] = useState(false);
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

  const sourceAccount = accounts.find(account => String(account.id) === form.sourceAccountId);

  const updateField = (key, value) => {
    setForm(prev => ({ ...prev, [key]: value }));
    if (['sourceAccountId', 'beneficiaryIban', 'beneficiaryName', 'amount'].includes(key)) {
      setResolvedBeneficiary(null);
    }
  };

  const changeOperationMode = (mode) => {
    const selected = OPERATION_MODES.find(item => item.key === mode);
    setOperationMode(mode);
    setError('');
    setResolvedBeneficiary(null);
    setForm(prev => ({
      ...prev,
      type: selected.type,
      targetAccountId: '',
      beneficiaryIban: '',
      beneficiaryName: '',
    }));
  };

  const resolveBeneficiary = async () => {
    setError('');
    if (!form.sourceAccountId || !form.beneficiaryIban || !form.beneficiaryName || !form.amount) {
      setError('Alıcı doğrulaması için kaynak hesap, IBAN, alıcı adı ve tutar zorunludur');
      return;
    }
    if (Number(form.amount) <= 0) {
      setError('Tutar 0\'dan büyük olmalıdır');
      return;
    }

    setResolving(true);
    try {
      const response = await paymentRailService.resolveBeneficiary({
        sourceAccountId: Number(form.sourceAccountId),
        iban: form.beneficiaryIban,
        beneficiaryName: form.beneficiaryName,
        amount: Number(form.amount),
        currency: sourceAccount.currency,
      });
      setResolvedBeneficiary(response.data.data);
      toast.success('Alıcı ve transfer kanalı doğrulandı');
    } catch (err) {
      setResolvedBeneficiary(null);
      setError(err.response?.data?.message || err.message || 'Alıcı doğrulanamadı');
    } finally {
      setResolving(false);
    }
  };

  const handleSubmit = async () => {
    setError('');
    if (!form.sourceAccountId || !form.amount) {
      setError('Kaynak hesap ve tutar zorunludur');
      return;
    }
    if (operationMode === 'OWN' && !form.targetAccountId) {
      setError('Kendi hesaplarınız arasındaki transfer için hedef hesap zorunludur');
      return;
    }
    if (operationMode === 'OTHER' && !resolvedBeneficiary) {
      setError('İşlemi başlatmadan önce alıcıyı doğrulayın');
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
            (operationMode === 'OWN' && form.targetAccountId) ? Number(form.targetAccountId) : null,
        beneficiaryIban: operationMode === 'OTHER' ? form.beneficiaryIban : null,
        beneficiaryName: operationMode === 'OTHER' ? form.beneficiaryName : null,
        transferRail: operationMode === 'OTHER' ? resolvedBeneficiary.rail : null,
        amount: Number(form.amount),
        currency: sourceAccount.currency,
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
              {result.transferRail && <DetailRow label="Transfer Kanalı" value={result.transferRail} />}
              {result.beneficiaryIban && <DetailRow label="Alıcı IBAN" value={result.beneficiaryIban} mono />}
              {result.externalReference && <DetailRow label="Banka Referansı" value={result.externalReference} mono />}
              <DetailRow label="Durum" value={<StatusBadge status={result.status} />} />
            </div>
          </div>

          <div className="flex gap-3 justify-center">
            <button
                onClick={() => {
                  setResult(null);
                  setOperationMode('OWN');
                  setResolvedBeneficiary(null);
                  setForm(INITIAL_FORM);
                }}
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
            <p className="text-sm text-slate-500 mt-1">Kendi hesaplarınıza veya IBAN ile başka hesaba para gönderin</p>
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
          <p className="text-sm text-slate-500 mt-1">Kendi hesaplarınıza veya IBAN ile başka hesaba para gönderin</p>
        </div>

        <div className="max-w-lg bg-white rounded-2xl p-7 border border-slate-200">
          {error && (
              <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-red-600 text-sm mb-5">{error}</div>
          )}

          {/* İşlem Akışı */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-slate-700 mb-2">İşlem Türü</label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {OPERATION_MODES.map(mode => (
                  <button
                      key={mode.key}
                      type="button"
                      onClick={() => changeOperationMode(mode.key)}
                      className={`py-2.5 px-2 rounded-lg border-[1.5px] text-center text-xs font-semibold transition-all cursor-pointer
                  ${operationMode === mode.key
                          ? 'border-blue-500 bg-blue-50 text-blue-600'
                          : 'border-slate-200 text-slate-500 hover:border-slate-300'
                      }`}
                  >
                    <div className="text-lg mb-0.5">{mode.icon}</div>
                    {mode.label}
                  </button>
              ))}
            </div>
          </div>

          {/* Kaynak Hesap */}
          <div className="mb-4">
            <label className="block text-sm font-semibold text-slate-700 mb-1.5">Kaynak Hesap *</label>
            <select value={form.sourceAccountId} onChange={e => {
              setForm(prev => ({ ...prev, sourceAccountId: e.target.value, targetAccountId: '' }));
              setResolvedBeneficiary(null);
            }}
                    className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none pr-9 appearance-none cursor-pointer">
              <option value="">Hesap seçin...</option>
              {accounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.accountName} — {formatMoney(a.availableBalance ?? a.balance, a.currency)} kullanılabilir
                  </option>
              ))}
            </select>
          </div>

          {/* Kendi hesapları arasında transfer */}
          {operationMode === 'OWN' && (
              <div className="mb-4">
                <label className="block text-sm font-semibold text-slate-700 mb-1.5">Hedef Hesap *</label>
                <select value={form.targetAccountId} onChange={e => updateField('targetAccountId', e.target.value)}
                        className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none pr-9 appearance-none cursor-pointer">
                  <option value="">Hesap seçin...</option>
                  {accounts.filter(a => String(a.id) !== form.sourceAccountId
                      && (!sourceAccount || a.currency === sourceAccount.currency)).map(a => (
                      <option key={a.id} value={a.id}>
                        {a.accountName} — {a.accountNumber}
                      </option>
                  ))}
                </select>
              </div>
          )}

          {/* IBAN ile başka hesaba transfer */}
          {operationMode === 'OTHER' && (
              <div className="mb-5 space-y-4 rounded-xl border border-blue-100 bg-blue-50/40 p-4">
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-1.5">Alıcı IBAN *</label>
                  <input
                      value={form.beneficiaryIban}
                      onChange={e => updateField('beneficiaryIban', e.target.value.toUpperCase())}
                      placeholder="TR00 0000 0000 0000 0000 0000 00"
                      autoComplete="off"
                      className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-white text-sm font-mono uppercase outline-none focus:border-blue-400"
                  />
                </div>
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-1.5">Alıcı Adı Soyadı *</label>
                  <input
                      value={form.beneficiaryName}
                      onChange={e => updateField('beneficiaryName', e.target.value)}
                      placeholder="Alıcı adı soyadı"
                      autoComplete="off"
                      className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-white text-sm outline-none focus:border-blue-400"
                  />
                </div>
              </div>
          )}

          {/* Tutar + Para Birimi */}
          <div className="grid grid-cols-3 gap-3 mb-4">
            <div className="col-span-2">
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Tutar *</label>
              <input type="number" step="0.01" min="0.01" value={form.amount} onChange={e => updateField('amount', e.target.value)}
                     placeholder="0.00"
                     className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-lg font-bold font-mono outline-none" />
            </div>
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Birim</label>
              <div className="w-full px-3 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-100 text-sm font-semibold text-slate-700">
                {sourceAccount?.currency || '—'}
              </div>
            </div>
          </div>

          {operationMode === 'OTHER' && (
              <div className="mb-5">
                <button
                    type="button"
                    onClick={resolveBeneficiary}
                    disabled={resolving}
                    className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg border border-blue-200 bg-blue-50 text-blue-700 text-sm font-semibold hover:bg-blue-100 disabled:opacity-60 cursor-pointer"
                >
                  <Search className="w-4 h-4" />
                  {resolving ? 'Alıcı doğrulanıyor...' : 'Alıcıyı Doğrula'}
                </button>

                {resolvedBeneficiary && (
                    <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm">
                      <div className="flex items-center gap-2 text-emerald-700 font-semibold mb-2">
                        <ShieldCheck className="w-4 h-4" /> Alıcı doğrulandı
                      </div>
                      <div className="grid grid-cols-2 gap-2 text-xs">
                        <span className="text-slate-500">Alıcı</span>
                        <span className="text-right font-medium text-slate-700">{resolvedBeneficiary.maskedBeneficiaryName}</span>
                        <span className="text-slate-500">IBAN</span>
                        <span className="text-right font-mono text-slate-700">{resolvedBeneficiary.maskedIban}</span>
                        <span className="text-slate-500">Kanal</span>
                        <span className="text-right font-bold text-blue-700">{resolvedBeneficiary.rail}</span>
                      </div>
                    </div>
                )}
              </div>
          )}

          {/* Açıklama */}
          <div className="mb-6">
            <label className="block text-sm font-semibold text-slate-700 mb-1.5">Açıklama</label>
            <input value={form.description} onChange={e => updateField('description', e.target.value)}
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
            {operationMode === 'OTHER'
                ? 'Başka hesaba transferde para önce rezerve edilir; Payment Rail Service HAVALE/EFT/FAST sonucunu üretir. Başarılıysa ledger kaydı oluşur, başarısızsa rezervasyon otomatik iade edilir.'
                : 'İşleminiz Kafka pipeline üzerinden Transaction → Fraud → Account → Notification aşamalarından geçecektir.'}
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
