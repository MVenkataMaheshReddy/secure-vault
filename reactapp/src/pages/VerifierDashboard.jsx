import React, { useState, useEffect, useCallback } from "react";
import { Link, useNavigate } from "react-router-dom";
import { fetchVerificationRecords, revokeVerification } from "../services/api";
import "./VerifierDashboard.css";

export default function VerifierDashboard() {
  const navigate = useNavigate();

  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [toast, setToast] = useState(null);

  // Quick lookup input state
  const [quickVerifyId, setQuickVerifyId] = useState("");

  // Revocation modal state
  const [revokingDoc, setRevokingDoc] = useState(null);
  const [revoking, setRevoking] = useState(false);

  const showToast = (message, type = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const loadRecords = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetchVerificationRecords();
      setRecords(res.data || []);
    } catch (err) {
      console.error("Failed to load verification records:", err);
      setError(err.response?.data?.message || "Failed to load verification records.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRecords();
  }, [loadRecords]);

  const handleQuickLookup = (e) => {
    e.preventDefault();
    if (quickVerifyId.trim()) {
      const cleanId = quickVerifyId.trim().toUpperCase();
      navigate(`/verify/${cleanId}`);
    }
  };

  const handleConfirmRevoke = async () => {
    if (!revokingDoc) return;
    try {
      setRevoking(true);
      await revokeVerification(revokingDoc.documentId);
      showToast(`Verification for document "${revokingDoc.documentTitle}" has been REVOKED.`, "success");
      setRevokingDoc(null);
      await loadRecords();
    } catch (err) {
      console.error("Error revoking verification:", err);
      showToast(err.response?.data?.message || "Failed to revoke verification.", "error");
    } finally {
      setRevoking(false);
    }
  };

  const filteredRecords = records.filter((r) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      r.documentTitle?.toLowerCase().includes(q) ||
      r.verificationId?.toLowerCase().includes(q) ||
      r.ownerName?.toLowerCase().includes(q) ||
      r.category?.toLowerCase().includes(q) ||
      r.status?.toLowerCase().includes(q)
    );
  });

  const formatDate = (dateStr) => {
    if (!dateStr) return "N/A";
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="verifier-dashboard-container">
      {/* Toast Alert */}
      {toast && (
        <div className={`toast-notification ${toast.type}`}>
          {toast.message}
        </div>
      )}

      {/* Header */}
      <div className="dashboard-header">
        <div>
          <h1 className="dashboard-title">Official Document Verification Console</h1>
          <p className="dashboard-subtitle">
            Admin & Verifier console for auditing certificates, verifying authenticity, and revoking credentials.
          </p>
        </div>
        <button onClick={loadRecords} className="btn-refresh" title="Refresh records">
          🔄 Refresh
        </button>
      </div>

      {/* Quick Lookup Bar */}
      <div className="quick-verify-card">
        <form onSubmit={handleQuickLookup} className="quick-verify-form">
          <label htmlFor="quick-id-input" className="quick-verify-label">
            🔎 Fast Verification Lookup by ID:
          </label>
          <div className="quick-verify-row">
            <input
              id="quick-id-input"
              type="text"
              value={quickVerifyId}
              onChange={(e) => setQuickVerifyId(e.target.value.toUpperCase())}
              placeholder="e.g. SV-1A2B3C4D"
              className="quick-input"
            />
            <button
              type="submit"
              disabled={!quickVerifyId.trim()}
              className="btn-quick-verify"
            >
              Verify Certificate
            </button>
          </div>
        </form>
      </div>

      {/* Stats summary */}
      <div className="stats-row">
        <div className="stat-card">
          <span className="stat-label">Total Certificates</span>
          <span className="stat-num">{records.length}</span>
        </div>
        <div className="stat-card stat-valid">
          <span className="stat-label">Active / Valid</span>
          <span className="stat-num">
            {records.filter((r) => r.status === "VALID").length}
          </span>
        </div>
        <div className="stat-card stat-revoked">
          <span className="stat-label">Revoked</span>
          <span className="stat-num">
            {records.filter((r) => r.status === "REVOKED").length}
          </span>
        </div>
      </div>

      {/* Search and Filter */}
      <div className="table-controls">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Filter by title, verification ID, owner, or status..."
          className="table-search-input"
        />
        <span className="table-count-label">
          Showing {filteredRecords.length} of {records.length} records
        </span>
      </div>

      {/* Records Table */}
      {loading ? (
        <div className="table-loading">
          <div className="table-spinner"></div>
          <p>Loading verification records...</p>
        </div>
      ) : error ? (
        <div className="table-error-alert">⚠️ {error}</div>
      ) : filteredRecords.length === 0 ? (
        <div className="table-empty-card">
          <p>No verification records match your search.</p>
        </div>
      ) : (
        <div className="records-table-wrapper">
          <table className="records-table">
            <thead>
              <tr>
                <th>Verification ID</th>
                <th>Document Title</th>
                <th>Category</th>
                <th>Owner</th>
                <th>Ver.</th>
                <th>Issued Date</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredRecords.map((rec) => (
                <tr key={rec.id || rec.verificationId}>
                  <td>
                    <code className="table-id-code">{rec.verificationId}</code>
                  </td>
                  <td>
                    <Link
                      to={`/documentsfetch/${rec.documentId}`}
                      className="table-doc-title-link"
                      title="View Document Details"
                    >
                      {rec.documentTitle}
                    </Link>
                  </td>
                  <td>
                    <span className="table-category-badge">{rec.category || "General"}</span>
                  </td>
                  <td>{rec.ownerName || "Unknown"}</td>
                  <td>v{rec.currentVersion || 1}</td>
                  <td>{formatDate(rec.createdAt)}</td>
                  <td>
                    <span
                      className={`status-badge ${
                        rec.status === "VALID" ? "badge-valid" : "badge-revoked"
                      }`}
                    >
                      {rec.status}
                    </span>
                  </td>
                  <td className="table-actions-cell">
                    <button
                      onClick={() => navigate(`/verify/${rec.verificationId}`)}
                      className="btn-action-view"
                      title="View Public Certificate"
                    >
                      Public Page
                    </button>
                    {rec.status === "VALID" && (
                      <button
                        onClick={() => setRevokingDoc(rec)}
                        className="btn-action-revoke"
                        title="Revoke Verification"
                      >
                        Revoke
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Revocation Confirmation Modal */}
      {revokingDoc && (
        <div className="modal-backdrop">
          <div className="modal-dialog">
            <h3 className="modal-title">⚠️ Revoke Document Verification</h3>
            <p className="modal-body-text">
              Are you sure you want to revoke the public verification for:
            </p>
            <div className="modal-doc-highlight">
              <strong>{revokingDoc.documentTitle}</strong>
              <div>Verification ID: <code>{revokingDoc.verificationId}</code></div>
            </div>
            <p className="modal-warning-text">
              Once revoked, the public verification page and any scanned QR code will immediately indicate
              that this document is <strong>REVOKED / INVALID</strong>.
            </p>
            <div className="modal-actions">
              <button
                onClick={() => setRevokingDoc(null)}
                disabled={revoking}
                className="btn-cancel"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmRevoke}
                disabled={revoking}
                className="btn-confirm-revoke"
              >
                {revoking ? "Revoking..." : "Confirm Revocation"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
