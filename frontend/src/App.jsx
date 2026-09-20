import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SellerProvider } from './lib/seller.jsx';
import { ToastProvider } from './components/Toast.jsx';
import Layout from './components/Layout.jsx';
import CatalogPage from './pages/CatalogPage.jsx';
import ProductPage from './pages/ProductPage.jsx';
import SellerLayout from './pages/SellerLayout.jsx';
import SellerListingsPage from './pages/SellerListingsPage.jsx';
import AddProductsPage from './pages/AddProductsPage.jsx';
import NotFound from './pages/NotFound.jsx';

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 15_000, refetchOnWindowFocus: true, retry: 1 } },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SellerProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<Layout />}>
                <Route index element={<CatalogPage />} />
                <Route path="products/:id" element={<ProductPage />} />
                <Route path="sell" element={<SellerLayout />}>
                  <Route index element={<SellerListingsPage />} />
                  <Route path="add" element={<AddProductsPage />} />
                </Route>
                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </SellerProvider>
    </QueryClientProvider>
  );
}
