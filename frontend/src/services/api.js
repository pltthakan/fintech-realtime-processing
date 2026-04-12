import axios from 'axios';
import toast from 'react-hot-toast';

// Vite proxy /api → http://localhost:8080
const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// ── Request interceptor: JWT token ekle ──
api.interceptors.request.use(
    (config) => {
      const auth = JSON.parse(sessionStorage.getItem('fintech_auth') || 'null');
      if (auth?.accessToken) {
        config.headers.Authorization = `Bearer ${auth.accessToken}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
);

// ── Response interceptor: 401 → refresh token dene ──
api.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config;

      if (error.response?.status === 401 && !originalRequest._retry) {
        originalRequest._retry = true;

        const auth = JSON.parse(sessionStorage.getItem('fintech_auth') || 'null');
        if (auth?.refreshToken) {
          try {
            const res = await axios.post('/api/v1/auth/refresh-token', {
              refreshToken: auth.refreshToken,
            });
            const newAuth = {
              ...auth,
              accessToken: res.data.data.accessToken,
              refreshToken: res.data.data.refreshToken,
            };
            sessionStorage.setItem('fintech_auth', JSON.stringify(newAuth));
            originalRequest.headers.Authorization = `Bearer ${newAuth.accessToken}`;
            return api(originalRequest);
          } catch (refreshError) {
            sessionStorage.removeItem('fintech_auth');
            window.location.href = '/login';
            return Promise.reject(refreshError);
          }
        }
      }

      const message =
          error.response?.data?.message ||
          error.response?.data?.error?.detail ||
          error.message ||
          'Bir hata oluştu';

      if (error.response?.status !== 401) {
        toast.error(message);
      }

      return Promise.reject(error);
    }
);

// ── API Servisleri ──

export const authService = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  refreshToken: (data) => api.post('/auth/refresh-token', data),
};

export const userService = {
  getById: (id) => api.get(`/users/${id}`),
  getByUsername: (username) => api.get(`/users/username/${username}`),
  getAll: () => api.get('/users'),
  update: (id, data) => api.put(`/users/${id}`, data),
  delete: (id) => api.delete(`/users/${id}`),
};

export const accountService = {
  getById: (id) => api.get(`/accounts/${id}`),
  getByUserId: (userId) => api.get(`/accounts/user/${userId}`),
  getByNumber: (number) => api.get(`/accounts/number/${number}`),
  create: (data) => api.post('/accounts', data),
};

export const transactionService = {
  create: (data) => api.post('/transactions', data),
  getById: (id) => api.get(`/transactions/${id}`),
  getByAccount: (accountId, page = 0, size = 20) =>
      api.get(`/transactions/account/${accountId}`, { params: { page, size } }),
  getHistory: (id) => api.get(`/transactions/${id}/history`),
};

export const reportService = {
  getDashboard: () => api.get('/reports/dashboard'),
  getByUser: (userId, page = 0, size = 20) =>
      api.get(`/reports/user/${userId}`, { params: { page, size } }),
  getByAccount: (accountId, page = 0, size = 20) =>
      api.get(`/reports/account/${accountId}`, { params: { page, size } }),
  getByDateRange: (startDate, endDate) =>
      api.get('/reports/date-range', { params: { startDate, endDate } }),
  getSuspicious: () => api.get('/reports/suspicious'),
  getBlocked: () => api.get('/reports/blocked'),
  getTransaction: (id) => api.get(`/reports/transaction/${id}`),
};

export default api;