import React, { useState, useEffect } from 'react';
import api from '../services/api';

interface DashboardProps {
  onNavigate: (page: string) => void;
}

const Dashboard: React.FC<DashboardProps> = ({ onNavigate }) => {
  const [secretsCount, setSecretsCount] = useState<number | null>(null);
  const [systemStatus, setSystemStatus] = useState<string>('Checking...');
  const [activeAlgo, setActiveAlgo] = useState<string>('AES');
  const [activeKeyVersion, setActiveKeyVersion] = useState<number | string>('1');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        // Fetch health
        try {
          const healthRes = await api.get('/api/health');
          setSystemStatus(healthRes.data.status === 'UP' ? 'Operational' : 'Maintenance');
        } catch {
          setSystemStatus('Offline');
        }

        // Fetch secrets list to calculate count and determine algorithms
        const vaultRes = await api.get('/api/vault');
        const list = vaultRes.data || [];
        setSecretsCount(list.length);

        if (list.length > 0) {
          // Determine the algorithm and key version of the newest secret
          const latest = list[list.length - 1];
          setActiveAlgo(latest.algorithmUsed);
          setActiveKeyVersion(latest.keyVersion);
        }
      } catch (err) {
        console.error('Failed to load dashboard statistics', err);
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

  return (
    <div className="app-container" style={{ paddingTop: '40px', paddingBottom: '60px', flex: 1 }}>
      <div style={{ textAlign: 'left', marginBottom: '32px' }}>
        <h1 style={{ fontSize: '36px', fontWeight: 700, margin: '0 0 8px' }}>Security Dashboard</h1>
        <p style={{ color: 'var(--color-text-secondary)' }}>Real-time overview of your cryptographic vault and system health.</p>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--color-text-secondary)' }}>Loading telemetry data...</div>
      ) : (
        <>
          <div className="dashboard-grid">
            <div className="card">
              <div className="card-title">Vault Records</div>
              <div className="card-value">{secretsCount !== null ? secretsCount : '0'}</div>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px', marginTop: '12px' }}>
                Active encrypted files stored.
              </p>
            </div>

            <div className="card">
              <div className="card-title">Active Algorithm</div>
              <div className="card-value" style={{ fontSize: '24px', height: '48px', display: 'flex', alignItems: 'center' }}>
                <span className="badge badge-primary">{activeAlgo}</span>
              </div>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px', marginTop: '12px' }}>
                Crypto-agility selection in configuration.
              </p>
            </div>

            <div className="card">
              <div className="card-title">Key version</div>
              <div className="card-value">{activeKeyVersion}</div>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px', marginTop: '12px' }}>
                Version of wrapped data key in use.
              </p>
            </div>

            <div className="card">
              <div className="card-title">Infrastructure Health</div>
              <div className="card-value" style={{ fontSize: '24px', height: '48px', display: 'flex', alignItems: 'center', color: systemStatus === 'Operational' ? 'var(--color-success)' : 'var(--color-error)' }}>
                ● {systemStatus}
              </div>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px', marginTop: '12px' }}>
                Backend database & cache liveness.
              </p>
            </div>
          </div>

          <div style={{ marginTop: '48px', display: 'flex', gap: '16px', justifyContent: 'flex-start' }}>
            <button className="btn btn-primary" onClick={() => onNavigate('vault')}>
              Manage Secrets
            </button>
            {localStorage.getItem('userRole') === 'ADMIN' && (
              <button className="btn btn-secondary" onClick={() => onNavigate('admin')}>
                Admin Console
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default Dashboard;
