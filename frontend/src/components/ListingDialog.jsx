import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api.js';
import { formatPrice } from '../lib/format.js';
import { validateListing } from '../lib/validation.js';
import { useSeller } from '../lib/seller.jsx';
import { Field, Modal } from './ui.jsx';
import { useToast } from './Toast.jsx';

/**
 * Create a listing (mode "create", `product` = catalogue row) or edit one (mode "edit", `listing`).
 * Edits send only changed fields plus the version the seller was looking at; if someone else saved first
 * the server answers 409 STALE_LISTING and we offer to load the latest values instead of overwriting them.
 */
export default function ListingDialog({ mode, product, listing, onClose, onReload }) {
  const { sellerId } = useSeller();
  const queryClient = useQueryClient();
  const toast = useToast();

  const isEdit = mode === 'edit';
  const name = isEdit ? listing.productName : product.name;
  const unit = isEdit ? listing.unit : product.unit;
  const marketMin = isEdit ? listing.marketMinPrice : product.marketMinPrice;
  const resuming = !isEdit && product.listingStatus === 'INACTIVE';

  const [form, setForm] = useState(() =>
    isEdit
      ? { price: String(listing.price), stock: String(listing.stock), minOrderQty: String(listing.minOrderQty) }
      : { price: '', stock: '', minOrderQty: '1' },
  );
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState(null);

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const mutation = useMutation({
    mutationFn: (payload) =>
      isEdit ? api.updateListing(sellerId, listing.id, payload) : api.createListing(sellerId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['listings'] });
      queryClient.invalidateQueries({ queryKey: ['sellerCatalog'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      toast.push(isEdit ? `Saved changes to ${name}.` : resuming ? `You are selling ${name} again.` : `You now sell ${name}.`);
      onClose();
    },
    onError: (error) => {
      if (error.status === 400 && Object.keys(error.fieldErrors).length) setErrors(error.fieldErrors);
      else setServerError(error);
    },
  });

  const submit = (e) => {
    e.preventDefault();
    setServerError(null);
    const found = validateListing(form);
    setErrors(found);
    if (Object.keys(found).length) return;

    const values = { price: Number(form.price), stock: Number(form.stock), minOrderQty: Number(form.minOrderQty) };
    if (!isEdit) {
      mutation.mutate({ productId: product.id, ...values });
      return;
    }
    const changes = {};
    if (values.price !== Number(listing.price)) changes.price = values.price;
    if (values.stock !== listing.stock) changes.stock = values.stock;
    if (values.minOrderQty !== listing.minOrderQty) changes.minOrderQty = values.minOrderQty;
    if (Object.keys(changes).length === 0) {
      setServerError({ code: 'NO_CHANGES', message: 'Nothing has changed yet.' });
      return;
    }
    mutation.mutate({ version: listing.version, ...changes });
  };

  const stale = serverError?.code === 'STALE_LISTING';

  return (
    <Modal title={isEdit ? `Edit ${name}` : resuming ? `Resume selling ${name}` : `Sell ${name}`} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        {resuming && (
          <p className="notice">You stopped selling this earlier. Saving restarts your listing with the values below.</p>
        )}
        {marketMin !== null && marketMin !== undefined && (
          <p className="muted">Lowest price buyers can order at today: {formatPrice(marketMin)} per {unit}</p>
        )}

        <Field label={`Your price per ${unit} (₹)`} htmlFor="f-price" error={errors.price} hint="Up to 2 decimals, e.g. 385 or 385.50">
          <input id="f-price" inputMode="decimal" value={form.price} onChange={set('price')} autoFocus />
        </Field>
        <Field label={`Stock (${unit}s you have)`} htmlFor="f-stock" error={errors.stock} hint="Use 0 if you are out of stock. The listing stays visible as unavailable.">
          <input id="f-stock" inputMode="numeric" value={form.stock} onChange={set('stock')} />
        </Field>
        <Field label="Minimum order quantity" htmlFor="f-moq" error={errors.minOrderQty} hint={`Smallest number of ${unit}s you sell in one order.`}>
          <input id="f-moq" inputMode="numeric" value={form.minOrderQty} onChange={set('minOrderQty')} />
        </Field>

        {serverError && (
          <div className="alert" role="alert">
            <p>{serverError.message}</p>
            {stale && (
              <button type="button" className="btn btn--ghost" onClick={onReload}>
                Load latest values
              </button>
            )}
          </div>
        )}

        <footer className="modal__foot">
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn--primary" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : isEdit ? 'Save changes' : resuming ? 'Resume selling' : 'Start selling'}
          </button>
        </footer>
      </form>
    </Modal>
  );
}
