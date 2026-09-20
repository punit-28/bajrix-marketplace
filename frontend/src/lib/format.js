const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 0, maximumFractionDigits: 2 });
const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const num = new Intl.NumberFormat('en-IN');

/** ₹1,23,456 – shows paise only when they exist (₹9.50, ₹385). */
export function formatPrice(value) {
  if (value === null || value === undefined) return '–';
  return inr.format(value);
}

/** Always two decimals; used for totals so columns line up. */
export function formatMoney(value) {
  if (value === null || value === undefined) return '–';
  return inrWhole.format(value);
}

export function formatNumber(value) {
  if (value === null || value === undefined) return '–';
  return num.format(value);
}

export function plural(count, one, many = `${one}s`) {
  return `${formatNumber(count)} ${count === 1 ? one : many}`;
}

export function timeAgo(iso, now = Date.now()) {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  return `${days} ${days === 1 ? 'day' : 'days'} ago`;
}
