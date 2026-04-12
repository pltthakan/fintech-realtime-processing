import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { AlertCircle } from 'lucide-react';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    username: '', email: '', password: '',
    firstName: '', lastName: '', phoneNumber: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await register(form);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Kayıt başarısız');
    } finally {
      setLoading(false);
    }
  };

  const set = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-sidebar via-slate-800 to-sidebar font-sans">
      <div className="w-[420px] bg-white rounded-2xl p-9 shadow-2xl">
        <div className="flex items-center gap-2.5 mb-6">
          <div className="w-9 h-9 rounded-[10px] bg-gradient-to-br from-blue-600 to-violet-600 flex items-center justify-center">
            <span className="text-white text-base font-extrabold">F</span>
          </div>
          <span className="text-lg font-bold text-slate-900">Kayıt Ol</span>
        </div>

        {error && (
          <div className="flex items-center gap-2 p-3 rounded-lg bg-red-50 border border-red-200 text-red-600 text-sm mb-4">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-2 gap-3 mb-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Ad</label>
              <input value={form.firstName} onChange={e => set('firstName', e.target.value)}
                placeholder="Ahmet" className="w-full px-3 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Soyad</label>
              <input value={form.lastName} onChange={e => set('lastName', e.target.value)}
                placeholder="Yılmaz" className="w-full px-3 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none" />
            </div>
          </div>
          {[
            { key: 'username', label: 'Kullanıcı Adı *', placeholder: 'kullanici_adi', type: 'text' },
            { key: 'email', label: 'Email *', placeholder: 'ornek@fintech.com', type: 'email' },
            { key: 'password', label: 'Şifre *', placeholder: 'En az 8 karakter', type: 'password' },
            { key: 'phoneNumber', label: 'Telefon', placeholder: '+90 5xx xxx xx xx', type: 'text' },
          ].map(f => (
            <div key={f.key} className="mb-3">
              <label className="block text-xs font-semibold text-slate-700 mb-1">{f.label}</label>
              <input
                type={f.type}
                value={form[f.key]}
                onChange={e => set(f.key, e.target.value)}
                placeholder={f.placeholder}
                required={f.label.includes('*')}
                className="w-full px-3 py-2.5 rounded-lg border-[1.5px] border-slate-200 bg-slate-50 text-sm outline-none"
              />
            </div>
          ))}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 mt-2 rounded-lg bg-gradient-to-r from-blue-600 to-violet-600 text-white text-[15px] font-semibold
                       hover:shadow-lg hover:shadow-blue-600/25 active:scale-[0.98] transition-all disabled:opacity-60 cursor-pointer"
          >
            {loading ? 'Kayıt yapılıyor...' : 'Kayıt Ol'}
          </button>
        </form>

        <p className="text-center text-sm text-slate-500 mt-4">
          Zaten hesabınız var mı?{' '}
          <Link to="/login" className="text-blue-600 font-semibold hover:underline">Giriş Yap</Link>
        </p>
      </div>
    </div>
  );
}
