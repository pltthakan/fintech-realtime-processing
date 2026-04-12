import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import AccountsPage from './pages/AccountsPage';
import TransactionsPage from './pages/TransactionsPage';
import TransferPage from './pages/TransferPage';
import FraudPage from './pages/FraudPage';
import UsersPage from './pages/UsersPage';
import ReportsPage from './pages/ReportsPage';

// ── Protected Route ──
function ProtectedRoute({ children, adminOnly = false }) {
  const { user, isPrivileged } = useAuth();

  if (!user) return <Navigate to="/login" replace />;
  if (adminOnly && !isPrivileged) return <Navigate to="/" replace />;

  return <Layout>{children}</Layout>;
}

// ── Public Route (giriş yapmışsa yönlendir) ──
function PublicRoute({ children }) {
  const { user } = useAuth();
  if (user) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      {/* Auth sayfaları */}
      <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
      <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />

      {/* Korumalı sayfalar */}
      <Route path="/" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
      <Route path="/accounts" element={<ProtectedRoute><AccountsPage /></ProtectedRoute>} />
      <Route path="/transactions" element={<ProtectedRoute><TransactionsPage /></ProtectedRoute>} />
      <Route path="/transfer" element={<ProtectedRoute><TransferPage /></ProtectedRoute>} />

      {/* Admin/Analyst sayfaları */}
      <Route path="/fraud" element={<ProtectedRoute adminOnly><FraudPage /></ProtectedRoute>} />
      <Route path="/users" element={<ProtectedRoute adminOnly><UsersPage /></ProtectedRoute>} />
      <Route path="/reports" element={<ProtectedRoute adminOnly><ReportsPage /></ProtectedRoute>} />

      {/* 404 → Dashboard */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
