// Client-side mirror of the backend rules so people get instant feedback.
// The server remains the source of truth and its field errors are shown too.

export const LIMITS = { maxPrice: 10_000_000, maxStock: 10_000_000, maxMoq: 1_000_000 };

export function validateListing({ price, stock, minOrderQty }) {
  const errors = {};

  if (price === '' || price === null || price === undefined) errors.price = 'Enter a price.';
  else if (!/^\d+(\.\d{1,2})?$/.test(String(price).trim())) errors.price = 'Use a positive amount with up to 2 decimals.';
  else if (Number(price) < 0.01) errors.price = 'Price must be at least ₹0.01.';
  else if (Number(price) > LIMITS.maxPrice) errors.price = 'Price is too high.';

  if (stock === '' || stock === null || stock === undefined) errors.stock = 'Enter the stock you have (0 if none).';
  else if (!/^\d+$/.test(String(stock).trim())) errors.stock = 'Use a whole number, 0 or more.';
  else if (Number(stock) > LIMITS.maxStock) errors.stock = 'Stock is too large.';

  if (minOrderQty === '' || minOrderQty === null || minOrderQty === undefined) errors.minOrderQty = 'Enter a minimum order quantity.';
  else if (!/^\d+$/.test(String(minOrderQty).trim()) || Number(minOrderQty) < 1) errors.minOrderQty = 'Use a whole number, 1 or more.';
  else if (Number(minOrderQty) > LIMITS.maxMoq) errors.minOrderQty = 'Minimum order is too large.';

  return errors;
}
