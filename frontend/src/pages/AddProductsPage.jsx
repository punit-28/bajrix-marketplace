import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { useOutletContext } from 'react-router-dom';
import { api } from '../lib/api.js';
import { formatPrice, plural } from '../lib/format.js';
import { useDebounced } from '../lib/hooks.js';
import { useSeller } from '../lib/seller.jsx';
import { EmptyState, ErrorState, Pagination, Skeleton } from '../components/ui.jsx';
import ListingDialog from '../components/ListingDialog.jsx';

export default function AddProductsPage() {
  const { me } = useOutletContext();
  const { sellerId } = useSeller();
  const [text, setText] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const q = useDebounced(text.trim());

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories, staleTime: 5 * 60_000 });
  const query = useQuery({
    queryKey: ['sellerCatalog', sellerId, { q, category, page }],
    queryFn: () => api.sellerCatalog(sellerId, { q, categoryId: category, page, size: 12 }),
    placeholderData: keepPreviousData,
  });

  const data = query.data;

  return (
    <section>
      <div className="toolbar">
        <label className="searchbar searchbar--small">
          <Search size={16} aria-hidden />
          <input type="search" placeholder="Find a product to sell, e.g. TMT bar" aria-label="Search the catalogue" value={text} onChange={(e) => { setText(e.target.value); setPage(0); }} />
        </label>
        <label className="inline-select">
          Category
          <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}>
            <option value="">All categories</option>
            {(categories.data ?? []).map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </label>
      </div>

      {query.isPending ? (
        <Skeleton rows={6} />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={query.refetch} />
      ) : data.items.length === 0 ? (
        <EmptyState title="Nothing found in the catalogue">
          Check the spelling or try a broader search. Missing a product? Catalogue additions are handled by the BajriX team.
        </EmptyState>
      ) : (
        <>
          <ul className="rows rows--seller">
            {data.items.map((p) => (
              <li key={p.id} className="row row--static">
                <div className="row__main">
                  <h3>{p.name}</h3>
                  <p>{p.brand} · {p.categoryName} · sold per {p.unit}</p>
                </div>
                <div className="row__sellers">
                  <strong>{p.marketMinPrice !== null ? `from ${formatPrice(p.marketMinPrice)}` : 'No price yet'}</strong>
                  <span>{plural(p.marketOfferCount, 'seller')} today</span>
                </div>
                <div className="row__action">
                  {p.listingStatus === 'ACTIVE' ? (
                    <span className="badge badge--approved">You sell this</span>
                  ) : (
                    <button className="btn btn--primary" disabled={!me.canManageListings} onClick={() => setSelected(p)}>
                      {p.listingStatus === 'INACTIVE' ? 'Resume selling' : 'Sell this'}
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}

      {selected && <ListingDialog mode="create" product={selected} onClose={() => setSelected(null)} onReload={() => {}} />}
    </section>
  );
}
