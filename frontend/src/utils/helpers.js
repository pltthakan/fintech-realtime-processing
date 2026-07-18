// ── Para formatı ──
export function formatMoney(value, currency = 'TRY') {
  const n = Number(value);
  if (isNaN(n)) return '—';
  const symbols = { TRY: '₺', USD: '$', EUR: '€', GBP: '£' };
  const sym = symbols[currency] || currency;
  return `${sym}${n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

// ── Tarih formatı ──
export function formatDate(d) {
  if (!d) return '—';
  return new Date(d).toLocaleDateString('tr-TR', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export function formatShortDate(d) {
  if (!d) return '—';
  return new Date(d).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short' });
}

// ── Transaction Status Map ──
export const STATUS_CONFIG = {
  PENDING:     { label: 'Bekliyor',       color: 'text-slate-500',  bg: 'bg-slate-100',  dot: 'bg-slate-500' },
  VALIDATED:   { label: 'Doğrulandı',     color: 'text-blue-600',   bg: 'bg-blue-50',    dot: 'bg-blue-600' },
  FRAUD_CHECK: { label: 'Fraud Kontrol',  color: 'text-amber-600',  bg: 'bg-amber-50',   dot: 'bg-amber-500' },
  CHECKED:     { label: 'Kontrol Edildi', color: 'text-violet-600', bg: 'bg-violet-50',  dot: 'bg-violet-500' },
  BLOCKED:     { label: 'Engellendi',     color: 'text-red-600',    bg: 'bg-red-50',     dot: 'bg-red-500' },
  PROCESSING:  { label: 'İşleniyor',     color: 'text-amber-600',  bg: 'bg-amber-50',   dot: 'bg-amber-500' },
  PROCESSED:   { label: 'İşlendi',       color: 'text-cyan-600',   bg: 'bg-cyan-50',    dot: 'bg-cyan-500' },
  COMPLETED:   { label: 'Tamamlandı',    color: 'text-emerald-600',bg: 'bg-emerald-50', dot: 'bg-emerald-500' },
  FAILED:      { label: 'Başarısız',     color: 'text-red-600',    bg: 'bg-red-50',     dot: 'bg-red-500' },
  CANCELLED:   { label: 'İptal',         color: 'text-gray-500',   bg: 'bg-gray-50',    dot: 'bg-gray-400' },
};

// ── Transaction Type Map ──
export const TX_TYPE_CONFIG = {
  TRANSFER:   { label: 'Transfer',  icon: '↔', iconBg: 'bg-blue-50',    iconColor: 'text-blue-600' },
  PAYMENT:    { label: 'Ödeme',     icon: '→', iconBg: 'bg-violet-50',  iconColor: 'text-violet-600' },
  DEPOSIT:    { label: 'Yatırma',   icon: '↓', iconBg: 'bg-emerald-50', iconColor: 'text-emerald-600' },
  WITHDRAWAL: { label: 'Çekme',     icon: '↑', iconBg: 'bg-red-50',     iconColor: 'text-red-600' },
};

export function transactionAmountMeta(transaction) {
  const direction = transaction.direction
      || (transaction.type === 'DEPOSIT'
          ? 'CREDIT'
          : ['PAYMENT', 'WITHDRAWAL'].includes(transaction.type) ? 'DEBIT' : null);

  if (direction === 'CREDIT') {
    return { sign: '+', color: 'text-emerald-600' };
  }
  if (direction === 'DEBIT') {
    return { sign: '-', color: 'text-red-600' };
  }
  if (direction === 'NEUTRAL') {
    return { sign: '', color: 'text-blue-600' };
  }
  return { sign: '', color: 'text-slate-900' };
}

export function transactionDisplayLabel(transaction) {
  if (transaction.type !== 'TRANSFER') {
    return TX_TYPE_CONFIG[transaction.type]?.label || transaction.type;
  }

  const railLabels = {
    HAVALE: 'Havale',
    EFT: 'EFT',
    FAST: 'FAST',
    INTERNAL: 'Transfer',
  };
  const railLabel = railLabels[transaction.transferRail] || 'Transfer';

  if (transaction.direction === 'CREDIT') return `Gelen ${railLabel}`;
  if (transaction.direction === 'DEBIT') return `Giden ${railLabel}`;
  if (transaction.direction === 'NEUTRAL') return 'Hesaplar Arası Transfer';
  return railLabel;
}

// ── Account Type Labels ──
export const ACCOUNT_TYPE_LABELS = {
  CHECKING: 'Vadesiz',
  SAVINGS: 'Birikim',
  INVESTMENT: 'Yatırım',
};

// ── Currency Symbols ──
export const CURRENCY_SYMBOLS = {
  TRY: '₺', USD: '$', EUR: '€', GBP: '£',
};
