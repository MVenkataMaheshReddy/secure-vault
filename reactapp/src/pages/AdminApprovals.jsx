import React, { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import {
  fetchAdminApprovals,
  approveAdminApproval,
  rejectAdminApproval,
  fetchPendingApprovalsCount,
  viewDocument,
  downloadDocument,
} from "../services/api";
import "./AdminApprovals.css";

export default function AdminApprovals() {
  const navigate = useNavigate();

  // Redux or localStorage user
  const reduxUser = useSelector((state) => state.auth?.user);
  let currentUser = reduxUser;
  if (!currentUser) {
    try {
      currentUser = JSON.parse(
        localStorage.getItem("user") || localStorage.getItem("dl_user") || "null"
      );
    } catch {
      currentUser = null;
    }
  }

  const [activeTab, setActiveTab] = useState("PENDING"); // PENDING, APPROVED, REJECTED, ALL
  const [approvals, setApprovals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);
  const [pendingCount, setPendingCount] = useState(0);

  // Pagination
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Decision Modal State
  const [decisionModal, setDecisionModal] = useState({
    isOpen: false,
    type: "APPROVE", // "APPROVE" or "REJECT"
    approval: null,
    comments: "",
    submitting: false,
    validationError: "",
  });

  // Preview Modal State
  const [previewModal, setPreviewModal] = useState({
    isOpen: false,
    title: "",
    blobUrl: null,
    isImage: false,
    loading: false,
    documentId: null,
  });

  const showToast = (message, type = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const loadPendingCount = useCallback(async () => {
    try {
      const res = await fetchPendingApprovalsCount();
      if (res.data) {
        setPendingCount(res.data.count ?? res.data.pending ?? 0);
      }
    } catch (e) {
      console.warn("Failed to load pending approvals count", e);
    }
  }, []);

  const loadApprovals = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {
        status: activeTab,
        page,
        size: pageSize,
      };
      const res = await fetchAdminApprovals(params);
      if (res.data) {
        setApprovals(Array.isArray(res.data.content) ? res.data.content : []);
        setTotalPages(res.data.totalPages || 0);
        setTotalElements(res.data.totalElements || 0);
      } else {
        setApprovals([]);
      }
    } catch (err) {
      console.error("Error loading approvals:", err);
      setError("Failed to load approvals. Ensure you have administrator permissions.");
    } finally {
      setLoading(false);
    }
  }, [activeTab, page, pageSize]);

  useEffect(() => {
    loadApprovals();
    loadPendingCount();
  }, [loadApprovals, loadPendingCount]);

  const handleTabChange = (newTab) => {
    setActiveTab(newTab);
    setPage(0);
  };

  const openDecisionModal = (type, approval) => {
    setDecisionModal({
      isOpen: true,
      type,
      approval,
      comments: "",
      submitting: false,
      validationError: "",
    });
  };

  const closeDecisionModal = () => {
    setDecisionModal({
      isOpen: false,
      type: "APPROVE",
      approval: null,
      comments: "",
      submitting: false,
      validationError: "",
    });
  };

  const handleDecisionSubmit = async (e) => {
    e.preventDefault();
    if (!decisionModal.approval) return;

    if (decisionModal.type === "REJECT" && !decisionModal.comments.trim()) {
      setDecisionModal((prev) => ({
        ...prev,
        validationError: "Rejection comment is required to provide feedback.",
      }));
      return;
    }

    setDecisionModal((prev) => ({ ...prev, submitting: true, validationError: "" }));

    try {
      if (decisionModal.type === "APPROVE") {
        await approveAdminApproval(decisionModal.approval.id, decisionModal.comments.trim());
        showToast(
          `Document "${decisionModal.approval.documentTitle}" approved successfully! Verification QR generated.`
        );
      } else {
        await rejectAdminApproval(decisionModal.approval.id, decisionModal.comments.trim());
        showToast(`Document "${decisionModal.approval.documentTitle}" rejected with feedback.`);
      }
      closeDecisionModal();
      loadApprovals();
      loadPendingCount();
    } catch (err) {
      console.error("Error submitting decision:", err);
      const errMsg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        `Failed to ${decisionModal.type.toLowerCase()} document.`;
      setDecisionModal((prev) => ({
        ...prev,
        submitting: false,
        validationError: errMsg,
      }));
    }
  };

  const handlePreview = async (documentId, documentTitle, originalFileName) => {
    try {
      setPreviewModal({
        isOpen: true,
        title: documentTitle || "Document Preview",
        blobUrl: null,
        isImage: false,
        loading: true,
        documentId,
      });

      const res = await viewDocument(documentId);
      const blob = new Blob([res.data], {
        type: res.headers["content-type"] || "application/pdf",
      });
      const url = URL.createObjectURL(blob);
      const isImg =
        res.headers["content-type"]?.startsWith("image/") ||
        originalFileName?.match(/\.(png|jpe?g|gif|webp)$/i);

      setPreviewModal((prev) => ({
        ...prev,
        blobUrl: url,
        isImage: !!isImg,
        loading: false,
      }));
    } catch (err) {
      console.error("Error loading preview:", err);
      showToast(err.response?.data?.message || "Failed to load document preview", "error");
      setPreviewModal({
        isOpen: false,
        title: "",
        blobUrl: null,
        isImage: false,
        loading: false,
        documentId: null,
      });
    }
  };

  const closePreviewModal = () => {
    if (previewModal.blobUrl) {
      URL.revokeObjectURL(previewModal.blobUrl);
    }
    setPreviewModal({
      isOpen: false,
      title: "",
      blobUrl: null,
      isImage: false,
      loading: false,
      documentId: null,
    });
  };

  const handleDownload = async (documentId, originalFileName) => {
    try {
      const res = await downloadDocument(documentId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", originalFileName || `document-${documentId}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showToast("Download started");
    } catch (err) {
      console.error("Download failed:", err);
      showToast(err.response?.data?.message || "Failed to download document", "error");
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return "N/A";
    const d = new Date(dateStr);
    return isNaN(d.getTime()) ? dateStr : d.toLocaleString();
  };

  const isOwnDocument = (approval) => {
    if (!currentUser || !approval) return false;
    const userEmail = (currentUser.email || "").toLowerCase();
    const ownerEmail = (approval.ownerEmail || "").toLowerCase();
    return userEmail && ownerEmail && userEmail === ownerEmail;
  };

  return (
    <div className="admin-approvals-page">
      {toast && (
        <div className={`approvals-toast ${toast.type}`}>
          <span>{toast.type === "success" ? "✅" : "⚠️"}</span>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Header */}
      <div className="approvals-header">
        <div>
          <h1 className="approvals-title">🛡️ Admin Document Approvals</h1>
          <p className="approvals-subtitle">
            Review, inspect, approve, or reject employee document submissions.
          </p>
        </div>
        <div className="approvals-header-badge">
          <span className="badge-pulse"></span>
          Pending Submissions: <strong>{pendingCount}</strong>
        </div>
      </div>

      {/* Tabs */}
      <div className="approvals-tabs" role="tablist">
        <button
          className={`tab-btn ${activeTab === "PENDING" ? "active" : ""}`}
          onClick={() => handleTabChange("PENDING")}
          role="tab"
          aria-selected={activeTab === "PENDING"}
        >
          Pending Review
          {pendingCount > 0 && <span className="tab-count-badge">{pendingCount}</span>}
        </button>
        <button
          className={`tab-btn ${activeTab === "APPROVED" ? "active" : ""}`}
          onClick={() => handleTabChange("APPROVED")}
          role="tab"
          aria-selected={activeTab === "APPROVED"}
        >
          Approved History
        </button>
        <button
          className={`tab-btn ${activeTab === "REJECTED" ? "active" : ""}`}
          onClick={() => handleTabChange("REJECTED")}
          role="tab"
          aria-selected={activeTab === "REJECTED"}
        >
          Rejected History
        </button>
        <button
          className={`tab-btn ${activeTab === "ALL" ? "active" : ""}`}
          onClick={() => handleTabChange("ALL")}
          role="tab"
          aria-selected={activeTab === "ALL"}
        >
          All Requests
        </button>
      </div>

      {/* Content */}
      <div className="approvals-content-card">
        {loading ? (
          <div className="loading-state">
            <div className="spinner"></div>
            <p>Loading approval queue...</p>
          </div>
        ) : error ? (
          <div className="error-banner">
            <span>⚠️</span>
            <p>{error}</p>
          </div>
        ) : approvals.length === 0 ? (
          <div className="empty-state">
            <span className="empty-icon">📂</span>
            <h3>No requests found in this view</h3>
            <p>
              {activeTab === "PENDING"
                ? "All caught up! No documents are currently pending approval."
                : `There are no ${activeTab.toLowerCase()} requests to show.`}
            </p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="approvals-table">
              <thead>
                <tr>
                  <th>Title & Document</th>
                  <th>Uploader</th>
                  <th>Submitted Date</th>
                  <th>Version</th>
                  <th>File Type</th>
                  <th>Status</th>
                  {activeTab !== "PENDING" && <th>Decided By & Feedback</th>}
                  <th className="actions-col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {approvals.map((approval) => {
                  const ownDoc = isOwnDocument(approval);
                  const fileTypeDisplay =
                    approval.fileType ||
                    (approval.originalFileName && approval.originalFileName.includes(".")
                      ? approval.originalFileName.split(".").pop().toUpperCase()
                      : "FILE");

                  return (
                    <tr key={approval.id} className="approval-row">
                      <td className="doc-col">
                        <strong
                          className="doc-title-link"
                          onClick={() => navigate(`/documentsfetch/${approval.documentId}`)}
                          title="View Full Document Details"
                        >
                          {approval.documentTitle || "Untitled Document"}
                        </strong>
                        <div className="doc-submeta">
                          <span>ID: #{approval.documentId}</span>
                          {approval.originalFileName && (
                            <span> • {approval.originalFileName}</span>
                          )}
                          <span className="category-pill">{approval.category || "GENERAL"}</span>
                        </div>
                      </td>
                      <td className="uploader-col">
                        <div className="user-info">
                          <strong>{approval.ownerName || "Unknown"}</strong>
                          <span className="email-subtext">{approval.ownerEmail || "N/A"}</span>
                        </div>
                      </td>
                      <td className="date-col">{formatDate(approval.createdAt)}</td>
                      <td className="version-col">
                        <span className="version-badge">v{approval.version || 1}</span>
                      </td>
                      <td className="filetype-col">
                        <span className="filetype-badge">{fileTypeDisplay}</span>
                      </td>
                      <td className="status-col">
                        <span className={`status-pill status-${approval.status?.toLowerCase()}`}>
                          {approval.status}
                        </span>
                      </td>
                      {activeTab !== "PENDING" && (
                        <td className="reviewer-col">
                          {approval.reviewerName ? (
                            <div>
                              <strong>{approval.reviewerName}</strong>
                              <span className="email-subtext">{approval.reviewerEmail}</span>
                              {approval.comments && (
                                <div className="reviewer-feedback" title={approval.comments}>
                                  <em>"{approval.comments}"</em>
                                </div>
                              )}
                            </div>
                          ) : (
                            <span className="text-muted">—</span>
                          )}
                        </td>
                      )}
                      <td className="actions-col">
                        <div className="actions-btn-group">
                          <button
                            className="btn-action btn-preview"
                            title="Preview Document"
                            onClick={() =>
                              handlePreview(
                                approval.documentId,
                                approval.documentTitle,
                                approval.originalFileName
                              )
                            }
                          >
                            👁️ Preview
                          </button>
                          <button
                            className="btn-action btn-download"
                            title="Download Document"
                            onClick={() =>
                              handleDownload(approval.documentId, approval.originalFileName)
                            }
                          >
                            ⬇️ Download
                          </button>

                          {approval.status === "PENDING" && (
                            <>
                              <button
                                className="btn-action btn-approve"
                                title={ownDoc ? "Cannot approve your own document" : "Approve Document"}
                                disabled={ownDoc}
                                onClick={() => openDecisionModal("APPROVE", approval)}
                              >
                                ✅ Approve
                              </button>
                              <button
                                className="btn-action btn-reject"
                                title={ownDoc ? "Cannot review your own document" : "Reject Document"}
                                disabled={ownDoc}
                                onClick={() => openDecisionModal("REJECT", approval)}
                              >
                                ❌ Reject
                              </button>
                            </>
                          )}
                        </div>
                        {ownDoc && approval.status === "PENDING" && (
                          <div className="own-doc-warning">
                            <small>⚠️ Cannot approve own doc</small>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        {!loading && totalElements > 0 && (
          <div className="approvals-pagination">
            <div className="pagination-text">
              Showing <strong>{page * pageSize + 1}</strong> to{" "}
              <strong>{Math.min((page + 1) * pageSize, totalElements)}</strong> of{" "}
              <strong>{totalElements}</strong> requests
            </div>
            <div className="pagination-controls">
              <select
                className="page-size-selector"
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value));
                  setPage(0);
                }}
              >
                <option value={5}>5 per page</option>
                <option value={10}>10 per page</option>
                <option value={20}>20 per page</option>
              </select>
              <button
                className="btn-page"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Prev
              </button>
              <span className="page-current">
                Page {page + 1} of {totalPages || 1}
              </span>
              <button
                className="btn-page"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Decision Modal (Approve / Reject) */}
      {decisionModal.isOpen && (
        <div className="modal-backdrop" onClick={closeDecisionModal}>
          <div
            className="decision-modal-card"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className={`decision-modal-header ${decisionModal.type.toLowerCase()}`}>
              <h2>
                {decisionModal.type === "APPROVE" ? "✅ Approve Document" : "❌ Reject Document"}
              </h2>
              <button className="close-btn" onClick={closeDecisionModal}>
                &times;
              </button>
            </div>

            <form onSubmit={handleDecisionSubmit}>
              <div className="decision-modal-body">
                <div className="doc-summary-box">
                  <p>
                    <strong>Document:</strong> {decisionModal.approval?.documentTitle}
                  </p>
                  <p>
                    <strong>Uploader:</strong> {decisionModal.approval?.ownerName} (
                    {decisionModal.approval?.ownerEmail})
                  </p>
                  <p>
                    <strong>Version:</strong> v{decisionModal.approval?.version}
                  </p>
                </div>

                {decisionModal.type === "APPROVE" ? (
                  <div className="approval-notice">
                    <p>
                      Approving this document will mark it as <strong>APPROVED</strong> and
                      automatically generate a verification ID with a secure QR code.
                    </p>
                    <label htmlFor="approve-comments" className="form-label">
                      Approval Comments (Optional):
                    </label>
                    <textarea
                      id="approve-comments"
                      rows={3}
                      className="form-textarea"
                      placeholder="Add any internal approval remarks or notes..."
                      value={decisionModal.comments}
                      onChange={(e) =>
                        setDecisionModal((prev) => ({ ...prev, comments: e.target.value }))
                      }
                    />
                  </div>
                ) : (
                  <div className="rejection-notice">
                    <p>
                      Please provide a clear reason for rejection. This feedback will be displayed
                      to the employee so they can make necessary corrections and resubmit.
                    </p>
                    <label htmlFor="reject-comments" className="form-label required">
                      Rejection Reason (Required): *
                    </label>
                    <textarea
                      id="reject-comments"
                      rows={4}
                      required
                      className="form-textarea"
                      placeholder="Explain why this document was rejected (e.g. illegible scan, missing signature)..."
                      value={decisionModal.comments}
                      onChange={(e) =>
                        setDecisionModal((prev) => ({
                          ...prev,
                          comments: e.target.value,
                          validationError: "",
                        }))
                      }
                    />
                  </div>
                )}

                {decisionModal.validationError && (
                  <div className="modal-error-box">{decisionModal.validationError}</div>
                )}
              </div>

              <div className="decision-modal-footer">
                <button
                  type="button"
                  className="btn-cancel"
                  onClick={closeDecisionModal}
                  disabled={decisionModal.submitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className={`btn-confirm ${decisionModal.type.toLowerCase()}`}
                  disabled={decisionModal.submitting}
                >
                  {decisionModal.submitting
                    ? "Submitting..."
                    : decisionModal.type === "APPROVE"
                    ? "Confirm Approval"
                    : "Confirm Rejection"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Document Preview Modal */}
      {previewModal.isOpen && (
        <div className="modal-backdrop" onClick={closePreviewModal}>
          <div
            className="preview-modal-card"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className="preview-modal-header">
              <h3>📄 {previewModal.title}</h3>
              <button className="close-btn" onClick={closePreviewModal}>
                &times;
              </button>
            </div>
            <div className="preview-modal-body">
              {previewModal.loading ? (
                <div className="preview-loading">
                  <div className="spinner"></div>
                  <p>Streaming document preview...</p>
                </div>
              ) : previewModal.blobUrl ? (
                previewModal.isImage ? (
                  <div className="image-preview-wrapper">
                    <img
                      src={previewModal.blobUrl}
                      alt="Document Preview"
                      className="preview-img"
                    />
                  </div>
                ) : (
                  <iframe
                    src={previewModal.blobUrl}
                    title="PDF Document Preview"
                    className="preview-iframe"
                  />
                )
              ) : (
                <div className="preview-error">
                  <p>Preview unavailable for this file format.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
