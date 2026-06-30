import React from 'react';

interface LandingProps {
  onNavigate: (page: string) => void;
}

const pillars = [
  {
    glyph: '⇄',
    title: 'Crypto-Agility',
    body: 'Swap the active cipher between AES-256-GCM and ChaCha20-Poly1305 from one config switch — no code changes, and old records keep decrypting under whatever algorithm wrote them.',
  },
  {
    glyph: '🔑',
    title: 'Envelope Encryption',
    body: 'Data keys are wrapped under a master KEK derived with HKDF-SHA256. Only wrapped key material ever touches the database — raw keys are never stored in plaintext.',
  },
  {
    glyph: '↻',
    title: 'Versioned Key Rotation',
    body: 'Rotate the active data key on demand and migrate legacy records in the background. Retired keys are kept — never deleted — so historical data still decrypts.',
  },
  {
    glyph: '🔒',
    title: 'TOTP Multi-Factor',
    body: 'A second factor built from scratch to RFC 6238, with one-time recovery codes. A correct password alone never yields a session once MFA is on.',
  },
  {
    glyph: '◉',
    title: 'Tamper-Evident Audit',
    body: 'Every login, read, write, and key rotation is recorded with a proxy-aware IP — a queryable trail, with brute-force lockout backed by Redis.',
  },
  {
    glyph: '⊘',
    title: 'Hard Logout',
    body: 'Stateless JWTs with real revocation: logout blacklists the token in Redis, so "logged out" means rejected on the very next request — not merely forgotten.',
  },
];

const Landing: React.FC<LandingProps> = ({ onNavigate }) => {
  return (
    <div style={{ flex: 1 }}>
      {/* Hero */}
      <section className="app-container" style={{ padding: '96px 24px 72px', maxWidth: '960px' }}>
        <div
          className="reveal"
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '10px', marginBottom: '28px',
            padding: '6px 14px', borderRadius: '999px',
            border: '1px solid rgba(205,255,79,0.25)', background: 'rgba(205,255,79,0.06)',
            fontFamily: 'var(--font-mono)', fontSize: '11.5px', letterSpacing: '0.16em',
            textTransform: 'uppercase', color: 'var(--accent)',
          }}
        >
          <span style={{ width: '7px', height: '7px', borderRadius: '50%', background: 'var(--accent)', boxShadow: '0 0 10px var(--accent)' }} />
          Encrypted · Secrets · At Rest
        </div>

        <h1 className="reveal" style={{ animationDelay: '0.06s', marginBottom: '26px' }}>
          Secrets, sealed under{' '}
          <span style={{ background: 'var(--color-gradient-primary)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            real cryptography
          </span>
          .
        </h1>

        <p className="reveal" style={{ animationDelay: '0.12s', fontSize: '19px', lineHeight: 1.6, color: 'var(--color-text-secondary)', maxWidth: '660px', marginBottom: '36px' }}>
          CryptoVault is a <strong style={{ color: 'var(--color-text-primary)' }}>crypto-agile secrets engine</strong> that
          protects sensitive data at rest — envelope encryption, versioned key rotation, multi-factor
          authentication, and tamper-evident auditing, all behind a hardened security gateway.
        </p>

        <div className="reveal" style={{ animationDelay: '0.18s', display: 'flex', gap: '14px', flexWrap: 'wrap' }}>
          <button className="btn btn-primary" style={{ padding: '13px 28px', fontSize: '15.5px' }} onClick={() => onNavigate('register')}>
            Create an account →
          </button>
          <button className="btn btn-secondary" style={{ padding: '13px 28px', fontSize: '15.5px' }} onClick={() => onNavigate('login')}>
            Access the vault
          </button>
        </div>
      </section>

      {/* What this signifies */}
      <section className="app-container" style={{ padding: '8px 24px 72px', maxWidth: '960px' }}>
        <div
          className="reveal"
          style={{
            animationDelay: '0.1s',
            border: '1px solid var(--border-color)', borderRadius: '20px', padding: '40px',
            background: 'linear-gradient(180deg, rgba(255,255,255,0.03), rgba(255,255,255,0.008))',
            position: 'relative', overflow: 'hidden',
          }}
        >
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: '11.5px', letterSpacing: '0.16em', textTransform: 'uppercase', color: 'var(--accent)', marginBottom: '16px' }}>
            What this project signifies
          </div>
          <p style={{ fontSize: '17.5px', lineHeight: 1.7, color: 'var(--color-text-primary)', marginBottom: '18px' }}>
            Most apps lean on a managed KMS/HSM and never see how secrets are actually protected.
            CryptoVault implements those patterns <strong>from scratch</strong> — the key hierarchy,
            the AEAD ciphers, the rotation flow, the second factor — so the mechanics are explicit
            instead of hidden behind a cloud API.
          </p>
          <p style={{ fontSize: '15.5px', lineHeight: 1.7, color: 'var(--color-text-secondary)', margin: 0 }}>
            It's a portfolio demonstration of <strong style={{ color: 'var(--color-text-primary)' }}>applied
            cryptography and security engineering</strong>: correct key derivation (HKDF, not a raw
            hash), authenticated encryption with per-call nonces, real token revocation, RBAC, rate
            limiting, and a full audit trail — built deliberately, tested end-to-end, and documented
            with its own threat model and design-decision records.
          </p>

          <div style={{ display: 'flex', gap: '32px', flexWrap: 'wrap', marginTop: '32px', paddingTop: '28px', borderTop: '1px solid var(--border-color)' }}>
            {[
              ['2', 'AEAD ciphers, hot-swappable'],
              ['HKDF', 'key derivation, done right'],
              ['RFC 6238', 'TOTP, hand-rolled'],
              ['60+', 'tests, gated by CI'],
            ].map(([stat, label]) => (
              <div key={label}>
                <div style={{ fontFamily: 'var(--font-display)', fontSize: '30px', fontWeight: 700, color: 'var(--accent)', letterSpacing: '-0.02em' }}>{stat}</div>
                <div style={{ fontSize: '13px', color: 'var(--color-text-muted)', marginTop: '2px' }}>{label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Pillars */}
      <section className="app-container" style={{ padding: '0 24px 96px' }}>
        <h2 style={{ fontSize: '13px', fontFamily: 'var(--font-mono)', fontWeight: 500, letterSpacing: '0.16em', textTransform: 'uppercase', color: 'var(--color-text-secondary)', marginBottom: '28px' }}>
          // The security model
        </h2>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
          {pillars.map((p, i) => (
            <div className="card reveal" key={p.title} style={{ animationDelay: `${0.05 * i}s` }}>
              <div style={{ fontSize: '22px', marginBottom: '16px', color: 'var(--accent)', lineHeight: 1 }}>{p.glyph}</div>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: '18px', fontWeight: 700, color: 'var(--color-text-primary)', marginBottom: '10px' }}>
                {p.title}
              </div>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', lineHeight: 1.6, margin: 0 }}>{p.body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Closing CTA */}
      <section className="app-container" style={{ padding: '0 24px 110px', textAlign: 'center', maxWidth: '720px' }}>
        <h2 style={{ fontSize: 'clamp(1.8rem, 4vw, 2.6rem)', marginBottom: '16px' }}>See it work, end to end.</h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '16px', lineHeight: 1.6, marginBottom: '28px' }}>
          Register, store an encrypted secret, decrypt it back, then turn on two-factor auth with
          recovery codes — all running live.
        </p>
        <button className="btn btn-primary" style={{ padding: '14px 32px', fontSize: '16px' }} onClick={() => onNavigate('register')}>
          Start now →
        </button>
      </section>
    </div>
  );
};

export default Landing;
