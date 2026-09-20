import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { api } from '../lib/api.js';
import { formatPrice, plural } from '../lib/format.js';
import { useDebounced } from '../lib/hooks.js';
import { EmptyState, ErrorState, Pagination, Skeleton } from '../components/ui.jsx';

const SORTS = [
  { value: 'relevance', label: 'Best match' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'name', label: 'Name A–Z' },
];

const TILE_COLORS = ['#DDE7EC', '#E6E4D6', '#E4DDE6', '#D9E8DF', '#EBE0D6', '#DCE2EE', '#E9E3DA', '#DFE9E6'];

export function ProductTile({ name, brand, categoryId }) {
  const letters = (brand && brand !== 'Generic' && brand !== 'Local' ? brand : name).replace(/[^A-Za-z]/g, '').slice(0, 2);
  return (
    <span className="tile" style={{ background: TILE_COLORS[(categoryId ?? 0) % TILE_COLORS.length] }} aria-hidden>
      {letters}
    </span>
  );
}

function PriceBlock({ product }) {
  if (product.minPrice === null) {
    return (
      <div className="row__price row__price--none">
        <strong>Out of stock</strong>
        <span>{plural(product.offerCount, 'seller')} listed</span>
      </div>
    );
  }
  const range = product.maxPrice && product.maxPrice !== product.minPrice ? `up to ${formatPrice(product.maxPrice)}` : null;
  return (
    <div className="row__price">
      <span className="row__price-label">from</span>
      <strong>{formatPrice(product.minPrice)}</strong>
      <span className="row__price-unit">per {product.unit}</span>
      {range && <span className="row__price-range">{range}</span>}
    </div>
  );
}

export default function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const category = params.get('category') ?? '';
  const sort = params.get('sort') ?? 'relevance';
  const inStock = params.get('inStock') === '1';
  const min = params.get('min') ?? '';
  const max = params.get('max') ?? '';
  const page = Number(params.get('page') ?? 0) || 0;

  const update = (patch) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v === '' || v === null || v === false ? next.delete(k) : next.set(k, String(v))));
    if (!('page' in patch)) next.delete('page');
    setParams(next, { replace: true });
  };

  // Search box: type freely, URL/query updates shortly after typing stops.
  const [text, setText] = useState(q);
  const debounced = useDebounced(text);
  useEffect(() => {
    if (debounced.trim() !== q) update({ q: debounced.trim() });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);
  useEffect(() => setText(q), [q]);

  const [minText, setMinText] = useState(min);
  const [maxText, setMaxText] = useState(max);
  useEffect(() => setMinText(min), [min]);
  useEffect(() => setMaxText(max), [max]);
  const applyPrice = () => update({ min: minText.trim(), max: maxText.trim() });

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories, staleTime: 5 * 60_000 });

  const query = useQuery({
    queryKey: ['products', { q, category, sort, inStock, min, max, page }],
    queryFn: () => api.products({ q, categoryId: category, sort, inStockOnly: inStock, minPrice: min, maxPrice: max, page, size: 12 }),
    placeholderData: keepPreviousData,
  });

  const data = query.data;
  const hasFilters = q || category || inStock || min || max;

  return (
    <div className="catalog">
      <section className="hero">
        <h1>Compare local sellers before you buy</h1>
        <p>Cement, steel, bricks and more. See who has stock, at what price, and the minimum order.</p>
        <form className="searchbar" role="search" onSubmit={(e) => e.preventDefault()}>
          <Search size={18} aria-hidden />
          <input
            type="search"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Search by product or brand, e.g. ultratech cement"
            aria-label="Search products"
          />
        </form>
      </section>

      <div className="catalog__grid">
        <aside className="filters" aria-label="Filters">
          <h2>Category</h2>
          <ul className="chips">
            <li>
              <button className={`chip${category === '' ? ' chip--on' : ''}`} onClick={() => update({ category: '' })}>
                All
              </button>
            </li>
            {(categories.data ?? []).map((c) => (
              <li key={c.id}>
                <button
                  className={`chip${category === String(c.id) ? ' chip--on' : ''}`}
                  aria-pressed={category === String(c.id)}
                  onClick={() => update({ category: c.id })}
                >
                  {c.name}
                </button>
              </li>
            ))}
          </ul>

          <h2>Best price (₹)</h2>
          <form
            className="price-range"
            onSubmit={(e) => {
              e.preventDefault();
              applyPrice();
            }}
          >
            <input inputMode="decimal" placeholder="Min" aria-label="Minimum price" value={minText} onChange={(e) => setMinText(e.target.value)} onBlur={applyPrice} />
            <span aria-hidden>to</span>
            <input inputMode="decimal" placeholder="Max" aria-label="Maximum price" value={maxText} onChange={(e) => setMaxText(e.target.value)} onBlur={applyPrice} />
          </form>

          <label className="check">
            <input type="checkbox" checked={inStock} onChange={(e) => update({ inStock: e.target.checked })} />
            Only products a seller can supply now
          </label>

          {hasFilters && (
            <button className="link-btn" onClick={() => setParams({}, { replace: true })}>
              Clear all filters
            </button>
          )}
        </aside>

        <section aria-live="polite" aria-busy={query.isFetching}>
          <div className="results-head">
            <p>{data ? plural(data.totalItems, 'product') : '\u00A0'}</p>
            <label>
              Sort by{' '}
              <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
                {SORTS.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {query.isPending ? (
            <Skeleton rows={6} />
          ) : query.isError ? (
            <ErrorState error={query.error} onRetry={query.refetch} />
          ) : data.items.length === 0 ? (
            <EmptyState
              title="No products match"
              action={hasFilters && <button className="btn" onClick={() => setParams({}, { replace: true })}>Clear filters</button>}
            >
              Try a shorter search or remove a filter. Products appear once at least one approved seller lists them.
            </EmptyState>
          ) : (
            <>
              <ul className={`rows${query.isPlaceholderData ? ' rows--stale' : ''}`}>
                {data.items.map((p) => (
                  <li key={p.id}>
                    <Link className="row" to={`/products/${p.id}`}>
                      <ProductTile name={p.name} brand={p.brand} categoryId={p.categoryId} />
                      <div className="row__main">
                        <h3>{p.name}</h3>
                        <p>
                          {p.brand} · {p.categoryName}
                        </p>
                      </div>
                      <div className="row__sellers">
                        <strong>{plural(p.offerCount, 'seller')}</strong>
                        <span>{p.availableOfferCount === 0 ? 'none can supply now' : `${p.availableOfferCount} can supply now`}</span>
                      </div>
                      <PriceBlock product={p} />
                    </Link>
                  </li>
                ))}
              </ul>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update({ page: p })} />
            </>
          )}
        </section>
      </div>
    </div>
  );
}
