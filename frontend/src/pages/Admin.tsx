import React, { useState, useEffect } from 'react';
import api from '../services/api';

const Admin: React.FC = () => {
  const [logs, setLogs] = useState<any[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Rotation State
  const [rotating, setRotating] = useState(false);

  // Filter State
  const [filterAction, setFilterAction] = useState('');
  const [filterUser, setFilterUser] = useState('');

  // Local state for statistics (CSS-only charts)
  const [stats, setStats] = useState<Record<string, number>>({});

  const fetchAuditLogs = async (page = 0) => {
    setLoading(true);
    setError('');
    try {
      let url = `/api/admin/audit?page=${page}&size=10`;
      if (filterAction) url += `&action=${filterAction}`;
      if (filterUser) {
        // Only append if it's a valid UUID format
        if (filterUser.trim().length === 36) {
          url += `&userId=${filterUser.trim()}`;
        }
      }
      const response = await api.get(url);
      setLogs(response.data.content || []);
      setTotalPages(response.data.totalPages || 0);
      setCurrentPage(page);

      // Compute statistics based on logs for visual telemetries
      const counts: Record<string, number> = {};
      (response.data.content || []).forEach((log: any) => {
        counts[log.action] = (counts[log.action] || 0) + 1;
      });
      setStats(counts);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to fetch audit logs. Access denied.');
    } finally {
      setLoading(false);
    }
  };

  // Re-fetch whenever the action filter changes. fetchAuditLogs is intentionally
  // left out of the dependency array so the effect doesn't re-run on every render.
  useEffect(() => {
    fetchAuditLogs(0);
  }, [filterAction]);

  const handleRotateKey = async () => {
    if (!window.confirm('WARNING: Rotating the data key generates a new version for all FUTURE encryptions. Old records will still decrypt using retired keys. Proceed?')) {
      return;
    }
    setRotating(true);
    setError('');
    setSuccess('');

    try {
      const response = await api.post('/api/admin/rotate-key');
      setSuccess(`Key successfully rotated! Active Version is now: v${response.data.version}`);
      fetchAuditLogs(currentPage);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to rotate key.');
    } finally {
      setRotating(false);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    fetchAuditLogs(0);
  };

  return (
    <div className="app-container" style={{ paddingTop: '40px', paddingBottom: '60px', flex: 1, textAlign: 'left' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h1 style={{ fontSize: '36px', fontWeight: 700, margin: '0 0 8px' }}>Security & Audit Console</h1>
          <p style={{ color: 'var(--color-text-secondary)' }}>Trigger cryptographic key rotations and monitor compliance activity logs.</p>
        </div>
        <button 
          className="btn btn-primary" 
          onClick={handleRotateKey} 
          disabled={rotating}
          style={{ background: 'var(--color-gradient-accent)' }}
        >
          {rotating ? 'Generating Key...' : '🔄 Rotate Data Key'}
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      {/* Visual Telemetry Chart (CSS-only) */}
      <h2 style={{ color: 'white', marginBottom: '16px' }}>Event Frequency (Telemetry)</h2>
      <div className="card" style={{ marginBottom: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {Object.keys(stats).length === 0 ? (
          <div style={{ color: 'var(--color-text-secondary)', textAlign: 'center', padding: '10px 0' }}>No event data captured in current search view.</div>
        ) : (
          Object.entries(stats).map(([action, count]) => {
            const percentage = Math.min(100, Math.max(10, (count / logs.length) * 100));
            const isAlarm = action === 'LOGIN_FAIL';
            return (
              <div key={action} style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <div style={{ width: '130px', fontWeight: 600, fontSize: '13px' }}>{action}</div>
                <div style={{ flex: 1, background: 'rgba(255,255,255,0.03)', height: '12px', borderRadius: '6px', overflow: 'hidden', border: '1px solid var(--border-color)' }}>
                  <div style={{ 
                    width: `${percentage}%`, 
                    background: isAlarm ? 'var(--color-error)' : 'var(--color-gradient-primary)', 
                    height: '100%', 
                    borderRadius: '6px',
                    transition: 'width 0.5s ease-out'
                  }} />
                </div>
                <div style={{ width: '40px', textAlign: 'right', fontSize: '13px', fontWeight: 600 }}>{count}</div>
              </div>
            );
          })
        )}
      </div>

      {/* Filters Form */}
      <form onSubmit={handleSearch} style={{ display: 'flex', gap: '16px', alignItems: 'flex-end', marginBottom: '24px' }}>
        <div className="form-group" style={{ margin: 0, flex: 1 }}>
          <label className="form-label">Filter by Action</label>
          <select 
            className="form-input" 
            style={{ width: '100%', height: '45px' }}
            value={filterAction}
            onChange={(e) => setFilterAction(e.target.value)}
          >
            <option value="">All Actions</option>
            <option value="REGISTER">REGISTER</option>
            <option value="LOGIN_SUCCESS">LOGIN_SUCCESS</option>
            <option value="LOGIN_FAIL">LOGIN_FAIL</option>
            <option value="LOGOUT">LOGOUT</option>
            <option value="VAULT_STORE">VAULT_STORE</option>
            <option value="VAULT_READ">VAULT_READ</option>
            <option value="VAULT_DELETE">VAULT_DELETE</option>
            <option value="KEY_ROTATE">KEY_ROTATE</option>
          </select>
        </div>

        <div className="form-group" style={{ margin: 0, flex: 2 }}>
          <label className="form-label">Filter by User ID (exact UUID)</label>
          <input 
            type="text" 
            className="form-input" 
            placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
            value={filterUser}
            onChange={(e) => setFilterUser(e.target.value)}
          />
        </div>

        <button type="submit" className="btn btn-secondary" style={{ height: '45px', padding: '0 24px' }}>
          Apply Filters
        </button>
      </form>

      {/* Logs Table */}
      <h2 style={{ color: 'white', marginBottom: '16px' }}>Compliance logs</h2>
      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--color-text-secondary)' }}>Querying audit logs...</div>
      ) : logs.length === 0 ? (
        <div className="card" style={{ padding: '40px', textAlign: 'center', color: 'var(--color-text-secondary)' }}>No audit logs matching search filters.</div>
      ) : (
        <>
          <div className="table-container">
            <table className="vault-table">
              <thead>
                <tr>
                  <th>Audit ID</th>
                  <th>User ID</th>
                  <th>Action performed</th>
                  <th>IP Address</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '13px' }}>#{log.id}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '13px', color: log.user ? 'var(--color-primary)' : 'var(--color-text-muted)' }}>
                      {log.user ? log.user.id : 'anonymous / failed login'}
                    </td>
                    <td>
                      <span className={`badge ${
                        log.action === 'LOGIN_SUCCESS' || log.action === 'REGISTER' ? 'badge-primary' : 
                        log.action === 'LOGIN_FAIL' ? 'badge-secondary' : 'badge-primary'
                      }`} style={{ 
                        background: log.action === 'LOGIN_FAIL' ? 'rgba(239, 68, 68, 0.1)' : undefined,
                        color: log.action === 'LOGIN_FAIL' ? 'var(--color-error)' : undefined,
                        borderColor: log.action === 'LOGIN_FAIL' ? 'rgba(239, 68, 68, 0.2)' : undefined
                      }}>
                        {log.action}
                      </span>
                    </td>
                    <td style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>{log.ipAddress}</td>
                    <td style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>
                      {new Date(log.timestamp).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '16px', marginTop: '24px' }}>
              <button 
                className="btn btn-secondary" 
                onClick={() => fetchAuditLogs(currentPage - 1)}
                disabled={currentPage === 0}
              >
                Previous
              </button>
              <span style={{ fontSize: '14px', color: 'var(--color-text-secondary)' }}>
                Page {currentPage + 1} of {totalPages}
              </span>
              <button 
                className="btn btn-secondary" 
                onClick={() => fetchAuditLogs(currentPage + 1)}
                disabled={currentPage === totalPages - 1}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default Admin;
