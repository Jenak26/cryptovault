import React, { useState, useEffect } from 'react';
import api from '../services/api';

type Stage = 'view' | 'enrolling' | 'disabling' | 'regenerating' | 'codes';

/**
 * MFA enrollment widget: TOTP setup → confirm → recovery codes, plus regenerate and disable.
 * Current state is read from /api/me.
 */
const MfaSettings: React.FC = () => {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [stage, setStage] = useState<Stage>('view');
  const [secret, setSecret] = useState('');
  const [code, setCode] = useState('');
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
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
    setError(''); setMessage(''); setBusy(true);
    try {
      const res = await api.post('/api/mfa/setup');
      setSecret(res.data.secret);
      setCode('');
      setStage('enrolling');
    } catch (err: any) {
      setError(err.response?.data?.error || 'Could not start MFA setup.');
    } finally {
      setBusy(false);
    }
  };

  // POST a code-bearing action; on success either show returned codes or run onOk.
  const submit = async (path: string, opts: { showCodes?: boolean; onOk?: () => void; okMsg?: string }) => {
    setError(''); setMessage(''); setBusy(true);
    try {
      const res = await api.post(path, { code });
      if (opts.showCodes) {
        setBackupCodes(res.data.backupCodes || []);
        setStage('codes');
      } else {
        opts.onOk?.();
      }
      if (opts.okMsg) setMessage(opts.okMsg);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Invalid code. Please try again.');
    } finally {
      setBusy(false);
    }
  };

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
            <span className={`badge ${enabled ? 'badge-primary' : ''}`}>{enabled ? 'Enabled' : 'Disabled'}</span>
          </p>
          {enabled ? (
            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="btn btn-secondary" disabled={busy} onClick={() => { setStage('regenerating'); setCode(''); }}>
                Regenerate backup codes
              </button>
              <button className="btn btn-secondary" disabled={busy} onClick={() => { setStage('disabling'); setCode(''); }}>
                Disable 2FA
              </button>
            </div>
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
            <button className="btn btn-primary" disabled={busy || code.length !== 6}
              onClick={() => { setEnabled(true); submit('/api/mfa/enable', { showCodes: true }); }}>
              {busy ? 'Verifying...' : 'Confirm & enable'}
            </button>
            <button className="btn btn-secondary" disabled={busy} onClick={reset}>Cancel</button>
          </div>
        </div>
      )}

      {stage === 'regenerating' && (
        <div style={{ marginTop: '16px' }}>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>
            Enter a current code to generate a new set of recovery codes (this invalidates the old set).
          </p>
          <CodeField code={code} setCode={setCode} />
          <div style={{ display: 'flex', gap: '12px', marginTop: '16px' }}>
            <button className="btn btn-primary" disabled={busy || code.length !== 6}
              onClick={() => submit('/api/mfa/backup-codes/regenerate', { showCodes: true })}>
              {busy ? 'Verifying...' : 'Regenerate'}
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
            <button className="btn btn-primary" disabled={busy || code.length !== 6}
              onClick={() => submit('/api/mfa/disable', { onOk: () => { setEnabled(false); reset(); }, okMsg: 'Two-factor authentication disabled.' })}>
              {busy ? 'Verifying...' : 'Confirm disable'}
            </button>
            <button className="btn btn-secondary" disabled={busy} onClick={reset}>Cancel</button>
          </div>
        </div>
      )}

      {stage === 'codes' && (
        <div style={{ marginTop: '16px' }}>
          <div className="alert alert-error" style={{ marginBottom: '16px' }}>
            Save these recovery codes now — each works once, and they won't be shown again. Use one
            in place of your authenticator code if you lose access to your device.
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontFamily: 'monospace', padding: '16px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px' }}>
            {backupCodes.map((c) => (<span key={c} style={{ letterSpacing: '2px' }}>{c}</span>))}
          </div>
          <button className="btn btn-primary" style={{ marginTop: '16px' }}
            onClick={() => { setMessage('Two-factor authentication is active.'); reset(); }}>
            I've saved my codes
          </button>
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
