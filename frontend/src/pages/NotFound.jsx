import { Link } from 'react-router-dom';
import { EmptyState } from '../components/ui.jsx';

export default function NotFound() {
  return (
    <EmptyState title="Page not found" action={<Link className="btn" to="/">Go to products</Link>}>
      The page you are looking for does not exist.
    </EmptyState>
  );
}
