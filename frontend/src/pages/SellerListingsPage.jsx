import { useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, PauseCircle, PlayCircle, Search } from 'lucide-react';
import { api } from '../lib/api.js';
import { formatNumber, formatPrice, plural, timeAgo } from '../lib/format.js';
import { useDebounced } from '../lib/hooks.js';
import { useSeller } from '../lib/seller.jsx';
import { EmptyState, ErrorState, Pagination, Skeleton } from '../components/ui.jsx';
import { useToast } from '../components/Toast.jsx';
import ListingDialog from '../components/ListingDialog.jsx';

const FILTERS = [
  { value: '', label: 'All' },
  { value: 'ACTIVE', label: 'Selling' },
  { value: 'INACTIVE', label: 'Stopped' },
];

function MarketPosition({ listing }) {
  const { price, marketMinPrice } = listing;
  if (marketMinPrice === null || marketMinPrice === undefined) return <span className="muted">No buyer-visible price yet</span>;
  if (Number(price) <= Number(marketMinPrice)) return <span className="tag tag--best">Lowest price</span>;
  return <span className="muted">{formatPrice(Number(price) - Number(marketMinPrice))} above lowest ({formatPrice(marketMinPrice)})</span>;
}

function StockCell({ listing }) {
  if (listing.stock === 0) return <span className="tone-bad">Out of stock</span>;
  return (
    <>
      <strong>{formatNumber(listing.stock)}</strong> {listing.unit}
      {!listing.orderable && <span className="tone-warn block">Below your minimum order</span>}
    </>
  );
}

export default function SellerListingsPage() {
  const { me } = useOutletContext();
  const { sellerId } = useSeller();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [status, setStatus] = useState('');
  const [text, setText] = useState('');
  const [page, setPage] = useState(0);
  const [editingId, setEditingId] = useState(null);
  const q = useDebounced(text.trim());

  const query = useQuery({
    queryKey: ['listings', sellerId, { status, q, page }],
    queryFn: () => api.myListings(sellerId, { status, q, page, size: 15 }),
    placeholderData: keepPreviousData,
  });

  const toggle = useMutation({
    mutationFn: ({ listing, next }) => api.setListingStatus(sellerId, listing.id, { version: listing.version, status: next }),
    onSuccess: (saved) => {
      toast.push(saved.status === 'INACTIVE' ? `You stopped selling ${saved.productName}.` : `You are selling ${saved.productName} again.`);
      queryClient.invalidateQueries({ queryKey: ['listings'] });
      queryClient.invalidateQueries({ queryKey: ['sellerCatalog'] });
    },
    onError: (error) => {
      toast.push(error.code === 'STALE_LISTING' ? 'This listing changed elsewhere. The list has been refreshed, please try again.' : error.message, 'error');
      queryClient.invalidateQueries({ queryKey: ['listings'] });
    },
  });

  const data = query.data;
  const editing = data?.items.find((l) => l.id === editingId);
  const canEdit = me.canManageListings;
  const filtered = Boolean(status || q);

  return (
    <section>
      <div className="toolbar">
        <div className="segmented" role="group" aria-label="Filter listings">
          {FILTERS.map((f) => (
            <button key={f.value} aria-pressed={status === f.value} className={status === f.value ? 'on' : ''} onClick={() => { setStatus(f.value); setPage(0); }}>
              {f.label}
            </button>
          ))}
        </div>
        <label className="searchbar searchbar--small">
          <Search size={16} aria-hidden />
          <input type="search" placeholder="Search your listings" aria-label="Search your listings" value={text} onChange={(e) => { setText(e.target.value); setPage(0); }} />
        </label>
      </div>

      {query.isPending ? (
        <Skeleton rows={5} />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={query.refetch} />
      ) : data.items.length === 0 ? (
        <EmptyState
          title={filtered ? 'No listings match' : 'You have no listings yet'}
          action={!filtered && canEdit && <Link className="btn btn--primary" to="/sell/add">Add your first product</Link>}
        >
          {filtered ? 'Try a different filter or search.' : 'Pick products from the catalogue and set your price, stock and minimum order.'}
        </EmptyState>
      ) : (
        <>
          <p className="muted count">{plural(data.totalItems, 'listing')}</p>
          <table className="listings">
            <thead>
              <tr>
                <th scope="col">Product</th>
                <th scope="col">Your price</th>
                <th scope="col">Stock</th>
                <th scope="col">Min. order</th>
                <th scope="col">Status</th>
                <th scope="col"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((l) => (
                <tr key={l.id} className={l.status === 'INACTIVE' ? 'offer--muted' : ''}>
                  <td data-label="Product">
                    <strong>{l.productName}</strong>
                    <span className="muted block">{l.brand} · updated {timeAgo(l.updatedAt)}</span>
                  </td>
                  <td data-label="Your price">
                    <strong className="price">{formatPrice(l.price)}</strong> <span className="muted">per {l.unit}</span>
                    <span className="block"><MarketPosition listing={l} /></span>
                  </td>
                  <td data-label="Stock"><StockCell listing={l} /></td>
                  <td data-label="Min. order">{formatNumber(l.minOrderQty)}</td>
                  <td data-label="Status">
                    <span className={`badge badge--${l.status === 'ACTIVE' ? 'approved' : 'stopped'}`}>{l.status === 'ACTIVE' ? 'Selling' : 'Stopped'}</span>
                  </td>
                  <td className="actions">
                    <button className="btn btn--ghost" disabled={!canEdit} onClick={() => setEditingId(l.id)}>
                      <Pencil size={15} aria-hidden /> Edit
                    </button>
                    {l.status === 'ACTIVE' ? (
                      <button className="btn btn--ghost" disabled={!canEdit || toggle.isPending} onClick={() => toggle.mutate({ listing: l, next: 'INACTIVE' })}>
                        <PauseCircle size={15} aria-hidden /> Stop selling
                      </button>
                    ) : (
                      <button className="btn btn--ghost" disabled={!canEdit || toggle.isPending} onClick={() => toggle.mutate({ listing: l, next: 'ACTIVE' })}>
                        <PlayCircle size={15} aria-hidden /> Resume
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}

      {editing && (
        <ListingDialog
          key={`${editing.id}-${editing.version}`}
          mode="edit"
          listing={editing}
          onClose={() => setEditingId(null)}
          onReload={() => queryClient.invalidateQueries({ queryKey: ['listings'] })}
        />
      )}
    </section>
  );
}
