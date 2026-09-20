import { describe, expect, it } from 'vitest';
import { formatPrice, plural, timeAgo } from './format.js';
import { evaluateOffer, rankOffers } from './offers.js';
import { validateListing } from './validation.js';

const offer = (over) => ({ listingId: 1, sellerName: 'S', price: '100.00', stock: 100, minOrderQty: 10, orderable: true, ...over });

describe('offers', () => {
  it('marks stock below the requested quantity', () => {
    expect(evaluateOffer(offer({ stock: 50 }), 80).fit).toBe('low-stock');
  });
  it('marks quantity below the minimum order', () => {
    expect(evaluateOffer(offer({ minOrderQty: 20 }), 5).fit).toBe('below-moq');
  });
  it('computes the total for the requested quantity', () => {
    expect(evaluateOffer(offer({ price: '385.00' }), 100).total).toBe(38500);
  });
  it('without a quantity, stock below MOQ counts as unavailable', () => {
    expect(evaluateOffer(offer({ stock: 3, minOrderQty: 5, orderable: false }), null).fit).toBe('low-stock');
    expect(evaluateOffer(offer({ stock: 0, orderable: false }), null).fit).toBe('out');
  });
  it('ranks suppliers first by price and picks the cheapest as best', () => {
    const { rows, best } = rankOffers(
      [
        offer({ listingId: 1, price: '400.00' }),
        offer({ listingId: 2, price: '380.00', stock: 0, orderable: false }),
        offer({ listingId: 3, price: '390.00' }),
      ],
      null,
    );
    expect(rows.map((r) => r.listingId)).toEqual([3, 1, 2]);
    expect(best.listingId).toBe(3);
    expect(rows[1].deltaVsBest).toBe(10);
    expect(rows[2].isBest).toBe(false);
  });
  it('re-ranks when a large quantity rules the cheapest seller out', () => {
    const { best } = rankOffers([offer({ listingId: 1, price: '385.00', stock: 120 }), offer({ listingId: 2, price: '405.00', stock: 2000 })], 500);
    expect(best.listingId).toBe(2);
  });
  it('has no best offer when nobody can supply', () => {
    expect(rankOffers([offer({ stock: 0, orderable: false })], null).best).toBeNull();
  });
});

describe('validation', () => {
  it('accepts a normal listing', () => {
    expect(validateListing({ price: '385.50', stock: '0', minOrderQty: '1' })).toEqual({});
  });
  it('rejects bad values', () => {
    const errors = validateListing({ price: '-5', stock: '1.5', minOrderQty: '0' });
    expect(Object.keys(errors).sort()).toEqual(['minOrderQty', 'price', 'stock']);
  });
  it('rejects three decimals and zero price', () => {
    expect(validateListing({ price: '10.123', stock: '1', minOrderQty: '1' }).price).toBeTruthy();
    expect(validateListing({ price: '0', stock: '1', minOrderQty: '1' }).price).toBeTruthy();
  });
});

describe('format', () => {
  it('formats rupees with Indian grouping and optional paise', () => {
    expect(formatPrice(123456)).toBe('₹1,23,456');
    expect(formatPrice('9.5')).toBe('₹9.5');
    expect(formatPrice(null)).toBe('–');
  });
  it('pluralises', () => {
    expect(plural(1, 'seller')).toBe('1 seller');
    expect(plural(3, 'seller')).toBe('3 sellers');
  });
  it('describes elapsed time', () => {
    const now = Date.parse('2026-01-01T12:00:00Z');
    expect(timeAgo('2026-01-01T11:59:40Z', now)).toBe('just now');
    expect(timeAgo('2026-01-01T09:00:00Z', now)).toBe('3 h ago');
  });
});
