import React, { useState } from 'react';
import api from '../services/api';

interface LoginProps {
  onLoginSuccess: (token: string, role: string) => void;
  onNavigate: (page: string) => void;
  initialMode?: 'login' | 'register';
}

const Login: React.FC<LoginProps> = ({ onLoginSuccess, initialMode = 'login' }) => {
  const [mode, setMode] = useState<'login' | 'register'>(initialMode);
  const [step, setStep] = useState<'credentials' | 'mfa'>('credentials');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaToken, setMfaToken] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');

  // Parse the role out of the JWT and hand the session up to the app.
  const completeLogin = (token: string) => {
    const payload = JSON.parse(atob(token.split('.')[1]));
    onLoginSuccess(token, payload.role || 'USER');
  };

  const resetToCredentials = () => {
    setStep('credentials');
    setMfaToken('');
    setMfaCode('');
    setError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);

    try {
      if (mode === 'register') {
        await api.post('/api/auth/register', { email, password });
        setSuccessMsg('Registration successful! Please log in.');
        setMode('login');
        setPassword('');
      } else if (step === 'mfa') {
        const response = await api.post('/api/auth/mfa/verify', { mfaToken, code: mfaCode });
        completeLogin(response.data.token);
      } else {
        const response = await api.post('/api/auth/login', { email, password });
        if (response.data.mfaRequired) {
          setMfaToken(response.data.mfaToken);
          setStep('mfa');
          setMfaCode('');
        } else {
          completeLogin(response.data.token);
        }
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

  const heading = step === 'mfa'
    ? 'Two-Factor Authentication'
    : mode === 'login' ? 'Welcome Back' : 'Create Account';
  const subheading = step === 'mfa'
    ? 'Enter the 6-digit code from your authenticator app'
    : mode === 'login'
      ? 'Sign in to access your secure cryptographic vault'
      : 'Register to start storing enterprise secrets securely';

  return (
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
      <div className="form-card">
        <h2 style={{ color: 'white', marginBottom: '8px', textAlign: 'center' }}>{heading}</h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', marginBottom: '24px', textAlign: 'center' }}>
          {subheading}
        </p>

        {error && <div className="alert alert-error">{error}</div>}
        {successMsg && <div className="alert alert-success">{successMsg}</div>}

        <form onSubmit={handleSubmit}>
          {step === 'mfa' ? (
            <div className="form-group" style={{ marginBottom: '28px' }}>
              <label className="form-label">Authentication Code</label>
              <input
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                className="form-input"
                placeholder="123456"
                maxLength={6}
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, ''))}
                autoFocus
                required
              />
            </div>
          ) : (
            <>
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
            </>
          )}

          <button
            type="submit"
            className="btn btn-primary"
            style={{ width: '100%', padding: '12px', fontSize: '15px' }}
            disabled={loading}
          >
            {loading
              ? 'Please wait...'
              : step === 'mfa' ? 'Verify' : mode === 'login' ? 'Sign In' : 'Register'}
          </button>
        </form>

        <div style={{ marginTop: '24px', textAlign: 'center', fontSize: '14px', color: 'var(--color-text-secondary)' }}>
          {step === 'mfa' ? (
            <a href="#" onClick={(e) => { e.preventDefault(); resetToCredentials(); }}>
              Back to sign in
            </a>
          ) : mode === 'login' ? (
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
