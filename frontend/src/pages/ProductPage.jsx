import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, MapPin } from 'lucide-react';
import { api } from '../lib/api.js';
import { formatMoney, formatNumber, formatPrice, plural, timeAgo } from '../lib/format.js';
import { rankOffers } from '../lib/offers.js';
import PriceSpread from '../components/PriceSpread.jsx';
import { EmptyState, ErrorState, Skeleton } from '../components/ui.jsx';

function Availability({ offer, unit }) {
  switch (offer.fit) {
    case 'out':
      return <span className="tone-bad">Out of stock</span>;
    case 'below-moq':
      return <span className="tone-warn">Minimum order is {formatNumber(offer.minOrderQty)}</span>;
    case 'low-stock':
      return (
        <span className="tone-warn">
          Only {formatNumber(offer.stock)} in stock
          {!offer.orderable && offer.minOrderQty > offer.stock ? `, minimum order ${formatNumber(offer.minOrderQty)}` : ''}
        </span>
      );
    default:
      return (
        <>
          <strong>{formatNumber(offer.stock)}</strong> {unit} in stock
        </>
      );
  }
}

export default function ProductPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [qtyText, setQtyText] = useState('');
  const query = useQuery({ queryKey: ['product', id], queryFn: () => api.product(id) });

  const back = () => (window.history.length > 1 ? navigate(-1) : navigate('/'));

  if (query.isPending) return <Skeleton rows={4} />;
  if (query.isError) {
    return query.error.status === 404 ? (
      <EmptyState title="Product not found" action={<Link className="btn" to="/">Browse all products</Link>}>
        It may have been removed from the catalogue.
      </EmptyState>
    ) : (
      <ErrorState error={query.error} onRetry={query.refetch} />
    );
  }

  const { product, offers } = query.data;
  const quantity = /^\d+$/.test(qtyText.trim()) ? Number(qtyText.trim()) : null;
  const { rows, best, supplierCount } = rankOffers(offers, quantity);

  return (
    <article className="product">
      <button className="link-btn back" onClick={back}>
        <ArrowLeft size={16} aria-hidden /> Back to results
      </button>

      <header className="product__head">
        <div>
          <p className="muted">
            {product.brand} · {product.categoryName}
          </p>
          <h1>{product.name}</h1>
          {product.description && <p className="product__desc">{product.description}</p>}
        </div>
        <div className="product__best" aria-live="polite">
          {best ? (
            <>
              <span className="muted">{quantity ? `Lowest total for ${formatNumber(quantity)} ${product.unit}` : 'Lowest available price'}</span>
              <strong>{quantity ? formatMoney(best.total) : formatPrice(best.price)}</strong>
              <span className="muted">
                {quantity ? `${formatPrice(best.price)} per ${product.unit}` : `per ${product.unit}`} from {best.sellerName}
              </span>
            </>
          ) : (
            <>
              <span className="muted">Availability</span>
              <strong>{offers.length ? 'No seller can supply this right now' : 'No sellers yet'}</strong>
            </>
          )}
        </div>
      </header>

      {offers.length === 0 ? (
        <EmptyState title="No sellers offer this right now">Check back later, or browse similar products.</EmptyState>
      ) : (
        <section aria-labelledby="offers-title">
          <div className="offers-head">
            <h2 id="offers-title">
              {plural(product.offerCount, 'seller')} <span className="muted">({supplierCount} can supply{quantity ? ' this quantity' : ' now'})</span>
            </h2>
            <label className="qty">
              How many {product.unit}s do you need?
              <input
                inputMode="numeric"
                value={qtyText}
                onChange={(e) => setQtyText(e.target.value)}
                placeholder="e.g. 100"
                aria-describedby="qty-help"
              />
            </label>
          </div>
          <p id="qty-help" className="muted qty-help">
            {quantity
              ? 'Sellers are re-ranked by what you would pay in total, and those who cannot supply this quantity move down.'
              : 'Enter a quantity to see the total per seller and which sellers can supply it.'}
          </p>

          <table className="offers">
            <thead>
              <tr>
                <th scope="col">Seller</th>
                <th scope="col">Price per {product.unit}</th>
                <th scope="col">Availability</th>
                <th scope="col">Min. order</th>
                {quantity && <th scope="col">Total</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((o) => (
                <tr key={o.listingId} className={`${o.fit !== 'ok' ? 'offer--muted' : ''}${o.isBest ? ' offer--best' : ''}`}>
                  <td data-label="Seller">
                    <strong>{o.sellerName}</strong>
                    <span className="muted loc">
                      <MapPin size={13} aria-hidden /> {o.sellerCity}
                    </span>
                  </td>
                  <td data-label={`Price per ${product.unit}`}>
                    <div className="price-cell">
                      <strong className="price">{formatPrice(o.price)}</strong>
                      {o.isBest ? (
                        <span className="tag tag--best">Lowest price</span>
                      ) : o.deltaVsBest !== null ? (
                        <span className="muted">
                          +{formatPrice(o.deltaVsBest)} ({((o.deltaVsBest / (Number(o.price) - o.deltaVsBest)) * 100).toFixed(1)}%)
                        </span>
                      ) : null}
                    </div>
                    <PriceSpread position={o.spread} best={o.isBest} />
                  </td>
                  <td data-label="Availability">
                    <Availability offer={o} unit={product.unit} />
                    <span className="muted updated">Updated {timeAgo(o.updatedAt)}</span>
                  </td>
                  <td data-label="Min. order">{formatNumber(o.minOrderQty)}</td>
                  {quantity && (
                    <td data-label="Total" className="total">
                      {o.fit === 'ok' ? formatMoney(o.total) : '–'}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {product.offerCount > offers.length && (
            <p className="muted">Showing the best {offers.length} of {formatNumber(product.offerCount)} sellers.</p>
          )}
        </section>
      )}
    </article>
  );
}
