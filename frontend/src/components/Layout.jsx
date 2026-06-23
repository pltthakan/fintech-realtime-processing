import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard, CreditCard, ArrowLeftRight, Send,
  Shield, Users, FileText, ScrollText, LogOut
} from 'lucide-react';

const navItems = [
  { to: '/',             label: 'Dashboard',   icon: LayoutDashboard },
  { to: '/accounts',     label: 'Hesaplarım',  icon: CreditCard },
  { to: '/transactions', label: 'İşlemler',    icon: ArrowLeftRight },
  { to: '/transfer',     label: 'Yeni İşlem',  icon: Send },
];

const adminItems = [
  { to: '/fraud',   label: 'Fraud & AML',    icon: Shield },
  { to: '/users',   label: 'Kullanıcılar',   icon: Users },
  { to: '/reports', label: 'Raporlar',        icon: FileText },
];

const adminOnlyItems = [
  { to: '/audit-logs', label: 'Audit Kayıtları', icon: ScrollText },
];

export default function Layout({ children }) {
  const { user, logout, isPrivileged, isAdmin } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen bg-slate-50">
      {/* ── Sidebar ── */}
      <aside className="w-[250px] bg-sidebar flex flex-col flex-shrink-0">
        {/* Logo */}
        <div className="px-5 pt-6 pb-5 border-b border-white/5">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-[10px] bg-gradient-to-br from-blue-600 to-violet-600 flex items-center justify-center flex-shrink-0">
              <span className="text-white text-base font-extrabold">F</span>
            </div>
            <div>
              <div className="text-white text-[15px] font-bold tracking-tight">FinTech</div>
              <div className="text-slate-500 text-[11px] font-medium">Transaction Engine</div>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-2.5 py-3 overflow-y-auto">
          {navItems.map(item => (
            <SidebarLink key={item.to} {...item} />
          ))}

          {isPrivileged && (
            <>
              <div className="px-3 pt-5 pb-2 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                Yönetim
              </div>
              {adminItems.map(item => (
                <SidebarLink key={item.to} {...item} />
              ))}
              {isAdmin && adminOnlyItems.map(item => (
                <SidebarLink key={item.to} {...item} />
              ))}
            </>
          )}
        </nav>

        {/* User info + logout */}
        <div className="px-3 py-4 border-t border-white/5">
          <div className="flex items-center gap-2.5 mb-2.5 px-1">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-600 to-violet-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
              {user?.firstName?.[0] || user?.username?.[0]?.toUpperCase()}
            </div>
            <div className="min-w-0">
              <div className="text-white text-[13px] font-semibold truncate">
                {user?.firstName ? `${user.firstName} ${user.lastName || ''}` : user?.username}
              </div>
              <div className="text-slate-500 text-[11px]">{user?.role}</div>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-2 px-2.5 py-2 rounded-md bg-red-500/10 text-red-400 text-xs font-semibold hover:bg-red-500/20 transition-colors cursor-pointer"
          >
            <LogOut className="w-3.5 h-3.5" />
            Çıkış Yap
          </button>
        </div>
      </aside>

      {/* ── Main Content ── */}
      <main className="flex-1 overflow-y-auto overflow-x-hidden">
        <div className="px-8 py-7 max-w-[1200px] mx-auto">
          {children}
        </div>
      </main>
    </div>
  );
}

function SidebarLink({ to, label, icon: Icon }) {
  return (
    <NavLink
      to={to}
      end={to === '/'}
      className={({ isActive }) =>
        `flex items-center gap-2.5 px-3 py-2.5 rounded-lg mb-0.5 text-[13px] font-medium transition-all
        ${isActive
          ? 'bg-sidebar-active text-white font-semibold'
          : 'text-slate-400 hover:bg-sidebar-hover hover:text-slate-200'
        }`
      }
    >
      <Icon className="w-[18px] h-[18px]" />
      {label}
    </NavLink>
  );
}
