import React, { useState } from 'react';
import api from '../services/api';

interface LoginProps {
  onLoginSuccess: (token: string, role: string) => void;
  onNavigate: (page: string) => void;
  initialMode?: 'login' | 'register';
}

const Login: React.FC<LoginProps> = ({ onLoginSuccess, initialMode = 'login' }) => {
  const [mode, setMode] = useState<'login' | 'register'>(initialMode);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);

    try {
      if (mode === 'login') {
        const response = await api.post('/api/auth/login', { email, password });
        const token = response.data.token;
        
        // Parse JWT to extract user role
        const payload = JSON.parse(atob(token.split('.')[1]));
        const role = payload.role || 'USER';
        
        onLoginSuccess(token, role);
      } else {
        await api.post('/api/auth/register', { email, password });
        setSuccessMsg('Registration successful! Please log in.');
        setMode('login');
        setPassword('');
      }
    } catch (err: any) {
      if (err.response && err.response.data && err.response.data.error) {
        setError(err.response.data.error);
      } else {
        setError('An unexpected error occurred. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
      <div className="form-card">
        <h2 style={{ color: 'white', marginBottom: '8px', textAlign: 'center' }}>
          {mode === 'login' ? 'Welcome Back' : 'Create Account'}
        </h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', marginBottom: '24px', textAlign: 'center' }}>
          {mode === 'login' 
            ? 'Sign in to access your secure cryptographic vault' 
            : 'Register to start storing enterprise secrets securely'}
        </p>

        {error && <div className="alert alert-error">{error}</div>}
        {successMsg && <div className="alert alert-success">{successMsg}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input 
              type="email" 
              className="form-input" 
              placeholder="name@bank.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-group" style={{ marginBottom: '28px' }}>
            <label className="form-label">Password</label>
            <input 
              type="password" 
              className="form-input" 
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button 
            type="submit" 
            className="btn btn-primary" 
            style={{ width: '100%', padding: '12px', fontSize: '15px' }}
            disabled={loading}
          >
            {loading ? 'Please wait...' : mode === 'login' ? 'Sign In' : 'Register'}
          </button>
        </form>

        <div style={{ marginTop: '24px', textAlign: 'center', fontSize: '14px', color: 'var(--color-text-secondary)' }}>
          {mode === 'login' ? (
            <>
              Don't have an account?{' '}
              <a href="#" onClick={(e) => { e.preventDefault(); setMode('register'); setError(''); }}>
                Register here
              </a>
            </>
          ) : (
            <>
              Already have an account?{' '}
              <a href="#" onClick={(e) => { e.preventDefault(); setMode('login'); setError(''); }}>
                Sign in here
              </a>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default Login;
