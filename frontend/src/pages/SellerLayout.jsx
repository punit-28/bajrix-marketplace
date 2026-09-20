import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api.js';
import { useSeller } from '../lib/seller.jsx';
import { ErrorState, SellerStatusBadge, Skeleton } from '../components/ui.jsx';
import { useEffect } from 'react';

const BANNERS = {
  PENDING: 'Your seller account is waiting for approval. You can prepare listings now, but buyers will not see them until you are approved.',
  REJECTED: 'Your seller account was not approved. Your listings are hidden from buyers and cannot be changed.',
};

function SellerPicker({ sellers, onPick }) {
  return (
    <section className="picker">
      <h1>Choose who you are selling as</h1>
      <p className="muted">
        Demo sign-in: pick a seller to manage their listings. Different statuses show how approval affects what buyers see.
      </p>
      <ul className="picker__list">
        {sellers.map((s) => (
          <li key={s.id}>
            <button onClick={() => onPick(s.id)}>
              <strong>{s.name}</strong>
              <span className="muted">{s.city}</span>
              <SellerStatusBadge status={s.status} />
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function SellerLayout() {
  const { sellerId, setSellerId } = useSeller();

  const sellers = useQuery({ queryKey: ['sellers'], queryFn: api.sellers });
  const profile = useQuery({
    queryKey: ['seller', sellerId],
    queryFn: () => api.me(sellerId),
    enabled: Boolean(sellerId),
    retry: false,
  });

  // A remembered seller that no longer exists sends the person back to the picker.
  useEffect(() => {
    if (profile.error?.status === 401) setSellerId(null);
  }, [profile.error, setSellerId]);

  if (sellers.isPending) return <Skeleton rows={4} />;
  if (sellers.isError) return <ErrorState error={sellers.error} onRetry={sellers.refetch} />;
  if (!sellerId) return <SellerPicker sellers={sellers.data} onPick={setSellerId} />;
  if (profile.isPending) return <Skeleton rows={4} />;
  if (profile.isError) return <ErrorState error={profile.error} onRetry={profile.refetch} />;

  const me = profile.data;

  return (
    <div className="seller">
      <div className="seller__bar">
        <div>
          <label className="seller__as">
            Selling as
            <select value={sellerId} onChange={(e) => setSellerId(e.target.value)}>
              {sellers.data.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.city})
                </option>
              ))}
            </select>
          </label>
          <SellerStatusBadge status={me.status} />
        </div>
        <nav className="tabs" aria-label="Seller sections">
          <NavLink to="/sell" end>
            My listings
          </NavLink>
          <NavLink to="/sell/add">Add products</NavLink>
        </nav>
      </div>

      {BANNERS[me.status] && (
        <div className={`banner banner--${me.status.toLowerCase()}`} role="note">
          {BANNERS[me.status]}
        </div>
      )}

      <Outlet context={{ me }} />
    </div>
  );
}
