import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { AlertCircle } from 'lucide-react';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ usernameOrEmail: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(form.usernameOrEmail, form.password);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Giriş başarısız');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex bg-gradient-to-br from-sidebar via-slate-800 to-sidebar font-sans">
      {/* Left - Branding */}
      <div className="flex-1 flex items-center justify-center p-10 relative overflow-hidden">
        <div className="absolute -top-24 -left-24 w-96 h-96 rounded-full bg-blue-600/[0.07] blur-[80px]" />
        <div className="absolute -bottom-12 -right-12 w-72 h-72 rounded-full bg-violet-600/[0.05] blur-[60px]" />
        <div className="relative z-10 max-w-md">
          <div className="flex items-center gap-3 mb-8">
            <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-blue-600 to-violet-600 flex items-center justify-center">
              <span className="text-white text-xl font-extrabold">F</span>
            </div>
            <span className="text-white text-[22px] font-bold tracking-tight">FinTech</span>
          </div>
          <h1 className="text-white text-4xl font-extrabold leading-tight tracking-tight">
            Gerçek Zamanlı<br />
            <span className="bg-gradient-to-r from-blue-500 to-violet-500 bg-clip-text text-transparent">
              Finansal İşlem
            </span><br />
            İşleme Sistemi
          </h1>
          <p className="text-slate-400 text-[15px] leading-relaxed mt-5 max-w-sm">
            Kafka pipeline ile event-driven mikro servis mimarisi. Her işleminiz gerçek zamanlı
            olarak izlenir, doğrulanır ve güvenle tamamlanır.
          </p>
          {/* Pipeline mini viz */}
          <div className="mt-8 flex items-center gap-1.5">
            {['A', 'B', 'C', 'D'].map((step, i) => (
              <div key={step} className="flex items-center gap-1.5">
                <div className="w-8 h-8 rounded-lg border border-blue-500/30 flex items-center justify-center text-blue-300 text-xs font-bold font-mono"
                     style={{ background: `rgba(37,99,235,${0.1 + i * 0.05})` }}>
                  {step}
                </div>
                {i < 3 && <div className="w-5 h-px bg-blue-500/30" />}
              </div>
            ))}
            <div className="ml-1.5 w-8 h-8 rounded-lg bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400 text-xs">
              ✓
            </div>
          </div>
        </div>
      </div>

      {/* Right - Form */}
      <div className="w-[480px] flex items-center justify-center p-10">
        <div className="w-full max-w-[380px] bg-white rounded-2xl p-9 shadow-2xl">
          <h2 className="text-2xl font-bold text-slate-900 tracking-tight mb-1">Hoş Geldiniz</h2>
          <p className="text-sm text-slate-500 mb-7">Hesabınıza giriş yapın</p>

          {error && (
            <div className="flex items-center gap-2 p-3 rounded-lg bg-red-50 border border-red-200 text-red-600 text-sm mb-5">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="mb-4">
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Kullanıcı Adı / Email</label>
              <input
                type="text"
                value={form.usernameOrEmail}
                onChange={e => setForm({ ...form, usernameOrEmail: e.target.value })}
                placeholder="ornek@fintech.com"
                className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none transition-colors"
                required
              />
            </div>
            <div className="mb-6">
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">Şifre</label>
              <input
                type="password"
                value={form.password}
                onChange={e => setForm({ ...form, password: e.target.value })}
                placeholder="••••••••"
                className="w-full px-3.5 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none transition-colors"
                required
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-lg bg-gradient-to-r from-blue-600 to-violet-600 text-white text-[15px] font-semibold
                         hover:shadow-lg hover:shadow-blue-600/25 active:scale-[0.98] transition-all disabled:opacity-60 cursor-pointer"
            >
              {loading ? 'Giriş yapılıyor...' : 'Giriş Yap'}
            </button>
          </form>

          <p className="text-center text-sm text-slate-500 mt-5">
            Hesabınız yok mu?{' '}
            <Link to="/register" className="text-blue-600 font-semibold hover:underline">
              Kayıt Ol
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
