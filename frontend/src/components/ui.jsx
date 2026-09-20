import { useEffect, useRef } from 'react';
import { AlertTriangle, ChevronLeft, ChevronRight, PackageSearch, X } from 'lucide-react';

export function Skeleton({ rows = 5 }) {
  return (
    <div className="skeleton-list" aria-hidden>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton" />
      ))}
    </div>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="state">
      <PackageSearch size={32} aria-hidden />
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ error, onRetry }) {
  return (
    <div className="state state--error" role="alert">
      <AlertTriangle size={32} aria-hidden />
      <h3>Could not load this page</h3>
      <p>{error?.message || 'Something went wrong.'}</p>
      {onRetry && (
        <button className="btn" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button className="btn btn--ghost" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        <ChevronLeft size={16} aria-hidden /> Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button className="btn btn--ghost" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Next <ChevronRight size={16} aria-hidden />
      </button>
    </nav>
  );
}

const STATUS_TEXT = { APPROVED: 'Approved', PENDING: 'Pending approval', REJECTED: 'Rejected' };

export function SellerStatusBadge({ status }) {
  return <span className={`badge badge--${status.toLowerCase()}`}>{STATUS_TEXT[status] ?? status}</span>;
}

/** Native <dialog>: focus trapping, Esc and inert background come from the browser. */
export function Modal({ title, onClose, children }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => {
      if (dialog?.open) dialog.close();
    };
  }, []);

  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby="modal-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onMouseDown={(e) => {
        if (e.target === ref.current) onClose();
      }}
    >
      <div className="modal__body">
        <header className="modal__head">
          <h2 id="modal-title">{title}</h2>
          <button className="icon-btn" aria-label="Close" onClick={onClose}>
            <X size={18} aria-hidden />
          </button>
        </header>
        {children}
      </div>
    </dialog>
  );
}

export function Field({ label, hint, error, children, htmlFor }) {
  return (
    <div className={`field${error ? ' field--error' : ''}`}>
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {error ? <p className="field__error">{error}</p> : hint ? <p className="field__hint">{hint}</p> : null}
    </div>
  );
}
