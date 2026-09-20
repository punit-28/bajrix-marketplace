/**
 * Compare seller offers for a quantity the buyer needs.
 *
 * fit values:
 *   ok         – seller can supply the quantity
 *   out        – no stock
 *   below-moq  – requested quantity is under the seller's minimum order
 *   low-stock  – seller has stock but not enough for the requested quantity
 *                (or, with no quantity entered, stock is below their own minimum order)
 */
export function evaluateOffer(offer, quantity) {
  const qty = Number.isFinite(quantity) && quantity > 0 ? quantity : null;
  let fit;
  if (offer.stock === 0) fit = 'out';
  else if (qty === null) fit = offer.orderable ? 'ok' : 'low-stock';
  else if (qty < offer.minOrderQty) fit = 'below-moq';
  else if (qty > offer.stock) fit = 'low-stock';
  else fit = 'ok';

  const total = qty === null ? null : Number(offer.price) * qty;
  return { ...offer, fit, total };
}

/** Ranks offers that can supply first (cheapest first), then the rest by price. */
export function rankOffers(offers, quantity) {
  const rows = offers.map((o) => evaluateOffer(o, quantity));
  rows.sort((a, b) => {
    if ((a.fit === 'ok') !== (b.fit === 'ok')) return a.fit === 'ok' ? -1 : 1;
    return Number(a.price) - Number(b.price) || a.listingId - b.listingId;
  });

  const suppliers = rows.filter((r) => r.fit === 'ok');
  const best = suppliers[0] ?? null;
  const prices = suppliers.map((r) => Number(r.price));
  const min = prices.length ? Math.min(...prices) : null;
  const max = prices.length ? Math.max(...prices) : null;

  const ranked = rows.map((r) => {
    if (r.fit !== 'ok' || min === null) return { ...r, isBest: false, deltaVsBest: null, spread: null };
    const price = Number(r.price);
    return {
      ...r,
      isBest: r === best,
      deltaVsBest: price - min,
      spread: max === min ? 0 : (price - min) / (max - min),
    };
  });
  return { rows: ranked, best: ranked.find((r) => r.isBest) ?? null, supplierCount: suppliers.length };
}
