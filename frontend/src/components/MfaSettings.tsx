import React, { useState, useEffect } from 'react';
import api from '../services/api';

/**
 * MFA enrollment widget. Drives the TOTP setup → confirm → enabled flow, and the disable flow,
 * reading current state from /api/me.
 */
const MfaSettings: React.FC = () => {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [stage, setStage] = useState<'view' | 'enrolling' | 'disabling'>('view');
  const [secret, setSecret] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get('/api/me')
      .then((res) => setEnabled(Boolean(res.data.mfaEnabled)))
      .catch(() => setEnabled(false));
  }, []);

  const reset = () => {
    setStage('view');
    setSecret('');
    setCode('');
    setError('');
  };

  const beginSetup = async () => {
    setError('');
    setMessage('');
    setBusy(true);
    try {
      const res = await api.post('/api/mfa/setup');
      setSecret(res.data.secret);
      setStage('enrolling');
    } catch (err: any) {
      setError(err.response?.data?.error || 'Could not start MFA setup.');
    } finally {
      setBusy(false);
    }
  };

  const submitCode = async (path: string, onOk: () => void) => {
    setError('');
    setMessage('');
    setBusy(true);
    try {
      await api.post(path, { code });
      onOk();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Invalid code. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  const confirmEnable = () =>
    submitCode('/api/mfa/enable', () => {
      setEnabled(true);
      setMessage('Two-factor authentication is now enabled.');
      reset();
    });

  const confirmDisable = () =>
    submitCode('/api/mfa/disable', () => {
      setEnabled(false);
      setMessage('Two-factor authentication has been disabled.');
      reset();
    });

  if (enabled === null) return null;

  return (
    <div className="card" style={{ marginTop: '48px' }}>
      <div className="card-title">Two-Factor Authentication (TOTP)</div>

      {error && <div className="alert alert-error" style={{ marginTop: '12px' }}>{error}</div>}
      {message && <div className="alert alert-success" style={{ marginTop: '12px' }}>{message}</div>}

      {stage === 'view' && (
        <>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', margin: '12px 0 20px' }}>
            Status:{' '}
            <span className={`badge ${enabled ? 'badge-primary' : ''}`}>
              {enabled ? 'Enabled' : 'Disabled'}
            </span>
          </p>
          {enabled ? (
            <button className="btn btn-secondary" disabled={busy} onClick={() => { setStage('disabling'); setCode(''); }}>
              Disable 2FA
            </button>
          ) : (
            <button className="btn btn-primary" disabled={busy} onClick={beginSetup}>
              {busy ? 'Please wait...' : 'Enable 2FA'}
            </button>
          )}
        </>
      )}

      {stage === 'enrolling' && (
        <div style={{ marginTop: '16px' }}>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>
            Add this secret to an authenticator app (Google Authenticator, Authy, 1Password), then
            enter the current 6-digit code to confirm.
          </p>
          <div style={{ margin: '16px 0' }}>
            <label className="form-label">Secret key</label>
            <code style={{ display: 'block', padding: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', letterSpacing: '2px', wordBreak: 'break-all' }}>
              {secret}
            </code>
          </div>
          <CodeField code={code} setCode={setCode} />
          <div style={{ display: 'flex', gap: '12px', marginTop: '16px' }}>
            <button className="btn btn-primary" disabled={busy || code.length !== 6} onClick={confirmEnable}>
              {busy ? 'Verifying...' : 'Confirm & enable'}
            </button>
            <button className="btn btn-secondary" disabled={busy} onClick={reset}>Cancel</button>
          </div>
        </div>
      )}

      {stage === 'disabling' && (
        <div style={{ marginTop: '16px' }}>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>
            Enter a current code to turn off two-factor authentication.
          </p>
          <CodeField code={code} setCode={setCode} />
          <div style={{ display: 'flex', gap: '12px', marginTop: '16px' }}>
            <button className="btn btn-primary" disabled={busy || code.length !== 6} onClick={confirmDisable}>
              {busy ? 'Verifying...' : 'Confirm disable'}
            </button>
            <button className="btn btn-secondary" disabled={busy} onClick={reset}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  );
};

const CodeField: React.FC<{ code: string; setCode: (v: string) => void }> = ({ code, setCode }) => (
  <div className="form-group" style={{ marginBottom: 0 }}>
    <label className="form-label">Authentication code</label>
    <input
      type="text"
      inputMode="numeric"
      autoComplete="one-time-code"
      className="form-input"
      placeholder="123456"
      maxLength={6}
      value={code}
      onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
    />
  </div>
);

export default MfaSettings;
