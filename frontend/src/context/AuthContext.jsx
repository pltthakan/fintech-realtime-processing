import { createContext, useContext, useState, useEffect } from 'react';
import { authService } from '../services/api';
import toast from 'react-hot-toast';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Sayfa yüklendiğinde session kontrol et
  useEffect(() => {
    const saved = sessionStorage.getItem('fintech_auth');
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        setUser(parsed.user);
      } catch {
        sessionStorage.removeItem('fintech_auth');
      }
    }
    setLoading(false);
  }, []);

  const login = async (usernameOrEmail, password) => {
    const res = await authService.login({ usernameOrEmail, password });
    const { accessToken, user: userData } = res.data.data;
    const authData = { accessToken, user: userData };
    sessionStorage.setItem('fintech_auth', JSON.stringify(authData));
    setUser(userData);
    toast.success(`Hoş geldin, ${userData.firstName || userData.username}!`);
    return userData;
  };

  const register = async (data) => {
    const res = await authService.register(data);
    const { accessToken, user: userData } = res.data.data;
    const authData = { accessToken, user: userData };
    sessionStorage.setItem('fintech_auth', JSON.stringify(authData));
    setUser(userData);
    toast.success('Kayıt başarılı!');
    return userData;
  };

  const logout = async () => {
    try {
      await authService.logout();
    } finally {
      sessionStorage.removeItem('fintech_auth');
      setUser(null);
      toast.success('Çıkış yapıldı');
    }
  };

  const isAdmin = user?.role === 'ADMIN';
  const isAnalyst = user?.role === 'ANALYST';
  const isPrivileged = isAdmin || isAnalyst;

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="w-8 h-8 border-3 border-slate-200 border-t-blue-600 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout, isAdmin, isAnalyst, isPrivileged }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
