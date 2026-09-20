import { createContext, useCallback, useContext, useMemo, useState } from 'react';

// Mocked login: the chosen seller id is remembered in localStorage and sent as X-Seller-Id.
const KEY = 'bajrix.sellerId';
const SellerContext = createContext(null);

function readStored() {
  try {
    return localStorage.getItem(KEY) || null;
  } catch {
    return null;
  }
}

export function SellerProvider({ children }) {
  const [sellerId, setId] = useState(readStored);

  const setSellerId = useCallback((id) => {
    setId(id ? String(id) : null);
    try {
      if (id) localStorage.setItem(KEY, String(id));
      else localStorage.removeItem(KEY);
    } catch {
      /* storage unavailable: selection just won't persist */
    }
  }, []);

  const value = useMemo(() => ({ sellerId, setSellerId }), [sellerId, setSellerId]);
  return <SellerContext.Provider value={value}>{children}</SellerContext.Provider>;
}

export function useSeller() {
  const ctx = useContext(SellerContext);
  if (!ctx) throw new Error('useSeller must be used inside SellerProvider');
  return ctx;
}
