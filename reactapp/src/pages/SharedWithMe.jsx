import React, { useEffect, useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { fetchSharedWithMe, viewDocument, downloadDocument } from "../services/api";
import "./SharedWithMe.css";

export default function SharedWithMe() {
  const [sharedDocs, setSharedDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [pageSize] = useState(10);
  const [searchFilter, setSearchFilter] = useState("");

  // Preview modal state
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [previewTitle, setPreviewTitle] = useState("");
  const [previewIsImage, setPreviewIsImage] = useState(false);
  const [showPreviewModal, setShowPreviewModal] = useState(false);
  const [downloadingId, setDownloadingId] = useState(null);

  const loadSharedDocs = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetchSharedWithMe({ page, size: pageSize });
      if (res.data) {
        if (Array.isArray(res.data)) {
          setSharedDocs(res.data);
          setTotalElements(res.data.length);
          setTotalPages(1);
        } else if (Array.isArray(res.data.content)) {
          setSharedDocs(res.data.content);
          setTotalPages(res.data.totalPages || 0);
          setTotalElements(res.data.totalElements || 0);
        } else {
          setSharedDocs([]);
        }
      } else {
        setSharedDocs([]);
      }
    } catch (err) {
      console.error("Failed to load shared documents:", err);
      setError(err.response?.data?.message || "Failed to load shared documents");
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    loadSharedDocs();
  }, [loadSharedDocs]);

  // Handle preview inline
  const handleView = async (doc) => {
    try {
      setLoadingPreview(true);
      setShowPreviewModal(true);
      setPreviewTitle(doc.documentTitle || "Document Preview");
      const isImg = Boolean(doc.contentType && doc.contentType.startsWith("image/"));
      setPreviewIsImage(isImg);

      const res = await viewDocument(doc.documentId);
      const blob = new Blob([res.data], { type: doc.contentType || "application/pdf" });
      const url = URL.createObjectURL(blob);
      setPreviewBlobUrl(url);
    } catch (err) {
      console.error("Failed to preview document:", err);
      const status = err.response?.status;
      if (status === 403 || status === 404) {
        alert("Access revoked/expired: You no longer have permission to access this document.");
      } else {
        alert(err.response?.data?.message || "Failed to open document preview");
      }
      setShowPreviewModal(false);
    } finally {
      setLoadingPreview(false);
    }
  };

  const closePreview = () => {
    setShowPreviewModal(false);
    if (previewBlobUrl) {
      URL.revokeObjectURL(previewBlobUrl);
      setPreviewBlobUrl(null);
    }
  };

  // Handle download
  const handleDownload = async (doc) => {
    if (doc.permission !== "DOWNLOAD") {
      alert("You only have VIEW (read-only) permissions for this document.");
      return;
    }
    try {
      setDownloadingId(doc.documentId);
      const res = await downloadDocument(doc.documentId);
      const blob = new Blob([res.data], { type: doc.contentType || "application/octet-stream" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = doc.originalFileName || `${doc.documentTitle || "document"}.bin`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error("Download failed:", err);
      const status = err.response?.status;
      if (status === 403 || status === 404) {
        alert("Access revoked/expired: You do not have permission to download this document.");
      } else {
        alert(err.response?.data?.message || "Failed to download document");
      }
    } finally {
      setDownloadingId(null);
    }
  };

  const filteredDocs = sharedDocs.filter((d) => {
    if (!searchFilter.trim()) return true;
    const q = searchFilter.toLowerCase();
    return (
      (d.documentTitle && d.documentTitle.toLowerCase().includes(q)) ||
      (d.category && d.category.toLowerCase().includes(q)) ||
      (d.sharedByName && d.sharedByName.toLowerCase().includes(q)) ||
      (d.sharedByEmail && d.sharedByEmail.toLowerCase().includes(q))
    );
  });

  return (
    <div className="shared-with-me-container">
      {/* Header Banner */}
      <div className="shared-header-card">
        <div className="shared-header-info">
          <h1>🤝 Documents Shared with Me</h1>
          <p>
            Secure, end-to-end access to files and records shared directly with your account by colleagues and administrators.
          </p>
        </div>
        <div className="shared-header-stats">
          <div className="stat-pill">
            <span className="stat-label">Total Shared:</span>
            <span className="stat-value">{totalElements}</span>
          </div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="shared-controls-bar">
        <input
          type="text"
          className="shared-search-input"
          placeholder="Filter by title, category, or owner email..."
          value={searchFilter}
          onChange={(e) => setSearchFilter(e.target.value)}
        />
      </div>

      {/* Main Table Content */}
      <div className="shared-table-wrapper">
        {loading ? (
          <div className="loading-state">
            <div className="spinner"></div>
            <p>Loading shared documents...</p>
          </div>
        ) : error ? (
          <div className="error-state">
            <p>⚠️ {error}</p>
            <button className="btn-retry" onClick={loadSharedDocs}>
              Retry
            </button>
          </div>
        ) : filteredDocs.length === 0 ? (
          <div className="empty-state">
            <span className="empty-icon">📂</span>
            <h3>No Documents Shared With You</h3>
            <p>When other users or administrators grant you access to documents, they will appear here.</p>
          </div>
        ) : (
          <table className="shared-table" aria-label="Shared documents table">
            <thead>
              <tr>
                <th>Document</th>
                <th>Category</th>
                <th>Shared By</th>
                <th>Your Permission</th>
                <th>Expires</th>
                <th>Shared Date</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredDocs.map((doc) => {
                const canDownload = doc.permission === "DOWNLOAD";
                return (
                  <tr key={doc.shareId} className="shared-row">
                    <td>
                      <div className="shared-doc-title">
                        <Link
                          to={`/documents/${doc.documentId}`}
                          className="doc-clickable-link"
                          title="View Full Document Details"
                        >
                          {doc.documentTitle || doc.title}
                        </Link>
                        <span className="file-info-sub">
                          {doc.originalFileName || "document"} • v{doc.currentVersion || 1}
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className="category-pill">{doc.category || "GENERAL"}</span>
                    </td>
                    <td>
                      <div className="shared-by-info">
                        <strong>{doc.sharedByName || doc.ownerName || "Colleague"}</strong>
                        <span className="shared-by-email">{doc.sharedByEmail || doc.ownerEmail}</span>
                      </div>
                    </td>
                    <td>
                      <span
                        className={`permission-badge ${doc.permission?.toLowerCase()}`}
                        title={
                          canDownload
                            ? "You can view and download this file"
                            : "Read-only: You may view inline but cannot download"
                        }
                      >
                        {canDownload ? "⬇️ DOWNLOAD" : "👁️ VIEW ONLY"}
                      </span>
                    </td>
                    <td>
                      {doc.expiresAt ? (
                        <span className="expiry-date">
                          {new Date(doc.expiresAt).toLocaleDateString()}
                        </span>
                      ) : (
                        <span className="perpetual-badge">No Expiry</span>
                      )}
                    </td>
                    <td className="date-col">
                      {doc.sharedAt ? new Date(doc.sharedAt).toLocaleDateString() : "Recent"}
                    </td>
                    <td className="actions-col">
                      <div className="action-buttons-group">
                        <button
                          className="btn-tbl-action view"
                          onClick={() => handleView(doc)}
                          title="View Inline"
                        >
                          👁️ View
                        </button>
                        <button
                          className={`btn-tbl-action download ${!canDownload ? "disabled" : ""}`}
                          onClick={() => canDownload && handleDownload(doc)}
                          disabled={!canDownload || downloadingId === doc.documentId}
                          title={canDownload ? "Download document" : "Download not permitted"}
                        >
                          {downloadingId === doc.documentId ? "..." : "⬇️ Download"}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="shared-pagination">
            <button
              className="btn-page"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Previous
            </button>
            <span className="page-indicator">
              Page {page + 1} of {totalPages}
            </span>
            <button
              className="btn-page"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next →
            </button>
          </div>
        )}
      </div>

      {/* INLINE PREVIEW MODAL */}
      {showPreviewModal && (
        <div className="modal-backdrop" onClick={closePreview}>
          <div className="modal-content preview-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>👁️ {previewTitle}</h3>
              <button className="modal-close-btn" onClick={closePreview}>
                ✕
              </button>
            </div>
            <div className="modal-body preview-body">
              {loadingPreview ? (
                <div className="loading-preview">
                  <div className="spinner"></div>
                  <p>Loading document preview...</p>
                </div>
              ) : previewBlobUrl ? (
                previewIsImage ? (
                  <img src={previewBlobUrl} alt="Preview" className="preview-image" />
                ) : (
                  <iframe src={previewBlobUrl} title="Document Preview" className="preview-frame" />
                )
              ) : (
                <p>Preview unavailable.</p>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={closePreview}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
