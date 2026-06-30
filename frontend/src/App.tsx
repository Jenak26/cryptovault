import { useState, useEffect } from 'react';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Vault from './pages/Vault';
import Admin from './pages/Admin';
import api from './services/api';

function App() {
  const [page, setPage] = useState<string>('landing');
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'));
  const [role, setRole] = useState<string | null>(localStorage.getItem('userRole'));

  // Sync hash changes for routing
  useEffect(() => {
    const handleHashChange = () => {
      const hash = window.location.hash || '#/';
      if (hash === '#/') setPage('landing');
      else if (hash === '#/login') setPage('login');
      else if (hash === '#/register') setPage('register');
      else if (hash === '#/dashboard') setPage('dashboard');
      else if (hash === '#/vault') setPage('vault');
      else if (hash === '#/admin') setPage('admin');
    };

    window.addEventListener('hashchange', handleHashChange);
    handleHashChange(); // Run once on startup

    return () => {
      window.removeEventListener('hashchange', handleHashChange);
    };
  }, []);

  const navigateTo = (targetPage: string) => {
    if (targetPage === 'landing') window.location.hash = '#/';
    else window.location.hash = `#/${targetPage}`;
  };

  const handleLoginSuccess = (newToken: string, newRole: string) => {
    localStorage.setItem('token', newToken);
    localStorage.setItem('userRole', newRole);
    setToken(newToken);
    setRole(newRole);
    navigateTo('dashboard');
  };

  const handleLogout = async () => {
    try {
      if (token) {
        await api.post('/api/auth/logout', {});
      }
    } catch (err) {
      console.error('Failed to blacklist JWT in server blacklist', err);
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('userRole');
      setToken(null);
      setRole(null);
      navigateTo('landing');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      {/* Premium Navigation Header */}
      <header className="header">
        <div className="app-container header-inner">
          <div className="logo-container" style={{ cursor: 'pointer' }} onClick={() => navigateTo(token ? 'dashboard' : 'landing')}>
            <span className="logo-icon" />
            <span>CryptoVault</span>
          </div>

          <nav className="nav-links">
            {!token ? (
              <>
                <a href="#/" className={`nav-link ${page === 'landing' ? 'active' : ''}`}>Home</a>
                <a href="#/login" className={`nav-link ${page === 'login' ? 'active' : ''}`}>Login</a>
                <a href="#/register" className={`nav-link ${page === 'register' ? 'active' : ''}`}>Register</a>
              </>
            ) : (
              <>
                <a href="#/dashboard" className={`nav-link ${page === 'dashboard' ? 'active' : ''}`}>Dashboard</a>
                <a href="#/vault" className={`nav-link ${page === 'vault' ? 'active' : ''}`}>Vault</a>
                {role === 'ADMIN' && (
                  <a href="#/admin" className={`nav-link ${page === 'admin' ? 'active' : ''}`}>Admin Console</a>
                )}
                <button 
                  className="btn btn-secondary" 
                  style={{ padding: '6px 14px', fontSize: '13px', marginLeft: '12px' }}
                  onClick={handleLogout}
                >
                  Logout
                </button>
              </>
            )}
          </nav>
        </div>
      </header>

      {/* Main Pages Content */}
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {page === 'landing' && <Landing onNavigate={navigateTo} />}
        {page === 'login' && <Login onLoginSuccess={handleLoginSuccess} onNavigate={navigateTo} initialMode="login" />}
        {page === 'register' && <Login onLoginSuccess={handleLoginSuccess} onNavigate={navigateTo} initialMode="register" />}
        {page === 'dashboard' && <Dashboard onNavigate={navigateTo} />}
        {page === 'vault' && <Vault />}
        {page === 'admin' && <Admin />}
      </main>

      {/* Footer */}
      <footer style={{ borderTop: '1px solid var(--border-color)', padding: '28px 0', fontSize: '13.5px', color: 'var(--color-text-muted)', background: 'rgba(255,255,255,0.012)' }}>
        <div className="app-container" style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>
            <span style={{ color: 'var(--color-text-secondary)', fontWeight: 600 }}>CryptoVault</span>
            {' '}— a portfolio project on applied cryptography &amp; security engineering.
          </span>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', letterSpacing: '0.04em' }}>
            Java 21 · Spring Boot · MySQL · Redis · React
          </span>
        </div>
      </footer>
    </div>
  );
}

export default App;
