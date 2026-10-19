import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchTrashDocuments, restoreDocument } from "../services/api";
import "./Trash.css";

export default function Trash() {
  const navigate = useNavigate();
  const [trashDocs, setTrashDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);
  const [restoringId, setRestoringId] = useState(null);

  const showToast = (message, type = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const loadTrash = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetchTrashDocuments();
      setTrashDocs(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.error("Error loading trash:", err);
      setError("Failed to load documents from Trash.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTrash();
  }, []);

  const handleRestore = async (doc) => {
    const docId = doc.id || doc.doc_id;
    try {
      setRestoringId(docId);
      await restoreDocument(docId);
      showToast(`"${doc.title || doc.originalFileName}" restored successfully!`);
      // Remove from list
      setTrashDocs((prev) => prev.filter((d) => (d.id || d.doc_id) !== docId));
    } catch (err) {
      console.error("Restore failed:", err);
      showToast(err.response?.data?.message || "Failed to restore document", "error");
    } finally {
      setRestoringId(null);
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes || bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
  };

  const formatDate = (isoString) => {
    if (!isoString) return "N/A";
    return new Date(isoString).toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  return (
    <div className="trash-page">
      {/* Toast Notification */}
      {toast && (
        <div className={`toast-notification ${toast.type}`}>
          <span>{toast.type === "success" ? "✅" : "⚠️"}</span>
          <span>{toast.message}</span>
        </div>
      )}

      <div className="trash-header">
        <div>
          <h1 className="trash-title">🗑️ Document Trash Bin</h1>
          <p className="trash-subtitle">
            Items in the trash remain safely stored on disk. You can restore them to your active vault at any time.
          </p>
        </div>
        <button
          className="btn-back-docs"
          onClick={() => navigate("/documents")}
        >
          ← Back to Documents
        </button>
      </div>

      {loading ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Retrieving deleted documents...</p>
        </div>
      ) : error ? (
        <div className="error-state">
          <p className="error">{error}</p>
          <button className="btn-retry" onClick={loadTrash}>
            Retry
          </button>
        </div>
      ) : trashDocs.length === 0 ? (
        <div className="empty-state">
          <span className="empty-icon">✨</span>
          <h3>Trash is empty</h3>
          <p>You have no deleted documents in your trash bin.</p>
          <button
            className="btn-back-docs"
            onClick={() => navigate("/documents")}
          >
            Go to Documents
          </button>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="trash-table">
            <thead>
              <tr>
                <th>Document Details</th>
                <th>Category</th>
                <th>Size</th>
                <th>Deleted On</th>
                <th>Status</th>
                <th className="actions-header">Actions</th>
              </tr>
            </thead>
            <tbody>
              {trashDocs.map((doc) => {
                const docId = doc.id || doc.doc_id;
                const docTitle = doc.title || doc.doc_name || doc.originalFileName;
                const isRestoring = restoringId === docId;

                return (
                  <tr key={docId} className="trash-row">
                    <td>
                      <div className="doc-meta-block">
                        <span className="file-badge-icon">🗑️</span>
                        <div>
                          <strong>{docTitle}</strong>
                          <span className="doc-subtext">
                            {doc.originalFileName} • v{doc.currentVersion || 1}
                          </span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="category-badge">{doc.category || "GENERAL"}</span>
                    </td>
                    <td>{formatFileSize(doc.fileSize)}</td>
                    <td className="date-cell">{formatDate(doc.deletedAt || doc.updatedAt)}</td>
                    <td>
                      <span className="status-badge deleted">DELETED</span>
                    </td>
                    <td className="actions-cell">
                      <button
                        className="btn-restore"
                        onClick={() => handleRestore(doc)}
                        disabled={isRestoring}
                      >
                        {isRestoring ? "Restoring..." : "♻️ Restore to Vault"}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
