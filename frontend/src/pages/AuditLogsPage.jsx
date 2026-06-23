import { useEffect, useState } from 'react';
import { auditService } from '../services/api';
import { EmptyState, LoadingState } from '../components/ui';
import { ChevronLeft, ChevronRight, FilterX, RefreshCw, Search, ShieldCheck } from 'lucide-react';

const ACTIONS = {
  ACCOUNT_VIEWED: 'Hesap görüntülendi',
  ACCOUNT_LIST_VIEWED: 'Hesap listesi görüntülendi',
  ACCOUNT_CREATED: 'Hesap oluşturuldu',
  TRANSACTION_VIEWED: 'İşlem görüntülendi',
  TRANSACTION_LIST_VIEWED: 'İşlem listesi görüntülendi',
  TRANSACTION_HISTORY_VIEWED: 'İşlem geçmişi görüntülendi',
  TRANSACTION_CREATED: 'İşlem oluşturuldu',
  AUDIT_LOG_VIEWED: 'Audit kayıtları görüntülendi',
};

const RESOURCE_LABELS = {
  ACCOUNT: 'Hesap',
  USER: 'Kullanıcı',
  TRANSACTION: 'İşlem',
  AUDIT_LOG: 'Audit kaydı',
};

function formatDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('tr-TR', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value));
}

function ActionBadge({ action }) {
  const isWrite = action?.endsWith('_CREATED');
  return (
    <span className={`inline-flex rounded-md px-2 py-1 text-[11px] font-bold ${
      isWrite ? 'bg-violet-50 text-violet-700' : 'bg-blue-50 text-blue-700'
    }`}>
      {ACTIONS[action] || action}
    </span>
  );
}

export default function AuditLogsPage() {
  const [logs, setLogs] = useState([]);
  const [pageData, setPageData] = useState({ page: 0, size: 25, totalElements: 0, totalPages: 0, first: true, last: true });
  const [filters, setFilters] = useState({ actorUsername: '', action: '', resourceType: '' });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadLogs = async (page = 0, showRefreshState = false, requestFilters = filters) => {
    showRefreshState ? setRefreshing(true) : setLoading(true);
    try {
      const response = await auditService.getLogs({ page, size: 25, ...requestFilters });
      const data = response.data.data || {};
      setLogs(data.content || []);
      setPageData({
        page: data.page ?? page,
        size: data.size ?? 25,
        totalElements: data.totalElements ?? 0,
        totalPages: data.totalPages ?? 0,
        first: data.first ?? true,
        last: data.last ?? true,
      });
    } catch (error) {
      console.error('Audit logs fetch error:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => { loadLogs(); }, []);

  const applyFilters = (event) => {
    event.preventDefault();
    loadLogs(0);
  };

  const clearFilters = () => {
    const emptyFilters = { actorUsername: '', action: '', resourceType: '' };
    setFilters(emptyFilters);
    loadLogs(0, false, emptyFilters);
  };

  if (loading) return <LoadingState message="Audit kayıtları yükleniyor..." />;

  return (
    <div className="animate-fade-in">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between mb-7">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-lg bg-violet-50 text-violet-600 flex items-center justify-center">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Audit Kayıtları</h1>
              <p className="text-sm text-slate-500 mt-0.5">Hesap ve işlem erişim hareketlerini inceleyin</p>
            </div>
          </div>
        </div>
        <button
          onClick={() => loadLogs(pageData.page, true)}
          disabled={refreshing}
          className="inline-flex items-center justify-center gap-2 px-3.5 py-2 rounded-lg border border-slate-200 bg-white text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-60 transition-colors"
        >
          <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} /> Yenile
        </button>
      </div>

      <form onSubmit={applyFilters} className="bg-white rounded-xl border border-slate-200 p-4 mb-5">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <div className="relative md:col-span-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              value={filters.actorUsername}
              onChange={(event) => setFilters({ ...filters, actorUsername: event.target.value })}
              placeholder="Kullanıcı ara..."
              className="w-full pl-9 pr-3 py-2.5 rounded-lg border border-slate-200 text-sm outline-none"
            />
          </div>
          <select
            value={filters.action}
            onChange={(event) => setFilters({ ...filters, action: event.target.value })}
            className="w-full px-3 py-2.5 rounded-lg border border-slate-200 bg-white text-sm outline-none appearance-none"
          >
            <option value="">Tüm eylemler</option>
            {Object.entries(ACTIONS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <select
            value={filters.resourceType}
            onChange={(event) => setFilters({ ...filters, resourceType: event.target.value })}
            className="w-full px-3 py-2.5 rounded-lg border border-slate-200 bg-white text-sm outline-none appearance-none"
          >
            <option value="">Tüm kaynaklar</option>
            {Object.entries(RESOURCE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <div className="flex gap-2">
            <button type="submit" className="flex-1 px-3 py-2.5 rounded-lg bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 transition-colors">Filtrele</button>
            <button type="button" onClick={clearFilters} title="Filtreleri temizle" className="px-3 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 transition-colors">
              <FilterX className="w-4 h-4" />
            </button>
          </div>
        </div>
      </form>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
          <p className="text-sm font-semibold text-slate-700">{pageData.totalElements.toLocaleString('tr-TR')} kayıt</p>
          <p className="text-xs text-slate-400">Yalnızca başarılı API eylemleri kaydedilir</p>
        </div>
        {logs.length === 0 ? <EmptyState title="Audit kaydı bulunamadı" description="Filtreleri değiştirerek tekrar deneyin." /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[940px] text-sm">
              <thead>
                <tr className="bg-slate-50">
                  {['Zaman', 'Kullanıcı', 'Eylem', 'Kaynak', 'Servis', 'IP', 'Detay'].map((header) => (
                    <th key={header} className="px-5 py-3 text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{header}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id} className="border-b border-slate-50 hover:bg-slate-50/70 transition-colors">
                    <td className="px-5 py-3.5 whitespace-nowrap text-xs text-slate-500">{formatDate(log.occurredAt)}</td>
                    <td className="px-5 py-3.5">
                      <p className="font-semibold text-slate-800">{log.actorUsername}</p>
                      <p className="text-[11px] text-slate-400">#{log.actorUserId} · {log.actorRole}</p>
                    </td>
                    <td className="px-5 py-3.5"><ActionBadge action={log.action} /></td>
                    <td className="px-5 py-3.5">
                      <p className="font-medium text-slate-700">{RESOURCE_LABELS[log.resourceType] || log.resourceType}</p>
                      <p className="font-mono text-[11px] text-slate-400 truncate max-w-[160px]">{log.resourceId}</p>
                    </td>
                    <td className="px-5 py-3.5 text-xs text-slate-500">{log.serviceName}</td>
                    <td className="px-5 py-3.5 font-mono text-xs text-slate-500">{log.clientIp || '-'}</td>
                    <td className="px-5 py-3.5 text-xs text-slate-500 max-w-[180px] truncate" title={log.details || ''}>{log.details || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {pageData.totalPages > 1 && (
          <div className="px-5 py-3.5 flex items-center justify-between border-t border-slate-100">
            <p className="text-xs text-slate-500">Sayfa {pageData.page + 1} / {pageData.totalPages}</p>
            <div className="flex gap-2">
              <button disabled={pageData.first} onClick={() => loadLogs(pageData.page - 1)} className="inline-flex items-center gap-1 px-3 py-1.5 rounded-md border border-slate-200 text-xs font-medium text-slate-600 disabled:opacity-40 hover:bg-slate-50">
                <ChevronLeft className="w-3.5 h-3.5" /> Önceki
              </button>
              <button disabled={pageData.last} onClick={() => loadLogs(pageData.page + 1)} className="inline-flex items-center gap-1 px-3 py-1.5 rounded-md border border-slate-200 text-xs font-medium text-slate-600 disabled:opacity-40 hover:bg-slate-50">
                Sonraki <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
