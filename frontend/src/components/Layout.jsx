import { NavLink, Outlet } from 'react-router-dom';

function Mark() {
  return (
    <svg width="26" height="26" viewBox="0 0 32 32" aria-hidden>
      <rect width="32" height="32" rx="7" fill="var(--steel)" />
      <circle cx="11" cy="20" r="5" fill="var(--marker)" />
      <circle cx="21" cy="20" r="5" fill="#fff" />
      <circle cx="16" cy="11" r="5" fill="#fff" fillOpacity=".7" />
    </svg>
  );
}

export default function Layout() {
  return (
    <>
      <a className="skip" href="#main">
        Skip to content
      </a>
      <header className="topbar">
        <div className="topbar__inner">
          <NavLink to="/" className="brand" aria-label="BajriX home">
            <Mark />
            <span>BajriX</span>
          </NavLink>
          <nav className="topnav" aria-label="Main">
            <NavLink to="/" end>
              Browse materials
            </NavLink>
            <NavLink to="/sell">Sell on BajriX</NavLink>
          </nav>
        </div>
      </header>
      <main id="main" className="page">
        <Outlet />
      </main>
    </>
  );
}
