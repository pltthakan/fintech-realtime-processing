import { useState, useEffect } from 'react';
import { userService } from '../services/api';
import { LoadingState } from '../components/ui';
import { Eye, Search } from 'lucide-react';

const ROLE_STYLES = {
  ADMIN:   { color: 'text-red-700',    bg: 'bg-red-50' },
  ANALYST: { color: 'text-violet-700', bg: 'bg-violet-50' },
  USER:    { color: 'text-blue-700',   bg: 'bg-blue-50' },
};

export default function UsersPage() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const res = await userService.getAll();
        setUsers(res.data.data || []);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) return <LoadingState />;

  const filtered = users.filter(u =>
    !search ||
    u.username.toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase()) ||
    `${u.firstName} ${u.lastName}`.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="animate-fade-in">
      <div className="flex items-center justify-between mb-7">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Kullanıcı Yönetimi</h1>
          <p className="text-sm text-slate-500 mt-1">Sistemdeki kullanıcıları yönetin</p>
        </div>
        {/* Search */}
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Kullanıcı ara..."
            className="pl-9 pr-4 py-2 rounded-lg border border-slate-200 bg-white text-sm outline-none w-56 focus:border-blue-500 transition-colors"
          />
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50">
              {['ID', 'Kullanıcı', 'Email', 'Ad Soyad', 'Rol', ''].map(h => (
                <th key={h} className="px-5 py-3 text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.map(u => {
              const rs = ROLE_STYLES[u.role] || ROLE_STYLES.USER;
              return (
                <tr key={u.id} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                  <td className="px-5 py-3.5 font-mono text-slate-400 text-xs">{u.id}</td>
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-2.5">
                      <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-600 to-violet-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                        {u.firstName?.[0] || u.username[0].toUpperCase()}
                      </div>
                      <span className="font-semibold text-slate-800">{u.username}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">{u.email}</td>
                  <td className="px-5 py-3.5 text-slate-800">{u.firstName} {u.lastName}</td>
                  <td className="px-5 py-3.5">
                    <span className={`px-2.5 py-1 rounded-md text-[11px] font-bold ${rs.color} ${rs.bg}`}>
                      {u.role}
                    </span>
                  </td>
                  <td className="px-5 py-3.5">
                    <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-slate-200 text-slate-500 text-xs font-medium hover:bg-slate-50 transition-colors cursor-pointer">
                      <Eye className="w-3.5 h-3.5" /> Detay
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <div className="text-center py-12 text-slate-400 text-sm">Kullanıcı bulunamadı</div>
        )}
      </div>
    </div>
  );
}
