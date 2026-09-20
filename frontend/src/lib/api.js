// Thin fetch wrapper. Errors are normalised into ApiError so screens can react to
// status/code (e.g. STALE_LISTING) and to per-field validation messages.

const BASE = import.meta.env.VITE_API_BASE ?? '';

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || 'Something went wrong.');
    this.status = status;
    this.code = body?.code || 'UNKNOWN';
    this.fieldErrors = body?.fieldErrors || {};
  }
}

function buildUrl(path, params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '' && value !== false) query.set(key, String(value));
  });
  const qs = query.toString();
  return `${BASE}${path}${qs ? `?${qs}` : ''}`;
}

async function request(path, { method = 'GET', params, body, sellerId } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (sellerId) headers['X-Seller-Id'] = String(sellerId); // mocked auth, see backend SellerContextArgumentResolver

  let response;
  try {
    response = await fetch(buildUrl(path, params), {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, { code: 'NETWORK', message: 'Cannot reach the server. Check your connection and try again.' });
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!response.ok) throw new ApiError(response.status, data);
  return data;
}

export const api = {
  categories: () => request('/api/categories'),
  products: (params) => request('/api/products', { params }),
  product: (id) => request(`/api/products/${id}`),

  sellers: () => request('/api/sellers'),
  me: (sellerId) => request('/api/seller/me', { sellerId }),
  myListings: (sellerId, params) => request('/api/seller/listings', { sellerId, params }),
  sellerCatalog: (sellerId, params) => request('/api/seller/catalog', { sellerId, params }),
  createListing: (sellerId, body) => request('/api/seller/listings', { method: 'POST', sellerId, body }),
  updateListing: (sellerId, id, body) => request(`/api/seller/listings/${id}`, { method: 'PATCH', sellerId, body }),
  setListingStatus: (sellerId, id, body) =>
    request(`/api/seller/listings/${id}/status`, { method: 'PUT', sellerId, body }),
};
