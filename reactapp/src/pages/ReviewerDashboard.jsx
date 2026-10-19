import React, { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import {
  fetchApprovals,
  approveApproval,
  rejectApproval,
  viewDocument,
  downloadDocument,
} from "../services/api";
import "./ReviewerDashboard.css";

export default function ReviewerDashboard() {
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

  // Pagination
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Decision Modal State
  const [decisionModal, setDecisionModal] = useState({
    isOpen: false,
    type: "APPROVE", // "APPROVE" or "REJECT"
    approval: null,
    comments: "",
    submitting: false,
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

  const loadApprovals = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {
        status: activeTab,
        page,
        size: pageSize,
      };
      const res = await fetchApprovals(params);
      if (res.data) {
        setApprovals(Array.isArray(res.data.content) ? res.data.content : []);
        setTotalPages(res.data.totalPages || 0);
        setTotalElements(res.data.totalElements || 0);
      } else {
        setApprovals([]);
      }
    } catch (err) {
      console.error("Failed to load approvals:", err);
      setError(err.response?.data?.message || "Failed to load approval requests");
    } finally {
      setLoading(false);
    }
  }, [activeTab, page, pageSize]);

  useEffect(() => {
    loadApprovals();
  }, [loadApprovals]);

  // Open Decision Modal
  const openDecisionModal = (approval, type) => {
    // Check self-approval guard
    if (
      approval.ownerEmail &&
      currentUser?.email &&
      approval.ownerEmail.toLowerCase() === currentUser.email.toLowerCase()
    ) {
      showToast("Security Policy: You cannot review or approve your own document.", "error");
      return;
    }

    setDecisionModal({
      isOpen: true,
      type,
      approval,
      comments: "",
      submitting: false,
    });
  };

  const closeDecisionModal = () => {
    setDecisionModal({
      isOpen: false,
      type: "APPROVE",
      approval: null,
      comments: "",
      submitting: false,
    });
  };

  const handleDecisionSubmit = async () => {
    const { type, approval, comments } = decisionModal;
    if (!approval) return;

    if (type === "REJECT" && (!comments || !comments.trim())) {
      showToast("Comments are required when rejecting a document.", "error");
      return;
    }

    try {
      setDecisionModal((prev) => ({ ...prev, submitting: true }));
      if (type === "APPROVE") {
        await approveApproval(approval.id, comments ? comments.trim() : "");
        showToast(`Document "${approval.documentTitle}" APPROVED! Verification ID generated.`, "success");
      } else {
        await rejectApproval(approval.id, comments.trim());
        showToast(`Document "${approval.documentTitle}" REJECTED.`, "success");
      }
      closeDecisionModal();
      loadApprovals();
    } catch (err) {
      console.error("Decision failed:", err);
      showToast(err.response?.data?.message || `Failed to ${type.toLowerCase()} document`, "error");
      setDecisionModal((prev) => ({ ...prev, submitting: false }));
    }
  };

  // Document Preview
  const handlePreview = async (approval) => {
    try {
      setPreviewModal({
        isOpen: true,
        title: approval.documentTitle,
        blobUrl: null,
        isImage: false,
        loading: true,
        documentId: approval.documentId,
      });

      const res = await viewDocument(approval.documentId);
      const mime = res.headers["content-type"] || "application/pdf";
      const blob = new Blob([res.data], { type: mime });
      const url = window.URL.createObjectURL(blob);

      setPreviewModal((prev) => ({
        ...prev,
        blobUrl: url,
        isImage: mime.startsWith("image/"),
        loading: false,
      }));
    } catch (err) {
      console.error("Preview failed:", err);
      showToast("Failed to load document preview or access denied", "error");
      closePreviewModal();
    }
  };

  const closePreviewModal = () => {
    if (previewModal.blobUrl) {
      window.URL.revokeObjectURL(previewModal.blobUrl);
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

  // Download Document
  const handleDownload = async (approval) => {
    try {
      const res = await downloadDocument(approval.documentId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `${approval.documentTitle || "document"}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showToast("Download started successfully");
    } catch (err) {
      console.error("Download failed:", err);
      showToast("Failed to download document", "error");
    }
  };

  const formatDate = (iso) => {
    if (!iso) return "N/A";
    return new Date(iso).toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  return (
    <div className="reviewer-dashboard-container">
      {/* Toast Notification */}
      {toast && (
        <div className={`toast-notification ${toast.type}`}>
          <span>{toast.type === "success" ? "✅" : "⚠️"}</span>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Header Banner */}
      <div className="reviewer-header-card">
        <div className="reviewer-header-info">
          <h1>🛡️ Document Approval Workflow</h1>
          <p>
            Review pending employee submissions. Approving a document automatically
            generates a cryptographically secure Verification ID and ZXing QR code.
          </p>
        </div>
        <div className="reviewer-quick-stats">
          <div className="stat-pill">
            <span className="stat-label">Active Filter:</span>
            <span className="stat-value">{activeTab}</span>
          </div>
          <div className="stat-pill">
            <span className="stat-label">Total Records:</span>
            <span className="stat-value">{totalElements}</span>
          </div>
        </div>
      </div>

      {/* Status Filter Tabs */}
      <div className="approval-filter-tabs">
        <button
          className={`filter-tab ${activeTab === "PENDING" ? "active" : ""}`}
          onClick={() => {
            setActiveTab("PENDING");
            setPage(0);
          }}
        >
          ⏳ Pending Queue
        </button>
        <button
          className={`filter-tab ${activeTab === "APPROVED" ? "active" : ""}`}
          onClick={() => {
            setActiveTab("APPROVED");
            setPage(0);
          }}
        >
          ✅ Approved
        </button>
        <button
          className={`filter-tab ${activeTab === "REJECTED" ? "active" : ""}`}
          onClick={() => {
            setActiveTab("REJECTED");
            setPage(0);
          }}
        >
          ❌ Rejected
        </button>
        <button
          className={`filter-tab ${activeTab === "ALL" ? "active" : ""}`}
          onClick={() => {
            setActiveTab("ALL");
            setPage(0);
          }}
        >
          📁 All History
        </button>
      </div>

      {/* Approvals Table */}
      <div className="approval-table-wrapper">
        {loading ? (
          <div className="loading-state">
            <div className="spinner"></div>
            <p>Loading approval queue...</p>
          </div>
        ) : error ? (
          <div className="error-state">
            <p>⚠️ {error}</p>
            <button className="btn-retry" onClick={loadApprovals}>
              Retry
            </button>
          </div>
        ) : approvals.length === 0 ? (
          <div className="empty-state">
            <span className="empty-icon">📭</span>
            <h3>No {activeTab.toLowerCase()} approvals found</h3>
            <p>
              {activeTab === "PENDING"
                ? "All caught up! There are no documents awaiting review."
                : `No documents found with status ${activeTab}.`}
            </p>
          </div>
        ) : (
          <table className="approval-table" aria-label="Approvals queue table">
            <thead>
              <tr>
                <th>Document</th>
                <th>Owner</th>
                <th>Version</th>
                <th>Submitted Date</th>
                <th>Status</th>
                <th>Reviewer / Comments</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {approvals.map((item) => {
                const isOwnDoc =
                  currentUser?.email &&
                  item.ownerEmail &&
                  currentUser.email.toLowerCase() === item.ownerEmail.toLowerCase();

                return (
                  <tr key={item.id} className={`approval-row status-${item.status?.toLowerCase()}`}>
                    <td>
                      <div className="doc-col-title">
                        <strong
                          className="doc-clickable-title"
                          onClick={() => navigate(`/documentsfetch/${item.documentId}`)}
                          title="View Document Details"
                        >
                          {item.documentTitle}
                        </strong>
                        <span className="category-pill">{item.category}</span>
                      </div>
                    </td>
                    <td>
                      <div className="owner-col-info">
                        <span className="owner-name">{item.ownerName}</span>
                        <span className="owner-email">{item.ownerEmail}</span>
                      </div>
                    </td>
                    <td>
                      <span className="version-pill">v{item.version || 1}</span>
                    </td>
                    <td className="date-col">{formatDate(item.createdAt)}</td>
                    <td>
                      <span className={`status-badge ${(item.status || "PENDING").toLowerCase()}`}>
                        {item.status}
                      </span>
                    </td>
                    <td className="reviewer-comments-col">
                      {item.reviewerName ? (
                        <div className="decided-by">
                          <span>👤 {item.reviewerName}</span>
                          {item.comments && <p className="comment-snippet">"{item.comments}"</p>}
                        </div>
                      ) : (
                        <span className="unassigned-text">Awaiting decision</span>
                      )}
                    </td>
                    <td className="actions-col">
                      <div className="action-buttons-group">
                        <button
                          className="btn-tbl-action preview"
                          onClick={() => handlePreview(item)}
                          title="Preview Document"
                        >
                          👁️
                        </button>
                        <button
                          className="btn-tbl-action download"
                          onClick={() => handleDownload(item)}
                          title="Download File"
                        >
                          ⬇️
                        </button>

                        {item.status === "PENDING" && (
                          <>
                            {isOwnDoc ? (
                              <span
                                className="self-approval-blocked-badge"
                                title="You cannot approve or reject your own document"
                              >
                                Self-Review Blocked
                              </span>
                            ) : (
                              <>
                                <button
                                  className="btn-tbl-action approve"
                                  onClick={() => openDecisionModal(item, "APPROVE")}
                                  title="Approve Document"
                                >
                                  ✅ Approve
                                </button>
                                <button
                                  className="btn-tbl-action reject"
                                  onClick={() => openDecisionModal(item, "REJECT")}
                                  title="Reject Document"
                                >
                                  ❌ Reject
                                </button>
                              </>
                            )}
                          </>
                        )}
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
          <div className="pagination-bar">
            <button
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Previous
            </button>
            <span>
              Page {page + 1} of {totalPages} ({totalElements} total)
            </span>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next →
            </button>
          </div>
        )}
      </div>

      {/* Decision Modal (Approve / Reject) */}
      {decisionModal.isOpen && (
        <div className="modal-backdrop">
          <div className="modal-box decision-modal">
            <div className={`modal-header ${decisionModal.type.toLowerCase()}`}>
              <h2>
                {decisionModal.type === "APPROVE"
                  ? "✅ Approve Document Submission"
                  : "❌ Reject Document Submission"}
              </h2>
              <button className="btn-close-modal" onClick={closeDecisionModal}>
                ×
              </button>
            </div>
            <div className="modal-body">
              <div className="decision-doc-summary">
                <p>
                  <strong>Document:</strong> {decisionModal.approval?.documentTitle}
                </p>
                <p>
                  <strong>Owner:</strong> {decisionModal.approval?.ownerName} (
                  {decisionModal.approval?.ownerEmail})
                </p>
                <p>
                  <strong>Version:</strong> v{decisionModal.approval?.version}
                </p>
              </div>

              {decisionModal.type === "APPROVE" ? (
                <div className="notice-box success">
                  <p>
                    Approving this document will update its status to <strong>APPROVED</strong>,
                    generate a cryptographic <strong>Verification ID</strong>, and create a ZXing
                    <strong> QR code</strong> for public authenticity verification.
                  </p>
                </div>
              ) : (
                <div className="notice-box danger">
                  <p>
                    Rejecting this document will notify the owner. <strong>Comments are strictly required</strong>
                    explaining what must be corrected before resubmission.
                  </p>
                </div>
              )}

              <div className="form-group">
                <label htmlFor="decision-comments">
                  {decisionModal.type === "APPROVE"
                    ? "Approval Comments (Optional):"
                    : "Rejection Reason & Required Changes (Mandatory):"}
                </label>
                <textarea
                  id="decision-comments"
                  rows="4"
                  className="comments-textarea"
                  value={decisionModal.comments}
                  onChange={(e) =>
                    setDecisionModal((prev) => ({ ...prev, comments: e.target.value }))
                  }
                  placeholder={
                    decisionModal.type === "APPROVE"
                      ? "Add optional review notes, compliance remarks, etc..."
                      : "Provide detailed feedback on why this document is rejected..."
                  }
                  required={decisionModal.type === "REJECT"}
                />
                {decisionModal.type === "REJECT" && !decisionModal.comments.trim() && (
                  <span className="field-error-hint">
                    * Rejection reason is required
                  </span>
                )}
              </div>
            </div>
            <div className="modal-footer">
              <button
                type="button"
                className="btn-cancel"
                onClick={closeDecisionModal}
                disabled={decisionModal.submitting}
              >
                Cancel
              </button>
              <button
                type="button"
                className={`btn-confirm ${decisionModal.type.toLowerCase()}`}
                onClick={handleDecisionSubmit}
                disabled={
                  decisionModal.submitting ||
                  (decisionModal.type === "REJECT" && !decisionModal.comments.trim())
                }
              >
                {decisionModal.submitting
                  ? "Processing..."
                  : decisionModal.type === "APPROVE"
                  ? "Confirm Approval"
                  : "Confirm Rejection"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Document Inline Preview Modal */}
      {previewModal.isOpen && (
        <div className="modal-backdrop">
          <div className="modal-box preview-modal">
            <div className="modal-header">
              <h2>👁️ Preview: {previewModal.title}</h2>
              <button className="btn-close-modal" onClick={closePreviewModal}>
                ×
              </button>
            </div>
            <div className="modal-body preview-modal-body">
              {previewModal.loading ? (
                <div className="loading-state">
                  <div className="spinner"></div>
                  <p>Fetching document stream...</p>
                </div>
              ) : previewModal.blobUrl ? (
                previewModal.isImage ? (
                  <img
                    src={previewModal.blobUrl}
                    alt={previewModal.title}
                    className="modal-preview-image"
                  />
                ) : (
                  <iframe
                    src={previewModal.blobUrl}
                    title={previewModal.title}
                    className="modal-preview-iframe"
                  />
                )
              ) : (
                <p>Preview unavailable for this file format.</p>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={closePreviewModal}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
