import { useState } from 'react';
import { reportService } from '../services/api';
import { StatusBadge, LoadingState, EmptyState } from '../components/ui';
import { formatMoney, formatDate, TX_TYPE_CONFIG } from '../utils/helpers';
import { Clock, Users, CreditCard, Calendar, Search } from 'lucide-react';

export default function ReportsPage() {
  const [activeReport, setActiveReport] = useState(null);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);

  // Date range state
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  // User/Account ID state
  const [inputId, setInputId] = useState('');

  const reports = [
    { id: 'date-range', title: 'Tarih Aralığı Raporu', desc: 'Belirli tarihler arasındaki işlemleri filtreleyin', icon: Clock, endpoint: 'GET /reports/date-range' },
    { id: 'user', title: 'Kullanıcı Raporu', desc: 'Kullanıcı bazlı işlem geçmişi', icon: Users, endpoint: 'GET /reports/user/{id}' },
    { id: 'account', title: 'Hesap Raporu', desc: 'Hesap bazlı işlem geçmişi', icon: CreditCard, endpoint: 'GET /reports/account/{id}' },
  ];

  const runReport = async () => {
    setLoading(true);
    setResults([]);
    try {
      let res;
      if (activeReport === 'date-range') {
        res = await reportService.getByDateRange(startDate, endDate);
        setResults(res.data.data || []);
      } else if (activeReport === 'user') {
        res = await reportService.getByUser(inputId);
        setResults(res.data.data?.content || res.data.data || []);
      } else if (activeReport === 'account') {
        res = await reportService.getByAccount(inputId);
        setResults(res.data.data?.content || res.data.data || []);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="animate-fade-in">
      <div className="mb-7">
        <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Raporlar</h1>
        <p className="text-sm text-slate-500 mt-1">Detaylı raporlar ve analizler</p>
      </div>

      {/* Report cards */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {reports.map(r => {
          const Icon = r.icon;
          const isActive = activeReport === r.id;
          return (
            <div
              key={r.id}
              onClick={() => { setActiveReport(isActive ? null : r.id); setResults([]); }}
              className={`bg-white rounded-xl p-5 border-[1.5px] cursor-pointer transition-all
                ${isActive ? 'border-blue-400 shadow-md shadow-blue-600/5' : 'border-slate-200 hover:border-slate-300'}`}
            >
              <div className={`w-9 h-9 rounded-lg flex items-center justify-center mb-3 ${isActive ? 'bg-blue-50 text-blue-600' : 'bg-slate-50 text-slate-400'}`}>
                <Icon className="w-[18px] h-[18px]" />
              </div>
              <p className="text-[15px] font-bold text-slate-900 mb-1">{r.title}</p>
              <p className="text-sm text-slate-500 leading-relaxed">{r.desc}</p>
              <p className="mt-3 text-[11px] font-mono text-slate-400">{r.endpoint}</p>
            </div>
          );
        })}
      </div>

      {/* Filter panel */}
      {activeReport && (
        <div className="bg-white rounded-xl border border-slate-200 p-5 mb-6 animate-slide-down">
          <h3 className="text-sm font-bold text-slate-800 mb-4 flex items-center gap-2">
            <Calendar className="w-4 h-4 text-blue-600" />
            Filtreler
          </h3>

          {activeReport === 'date-range' ? (
            <div className="flex items-end gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Başlangıç</label>
                <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)}
                  className="px-3 py-2 rounded-lg border border-slate-200 text-sm outline-none" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Bitiş</label>
                <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)}
                  className="px-3 py-2 rounded-lg border border-slate-200 text-sm outline-none" />
              </div>
              <button onClick={runReport} disabled={!startDate || !endDate || loading}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 disabled:opacity-50 transition-colors cursor-pointer">
                <Search className="w-3.5 h-3.5" /> Rapor Oluştur
              </button>
            </div>
          ) : (
            <div className="flex items-end gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">
                  {activeReport === 'user' ? 'Kullanıcı ID' : 'Hesap ID'}
                </label>
                <input type="number" value={inputId} onChange={e => setInputId(e.target.value)}
                  placeholder="Örn: 3"
                  className="px-3 py-2 rounded-lg border border-slate-200 text-sm outline-none w-40" />
              </div>
              <button onClick={runReport} disabled={!inputId || loading}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 disabled:opacity-50 transition-colors cursor-pointer">
                <Search className="w-3.5 h-3.5" /> Rapor Oluştur
              </button>
            </div>
          )}
        </div>
      )}

      {/* Results */}
      {loading && <LoadingState message="Rapor oluşturuluyor..." />}

      {!loading && results.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-100">
            <span className="text-sm font-bold text-slate-800">{results.length} sonuç bulundu</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50">
                  {['İşlem ID', 'Tür', 'Tutar', 'Durum', 'Fraud', 'Tarih'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {results.map((tx, i) => (
                  <tr key={i} className="border-b border-slate-50 hover:bg-slate-50/50">
                    <td className="px-4 py-3 font-mono text-xs text-blue-600">{(tx.transactionId || tx.id)?.slice(0, 12)}...</td>
                    <td className="px-4 py-3">
                      <span className="flex items-center gap-1">
                        <span className="text-sm">{TX_TYPE_CONFIG[tx.type]?.icon}</span>
                        {TX_TYPE_CONFIG[tx.type]?.label || tx.type}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-bold font-mono text-[13px]">{formatMoney(tx.amount, tx.currency)}</td>
                    <td className="px-4 py-3"><StatusBadge status={tx.status} /></td>
                    <td className="px-4 py-3">
                      <span className={`font-mono text-xs font-semibold
                        ${tx.fraudScore > 50 ? 'text-red-600' : tx.fraudScore > 25 ? 'text-amber-600' : 'text-emerald-600'}`}>
                        {tx.fraudScore ?? '—'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-400 whitespace-nowrap">
                      {formatDate(tx.completedTimestamp || tx.completedAt || tx.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!loading && activeReport && results.length === 0 && !loading && (
        <EmptyState title="Henüz sonuç yok" description="Filtreleri uygulayıp rapor oluşturun" />
      )}
    </div>
  );
}
