import React from 'react';

interface LandingProps {
  onNavigate: (page: string) => void;
}

const Landing: React.FC<LandingProps> = ({ onNavigate }) => {
  return (
    <div style={{ padding: '80px 20px', textAlign: 'center', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
      <div className="app-container" style={{ maxWidth: '800px' }}>
        <h1 style={{ background: 'var(--color-gradient-primary)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', marginBottom: '24px' }}>
          CryptoVault
        </h1>
        <p style={{ fontSize: '20px', color: 'var(--color-text-secondary)', marginBottom: '40px', lineHeight: '1.6' }}>
          A secure, enterprise-grade cryptographic vault designed for financial institutions. Featuring pluggable crypto-agility and versioned key rotation.
        </p>

        <div style={{ display: 'flex', justifyContent: 'center', gap: '16px', marginBottom: '80px' }}>
          <button className="btn btn-primary" style={{ padding: '12px 28px', fontSize: '16px' }} onClick={() => onNavigate('login')}>
            Access Vault
          </button>
          <button className="btn btn-secondary" style={{ padding: '12px 28px', fontSize: '16px' }} onClick={() => onNavigate('register')}>
            Register Account
          </button>
        </div>

        <h2 style={{ marginBottom: '32px', color: 'white' }}>Key Security Pillars</h2>
        
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '24px' }}>
          <div className="card">
            <div style={{ fontSize: '32px', marginBottom: '16px' }}>🛡️</div>
            <div className="card-title">Crypto Agility</div>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', lineHeight: '1.5' }}>
              Pluggable strategies for AES-GCM and ChaCha20-Poly1305. Upgrade algorithms seamlessly without breaking legacy records.
            </p>
          </div>

          <div className="card">
            <div style={{ fontSize: '32px', marginBottom: '16px' }}>🔑</div>
            <div className="card-title">Envelope Key Seeding</div>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', lineHeight: '1.5' }}>
              Data keys are wrapped/encrypted under a Master Key (KEK) derived from environment variables, preventing plaintext database exposure.
            </p>
          </div>

          <div className="card">
            <div style={{ fontSize: '32px', marginBottom: '16px' }}>📊</div>
            <div className="card-title">Audit Integrity</div>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px', lineHeight: '1.5' }}>
              Complete, queryable audit trail of logins, reads, and rotations. Implements rate limiting to prevent login brute force.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Landing;
