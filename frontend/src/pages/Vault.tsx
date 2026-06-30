import React, { useState, useEffect } from 'react';
import api from '../services/api';

const Vault: React.FC = () => {
  const [records, setRecords] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Store Modal State
  const [storeModalOpen, setStoreModalOpen] = useState(false);
  const [secretName, setSecretName] = useState('');
  const [secretVal, setSecretVal] = useState('');
  const [storing, setStoring] = useState(false);

  // Decrypt Modal State
  const [decryptModalOpen, setDecryptModalOpen] = useState(false);
  const [decryptedName, setDecryptedName] = useState('');
  const [decryptedSecret, setDecryptedSecret] = useState('');
  const [decrypting, setDecrypting] = useState(false);

  const fetchRecords = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/vault');
      setRecords(response.data || []);
    } catch {
      setError('Failed to fetch records from the vault.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRecords();
  }, []);

  const handleStore = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setStoring(true);

    try {
      await api.post('/api/vault/store', { name: secretName, secret: secretVal });
      setSuccess('Secret successfully encrypted and stored.');
      setSecretName('');
      setSecretVal('');
      setStoreModalOpen(false);
      fetchRecords();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to encrypt and store secret.');
    } finally {
      setStoring(false);
    }
  };

  const handleDecrypt = async (id: string, name: string) => {
    setError('');
    setDecrypting(true);
    setDecryptedName(name);
    setDecryptedSecret('');

    try {
      const response = await api.get(`/api/vault/${id}`);
      setDecryptedSecret(response.data.secret);
      setDecryptModalOpen(true);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to decrypt secret. Access denied.');
    } finally {
      setDecrypting(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to soft-delete this record? It will be removed from your vault list for audit compliance.')) {
      return;
    }
    setError('');
    setSuccess('');

    try {
      await api.delete(`/api/vault/${id}`);
      setSuccess('Secret successfully removed.');
      fetchRecords();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to delete secret.');
    }
  };

  return (
    <div className="app-container" style={{ paddingTop: '40px', paddingBottom: '60px', flex: 1, textAlign: 'left' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h1 style={{ fontSize: '36px', fontWeight: 700, margin: '0 0 8px' }}>Vault Storage</h1>
          <p style={{ color: 'var(--color-text-secondary)' }}>Manage your secure banking keys, credentials, and configuration files.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setStoreModalOpen(true)}>
          + Store New Secret
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--color-text-secondary)' }}>Querying secure storage...</div>
      ) : records.length === 0 ? (
        <div className="card" style={{ padding: '60px', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
          <div style={{ fontSize: '48px', marginBottom: '16px' }}>📭</div>
          <h3>No secrets stored yet</h3>
          <p style={{ marginTop: '8px' }}>Store your first credential using the button above.</p>
        </div>
      ) : (
        <div className="table-container">
          <table className="vault-table">
            <thead>
              <tr>
                <th>Secret Name</th>
                <th>Algorithm Used</th>
                <th>Key Version</th>
                <th>Created At</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td style={{ fontWeight: 600 }}>{record.name}</td>
                  <td>
                    <span className={`badge ${record.algorithmUsed === 'AES' ? 'badge-primary' : 'badge-secondary'}`}>
                      {record.algorithmUsed}
                    </span>
                  </td>
                  <td>v{record.keyVersion}</td>
                  <td style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>
                    {new Date(record.createdAt).toLocaleString()}
                  </td>
                  <td style={{ textAlign: 'right', display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                    <button 
                      className="btn btn-secondary" 
                      style={{ padding: '6px 12px', fontSize: '13px' }}
                      onClick={() => handleDecrypt(record.id, record.name)}
                      disabled={decrypting}
                    >
                      {decrypting && decryptedName === record.name ? 'Decrypting...' : 'View Plaintext'}
                    </button>
                    <button 
                      className="btn btn-danger" 
                      style={{ padding: '6px 12px', fontSize: '13px' }}
                      onClick={() => handleDelete(record.id)}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Store Secret Modal */}
      {storeModalOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">Store New Secret</div>
            <form onSubmit={handleStore}>
              <div className="form-group">
                <label className="form-label">Secret Label / Name</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. database-root-credentials" 
                  value={secretName}
                  onChange={(e) => setSecretName(e.target.value)}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: '24px' }}>
                <label className="form-label">Secret Payload (plaintext)</label>
                <textarea 
                  className="form-input" 
                  style={{ minHeight: '120px', resize: 'vertical' }}
                  placeholder="Paste your sensitive keys or credentials here..." 
                  value={secretVal}
                  onChange={(e) => setSecretVal(e.target.value)}
                  required
                />
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setStoreModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={storing}>
                  {storing ? 'Encrypting...' : 'Secure & Store'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Decrypt / View Secret Modal */}
      {decryptModalOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">Decrypted Secret Material</div>
            <div style={{ textAlign: 'left', marginBottom: '20px' }}>
              <div style={{ fontSize: '13px', textTransform: 'uppercase', color: 'var(--color-text-secondary)', fontWeight: 600, marginBottom: '6px' }}>Label</div>
              <div style={{ color: 'white', fontWeight: 600, fontSize: '16px', marginBottom: '20px' }}>{decryptedName}</div>
              
              <div style={{ fontSize: '13px', textTransform: 'uppercase', color: 'var(--color-text-secondary)', fontWeight: 600, marginBottom: '6px' }}>Plaintext Payload</div>
              <pre style={{ 
                background: 'rgba(0,0,0,0.3)', 
                border: '1px solid var(--border-color)', 
                borderRadius: '8px', 
                padding: '16px', 
                color: 'var(--color-primary)', 
                fontSize: '14px', 
                overflowX: 'auto',
                fontFamily: 'var(--font-mono)'
              }}>
                {decryptedSecret}
              </pre>
            </div>
            <div className="modal-footer">
              <button className="btn btn-primary" onClick={() => setDecryptModalOpen(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Vault;
