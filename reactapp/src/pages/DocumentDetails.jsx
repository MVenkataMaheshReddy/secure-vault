import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import {
  fetchDocumentById,
  downloadDocument,
  viewDocument,
  deleteDocument,
  fetchDocumentVersions,
  uploadDocumentVersion,
  downloadDocumentVersion,
  viewDocumentVersion,
  restoreDocumentVersion,
  generateVerification,
  revokeVerification,
  getDocumentQrCodeBlob,
  fetchDocumentApprovals,
  resubmitDocument,
  shareDocument,
  fetchDocumentShares,
  revokeDocumentShare,
} from "../services/api";
import "./DocumentDetails.css";

const ALLOWED_EXTENSIONS = ["pdf", "docx", "png", "jpg", "jpeg"];
const MAX_DOC_SIZE = 10 * 1024 * 1024;
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;

export default function DocumentDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const versionFileInputRef = useRef(null);

  const [doc, setDoc] = useState(null);
  const [versions, setVersions] = useState([]);
  const [activeTab, setActiveTab] = useState("overview"); // "overview" or "versions"
  const [loading, setLoading] = useState(true);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);

  // Preview state (works for current doc or specific version)
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [previewTitle, setPreviewTitle] = useState("");
  const [previewIsImage, setPreviewIsImage] = useState(false);
  const [showPreviewModal, setShowPreviewModal] = useState(false);

  // Delete modal state
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  // Upload New Version Modal state
  const [showUploadVersionModal, setShowUploadVersionModal] = useState(false);
  const [versionFile, setVersionFile] = useState(null);
  const [versionDescription, setVersionDescription] = useState("");
  const [uploadingVersion, setUploadingVersion] = useState(false);
  const [versionProgress, setVersionProgress] = useState(0);
  const [versionError, setVersionError] = useState("");

  // Restore confirmation modal state
  const [restoreConfirmVersion, setRestoreConfirmVersion] = useState(null);
  const [restoringVersion, setRestoringVersion] = useState(false);

  // Current User & Roles
  const reduxUser = useSelector((state) => state.auth?.user);
  let currentUser = reduxUser;
  if (!currentUser) {
    try {
      currentUser = JSON.parse(localStorage.getItem("user") || localStorage.getItem("dl_user") || "null");
    } catch {
      currentUser = null;
    }
  }
  const userRoles = [];
  if (currentUser?.role) userRoles.push(currentUser.role.toUpperCase().replace("ROLE_", ""));
  if (Array.isArray(currentUser?.roles)) {
    currentUser.roles.forEach((r) => userRoles.push(r.toUpperCase().replace("ROLE_", "")));
  }
  const isAdmin = userRoles.includes("ADMIN");

  // QR Code & Verification State (Phase 7)
  const [generatingQr, setGeneratingQr] = useState(false);
  const [revokingQr, setRevokingQr] = useState(false);
  const [showRevokeQrModal, setShowRevokeQrModal] = useState(false);
  const [qrBlobUrl, setQrBlobUrl] = useState(null);
  const [loadingQrBlob, setLoadingQrBlob] = useState(false);

  useEffect(() => {
    let active = true;
    let objectUrl = null;

    if (doc?.id && doc?.verificationId && doc?.status === "APPROVED" && typeof getDocumentQrCodeBlob === "function") {
      setLoadingQrBlob(true);
      getDocumentQrCodeBlob(doc.id)
        .then((res) => {
          if (!active) return;
          objectUrl = window.URL.createObjectURL(new Blob([res.data], { type: "image/png" }));
          setQrBlobUrl(objectUrl);
        })
        .catch((err) => {
          console.error("Failed to load QR code blob:", err);
        })
        .finally(() => {
          if (active) setLoadingQrBlob(false);
        });
    } else {
      setQrBlobUrl(null);
    }

    return () => {
      active = false;
      if (objectUrl) {
        window.URL.revokeObjectURL(objectUrl);
      }
    };
  }, [doc?.id, doc?.verificationId, doc?.status]);

  // Approval Workflow State (Phase 8)
  const [approvals, setApprovals] = useState([]);
  const [loadingApprovals, setLoadingApprovals] = useState(false);
  const [resubmitting, setResubmitting] = useState(false);

  // Sharing State (Phase 9)
  const [showShareModal, setShowShareModal] = useState(false);
  const [shares, setShares] = useState([]);
  const [loadingShares, setLoadingShares] = useState(false);
  const [shareEmail, setShareEmail] = useState("");
  const [sharePermission, setSharePermission] = useState("VIEW");
  const [shareExpiresAt, setShareExpiresAt] = useState("");
  const [submittingShare, setSubmittingShare] = useState(false);
  const [shareError, setShareError] = useState("");

  const loadShares = useCallback(async () => {
    try {
      setLoadingShares(true);
      const res = await fetchDocumentShares(id);
      setShares(res.data || []);
    } catch (err) {
      console.error("Failed to load document shares:", err);
    } finally {
      setLoadingShares(false);
    }
  }, [id]);

  const handleShareSubmit = async (e) => {
    e.preventDefault();
    if (!shareEmail.trim()) {
      setShareError("Recipient email is required.");
      return;
    }
    try {
      setSubmittingShare(true);
      setShareError("");
      const payload = {
        sharedWithEmail: shareEmail.trim(),
        permission: sharePermission,
        expiresAt: shareExpiresAt ? new Date(shareExpiresAt).toISOString() : null,
      };
      await shareDocument(id, payload);
      showToast(`Document shared successfully with ${shareEmail.trim()}!`, "success");
      setShareEmail("");
      setSharePermission("VIEW");
      setShareExpiresAt("");
      await loadShares();
    } catch (err) {
      console.error("Failed to share document:", err);
      setShareError(err.response?.data?.message || "Failed to share document.");
    } finally {
      setSubmittingShare(false);
    }
  };

  const handleRevokeShare = async (shareId, recipientEmail) => {
    try {
      await revokeDocumentShare(id, shareId);
      showToast(`Access revoked for ${recipientEmail || "user"}.`, "success");
      await loadShares();
    } catch (err) {
      console.error("Failed to revoke share:", err);
      showToast(err.response?.data?.message || "Failed to revoke share.", "error");
    }
  };

  const showToast = (message, type = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const handleGenerateVerification = async () => {
    try {
      setGeneratingQr(true);
      await generateVerification(id);
      showToast("Verification certificate & QR code generated successfully!", "success");
      await loadDocument();
    } catch (err) {
      console.error("Failed to generate verification:", err);
      showToast(err.response?.data?.message || "Failed to generate verification.", "error");
    } finally {
      setGeneratingQr(false);
    }
  };

  const handleRevokeVerification = async () => {
    try {
      setRevokingQr(true);
      await revokeVerification(id);
      showToast("Document verification has been REVOKED.", "success");
      setShowRevokeQrModal(false);
      await loadDocument();
    } catch (err) {
      console.error("Failed to revoke verification:", err);
      showToast(err.response?.data?.message || "Failed to revoke verification.", "error");
    } finally {
      setRevokingQr(false);
    }
  };

  const handleDownloadQr = async () => {
    try {
      const res = await getDocumentQrCodeBlob(id);
      const url = window.URL.createObjectURL(new Blob([res.data], { type: "image/png" }));
      const a = document.createElement("a");
      a.href = url;
      a.download = `qr-${doc.verificationId || id}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      showToast("QR code downloaded successfully!", "success");
    } catch (err) {
      console.error("Error downloading QR:", err);
      showToast("Failed to download QR code image.", "error");
    }
  };

  const handlePrintQr = () => {
    const printWindow = window.open("", "_blank", "width=600,height=700");
    if (!printWindow) {
      window.print();
      return;
    }
    const htmlContent = `
      <html>
        <head>
          <title>Document Verification Certificate - ${doc?.title || "Document"}</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; text-align: center; padding: 40px; color: #1e293b; background: #fff; }
            .cert-box { border: 3px double #2563eb; padding: 30px; border-radius: 12px; max-width: 500px; margin: 0 auto; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
            h1 { font-size: 22px; color: #0f172a; margin-bottom: 5px; }
            .subtitle { color: #64748b; font-size: 14px; margin-bottom: 20px; }
            img { width: 220px; height: 220px; margin: 15px auto; display: block; border-radius: 8px; border: 1px solid #cbd5e1; }
            .id-badge { font-family: monospace; font-size: 18px; font-weight: bold; color: #2563eb; margin: 10px 0; }
            .meta { font-size: 13px; color: #475569; margin: 6px 0; }
            .hash { font-family: monospace; font-size: 11px; word-break: break-all; background: #f1f5f9; padding: 8px; border-radius: 4px; margin-top: 8px; }
          </style>
        </head>
        <body>
          <div class="cert-box">
            <h1>🛡️ SecureVault Authenticity Certificate</h1>
            <div class="subtitle">Official Verification Proof</div>
            <img src="${qrBlobUrl || `/api/documents/${doc?.id}/qr`}" alt="QR Code" />
            <div class="id-badge">${doc?.verificationId || "SV-PENDING"}</div>
            <div class="meta"><strong>Document:</strong> ${doc?.title} (v${doc?.currentVersion})</div>
            <div class="meta"><strong>Owner:</strong> ${doc?.ownerEmail || "Verified User"}</div>
            <div class="meta"><strong>Verification URL:</strong> ${window.location.origin}/verify/${doc?.verificationId}</div>
            <div class="meta"><strong>SHA-256 Checksum:</strong></div>
            <div class="hash">${doc?.checksum || "N/A"}</div>
          </div>
          <script>
            window.onload = function() { window.print(); }
          </script>
        </body>
      </html>
    `;
    printWindow.document.write(htmlContent);
    printWindow.document.close();
  };

  const loadDocument = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetchDocumentById(id);
      if (res.data) {
        setDoc(res.data);
      } else {
        setError("Document data not found");
      }
    } catch (err) {
      console.error("Error fetching document:", err);
      const status = err.response?.status;
      const msg =
        status === 403 || status === 404
          ? "Access revoked/expired: You do not have permission to view this document or access has been revoked."
          : err.response?.data?.message || "Failed to load document metadata";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadVersions = useCallback(async () => {
    try {
      setLoadingVersions(true);
      const res = await fetchDocumentVersions(id);
      setVersions(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.error("Error fetching versions:", err);
    } finally {
      setLoadingVersions(false);
    }
  }, [id]);

  const loadApprovals = useCallback(async () => {
    try {
      setLoadingApprovals(true);
      const res = await fetchDocumentApprovals(id);
      setApprovals(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.error("Error fetching approval history:", err);
    } finally {
      setLoadingApprovals(false);
    }
  }, [id]);

  const handleResubmit = async () => {
    try {
      setResubmitting(true);
      await resubmitDocument(id);
      showToast("Document has been resubmitted for approval!", "success");
      await loadDocument();
      await loadApprovals();
    } catch (err) {
      console.error("Failed to resubmit document:", err);
      showToast(err.response?.data?.message || "Failed to resubmit document.", "error");
    } finally {
      setResubmitting(false);
    }
  };

  useEffect(() => {
    loadDocument();
    loadVersions();
    loadApprovals();
  }, [loadDocument, loadVersions, loadApprovals]);

  useEffect(() => {
    return () => {
      if (previewBlobUrl) {
        window.URL.revokeObjectURL(previewBlobUrl);
      }
    };
  }, [previewBlobUrl]);

  // Download Current Document
  const handleDownloadCurrent = async () => {
    try {
      const res = await downloadDocument(id);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute(
        "download",
        doc.originalFileName || doc.doc_name || "document"
      );
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showToast("Download started");
    } catch (err) {
      console.error("Download failed:", err);
      const status = err.response?.status;
      if (status === 403 || status === 404) {
        showToast("Access revoked/expired: You do not have permission to download this document.", "error");
      } else {
        showToast("Download failed or access denied", "error");
      }
    }
  };

  // View Current Document Inline
  const handleViewCurrentInline = async () => {
    try {
      setLoadingPreview(true);
      setPreviewTitle(doc.title || doc.originalFileName);
      setPreviewIsImage(
        doc.contentType?.startsWith("image") ||
        doc.originalFileName?.match(/\.(png|jpg|jpeg)$/i)
      );
      setShowPreviewModal(true);
      if (previewBlobUrl) {
        window.URL.revokeObjectURL(previewBlobUrl);
        setPreviewBlobUrl(null);
      }

      const res = await viewDocument(id);
      const mime = res.headers["content-type"] || doc.contentType || "application/pdf";
      const blob = new Blob([res.data], { type: mime });
      const url = window.URL.createObjectURL(blob);
      setPreviewBlobUrl(url);
    } catch (err) {
      console.error("Preview failed:", err);
      const status = err.response?.status;
      if (status === 403 || status === 404) {
        showToast("Access revoked/expired: You do not have permission to view this document.", "error");
      } else {
        showToast("Unable to load document preview", "error");
      }
    } finally {
      setLoadingPreview(false);
    }
  };

  // View Specific Version Inline
  const handleViewVersion = async (ver) => {
    try {
      setLoadingPreview(true);
      setPreviewTitle(`v${ver.versionNumber} - ${doc.title || doc.originalFileName}`);
      setPreviewIsImage(
        ver.contentType?.startsWith("image") ||
        ver.filePath?.match(/\.(png|jpg|jpeg)$/i)
      );
      setShowPreviewModal(true);
      if (previewBlobUrl) {
        window.URL.revokeObjectURL(previewBlobUrl);
        setPreviewBlobUrl(null);
      }

      const res = await viewDocumentVersion(id, ver.id);
      const mime = res.headers["content-type"] || ver.contentType || "application/pdf";
      const blob = new Blob([res.data], { type: mime });
      const url = window.URL.createObjectURL(blob);
      setPreviewBlobUrl(url);
    } catch (err) {
      console.error("Version preview failed:", err);
      showToast("Unable to load version preview", "error");
    } finally {
      setLoadingPreview(false);
    }
  };

  // Download Specific Version
  const handleDownloadVersion = async (ver) => {
    try {
      const res = await downloadDocumentVersion(id, ver.id);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      const ext = ver.contentType?.includes("pdf")
        ? "pdf"
        : ver.contentType?.includes("docx")
        ? "docx"
        : ver.contentType?.split("/")[1] || "bin";
      link.setAttribute("download", `v${ver.versionNumber}_${doc.originalFileName || "document." + ext}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showToast(`v${ver.versionNumber} download started`);
    } catch (err) {
      console.error("Version download failed:", err);
      showToast("Failed to download version", "error");
    }
  };

  const closePreview = () => {
    setShowPreviewModal(false);
    if (previewBlobUrl) {
      window.URL.revokeObjectURL(previewBlobUrl);
      setPreviewBlobUrl(null);
    }
  };

  // Delete Document
  const handleDelete = async () => {
    try {
      await deleteDocument(id);
      showToast("Document moved to Trash successfully");
      setShowDeleteModal(false);
      setTimeout(() => navigate("/documents"), 1000);
    } catch (err) {
      console.error("Delete failed:", err);
      showToast(err.response?.data?.message || "Failed to delete document", "error");
    }
  };

  // Upload New Version Handler
  const handleVersionFileChange = (e) => {
    const file = e.target.files && e.target.files[0];
    setVersionError("");
    if (!file) return;

    if (file.size === 0) {
      setVersionError("Selected file is empty.");
      setVersionFile(null);
      return;
    }

    const ext = file.name.split(".").pop().toLowerCase();
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      setVersionError(
        `Invalid file type ".${ext}". Allowed: ${ALLOWED_EXTENSIONS.join(", ").toUpperCase()}`
      );
      setVersionFile(null);
      return;
    }

    if (["pdf", "docx"].includes(ext) && file.size > MAX_DOC_SIZE) {
      setVersionError(`Document exceeds 10 MB limit.`);
      setVersionFile(null);
      return;
    }

    if (!["pdf", "docx"].includes(ext) && file.size > MAX_IMAGE_SIZE) {
      setVersionError(`Image exceeds 5 MB limit.`);
      setVersionFile(null);
      return;
    }

    setVersionFile(file);
  };

  const handleUploadNewVersionSubmit = async () => {
    if (!versionFile) {
      setVersionError("Please select a file to upload.");
      return;
    }

    try {
      setUploadingVersion(true);
      setVersionProgress(0);
      setVersionError("");

      const formData = new FormData();
      formData.append("file", versionFile);
      if (versionDescription.trim()) {
        formData.append("changeDescription", versionDescription.trim());
      }

      await uploadDocumentVersion(id, formData, {
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setVersionProgress(percent);
          }
        },
      });

      showToast("New version uploaded successfully!");
      setShowUploadVersionModal(false);
      setVersionFile(null);
      setVersionDescription("");
      // Reload document and versions
      loadDocument();
      loadVersions();
      setActiveTab("versions");
    } catch (err) {
      console.error("Version upload error:", err);
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Failed to upload new version.";
      setVersionError(msg);
    } finally {
      setUploadingVersion(false);
    }
  };

  // Restore Old Version
  const handleConfirmRestore = async () => {
    if (!restoreConfirmVersion) return;
    try {
      setRestoringVersion(true);
      await restoreDocumentVersion(id, restoreConfirmVersion.id);
      showToast(
        `Restored from v${restoreConfirmVersion.versionNumber}! Created version v${(doc.currentVersion || 1) + 1}.`
      );
      setRestoreConfirmVersion(null);
      loadDocument();
      loadVersions();
    } catch (err) {
      console.error("Restore version failed:", err);
      showToast(err.response?.data?.message || "Failed to restore version", "error");
    } finally {
      setRestoringVersion(false);
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

  if (loading) {
    return (
      <div className="doc-details-container">
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Decrypting & loading document details...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="doc-details-container">
        <div className="error-card">
          <span className="error-icon">🚫</span>
          <h2>Unable to Display Document</h2>
          <p className="error-text">{error}</p>
          <button className="btn-back" onClick={() => navigate("/documents")}>
            ← Back to Documents
          </button>
        </div>
      </div>
    );
  }

  if (!doc) return null;

  const docTitle = doc.title || doc.doc_name || doc.originalFileName;
  const isImageOrPdf =
    doc.contentType === "application/pdf" ||
    doc.contentType?.startsWith("image/") ||
    doc.originalFileName?.endsWith(".pdf") ||
    doc.originalFileName?.match(/\.(png|jpg|jpeg)$/i);

  return (
    <div className="doc-details-container">
      {/* Toast Notification */}
      {toast && (
        <div className={`toast-notification ${toast.type}`}>
          <span>{toast.type === "success" ? "✅" : "⚠️"}</span>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Navigation & Header */}
      <div className="details-header-bar">
        <button className="btn-back" onClick={() => navigate("/documents")}>
          ← Back to Documents
        </button>
        <div className="header-badges">
          <span className={`status-badge ${(doc.status || "ACTIVE").toLowerCase()}`}>
            {doc.status || "ACTIVE"}
          </span>
          <span className="category-badge">{doc.category || "GENERAL"}</span>
          <span className="version-badge">Version v{doc.currentVersion || 1}</span>
        </div>
      </div>

      {/* Main Details Card */}
      <div className="doc-main-card">
        {/* Phase 8: Rejection Alert Banner */}
        {doc.status === "REJECTED" && (
          <div className="rejection-alert-banner">
            <div className="rejection-alert-content">
              <span className="rejection-icon">⚠️</span>
              <div>
                <h3>Document Submission Rejected</h3>
                <p className="rejection-comment">
                  <strong>Reviewer Feedback:</strong>{" "}
                  {approvals.find((a) => a.status === "REJECTED")?.comments ||
                    "This document was rejected by the reviewer. Please review requirements and resubmit."}
                </p>
              </div>
            </div>
            {(isAdmin || (currentUser?.email && doc.ownerEmail && currentUser.email.toLowerCase() === doc.ownerEmail.toLowerCase())) && (
              <button
                className="btn-resubmit"
                onClick={handleResubmit}
                disabled={resubmitting}
              >
                {resubmitting ? "Resubmitting..." : "🔄 Resubmit for Approval"}
              </button>
            )}
          </div>
        )}

        <div className="card-top-section">
          <div className="title-area">
            <h1>📄 {docTitle}</h1>
            <p className="original-filename">
              Original File: <strong>{doc.originalFileName || "N/A"}</strong>
            </p>
          </div>

          <div className="actions-button-group">
            {(isAdmin || !currentUser || (currentUser?.email && doc.ownerEmail && currentUser.email.toLowerCase() === doc.ownerEmail.toLowerCase())) && (
              <button
                className="btn-action upload-version"
                onClick={() => {
                  setVersionError("");
                  setShowUploadVersionModal(true);
                }}
              >
                + Upload New Version
              </button>
            )}
            {isImageOrPdf && (
              <button className="btn-action view" onClick={handleViewCurrentInline}>
                👁️ View Inline
              </button>
            )}
            <button className="btn-action download" onClick={handleDownloadCurrent}>
              ⬇️ Download Current
            </button>
            {(isAdmin || (currentUser?.email && doc.ownerEmail && currentUser.email.toLowerCase() === doc.ownerEmail.toLowerCase())) && (
              <button
                className="btn-action share"
                onClick={() => {
                  setShowShareModal(true);
                  loadShares();
                }}
                title="Share Document"
              >
                🔗 Share Document
              </button>
            )}
            {(isAdmin || (currentUser?.email && doc.ownerEmail && currentUser.email.toLowerCase() === doc.ownerEmail.toLowerCase())) && (
              <button
                className="btn-action delete"
                onClick={() => setShowDeleteModal(true)}
              >
                🗑️ Move to Trash
              </button>
            )}
          </div>
        </div>

        {/* Tabs Bar */}
        <div className="details-tabs-bar">
          <button
            className={`details-tab-btn ${activeTab === "overview" ? "active" : ""}`}
            onClick={() => setActiveTab("overview")}
          >
            ℹ️ Overview & Metadata
          </button>
          <button
            className={`details-tab-btn ${activeTab === "versions" ? "active" : ""}`}
            onClick={() => setActiveTab("versions")}
          >
            🕒 Version History ({versions.length})
          </button>
          <button
            className={`details-tab-btn ${activeTab === "approvals" ? "active" : ""}`}
            onClick={() => setActiveTab("approvals")}
          >
            🛡️ Approval History ({approvals.length})
          </button>
        </div>

        {/* TAB 1: OVERVIEW & METADATA */}
        {activeTab === "overview" && (
          <div className="tab-pane">
            <div className="metadata-grid">
              <div className="meta-item">
                <span className="meta-label">Current Version</span>
                <span className="meta-value">v{doc.currentVersion || 1}</span>
              </div>

              <div className="meta-item">
                <span className="meta-label">File Size</span>
                <span className="meta-value">{formatFileSize(doc.fileSize)}</span>
              </div>

              <div className="meta-item">
                <span className="meta-label">Content Type (MIME)</span>
                <span className="meta-value">{doc.contentType || "application/octet-stream"}</span>
              </div>

              <div className="meta-item">
                <span className="meta-label">Owner</span>
                <span className="meta-value">
                  {doc.ownerEmail || (doc.userId ? `User #${doc.userId}` : "Locker Owner")}
                </span>
              </div>

              <div className="meta-item">
                <span className="meta-label">Created Date</span>
                <span className="meta-value">{formatDate(doc.createdAt)}</span>
              </div>

              <div className="meta-item">
                <span className="meta-label">Last Modified</span>
                <span className="meta-value">{formatDate(doc.updatedAt)}</span>
              </div>
            </div>

            {/* Description Section */}
            {doc.description && (
              <div className="section-block">
                <h3>Description / Notes</h3>
                <p className="description-text">{doc.description}</p>
              </div>
            )}

            {/* Tags Section */}
            {doc.tags && doc.tags.size > 0 && (
              <div className="section-block">
                <h3>Document Tags</h3>
                <div className="tags-container">
                  {Array.from(doc.tags).map((tag) => (
                    <span key={tag} className="tag-pill">
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* QR-Code Document Verification Block */}
            <div className="section-block qr-verification-block" data-testid="qr-verification-section">
              <div className="qr-section-header">
                <h3>🛡️ QR-Code Document Verification</h3>
                {doc.verificationId && (
                  <span className={`status-pill ${doc.verificationStatus === "REVOKED" ? "pill-revoked" : "pill-valid"}`}>
                    {doc.verificationStatus === "REVOKED" ? "REVOKED" : "VERIFIED & CERTIFIED"}
                  </span>
                )}
              </div>

              {doc.status === "PENDING" ? (
                <div className="qr-unverified-state">
                  <p className="unverified-text" style={{ fontWeight: 600, color: "#d97706" }}>
                    ⏳ Verification will be generated after approval
                  </p>
                </div>
              ) : doc.verificationId ? (
                <div className="qr-details-layout">
                  <div className="qr-image-wrapper">
                    {loadingQrBlob ? (
                      <div className="qr-loading-spinner" style={{ padding: "40px", textAlign: "center" }}>
                        Loading QR Code...
                      </div>
                    ) : (
                      <img
                        src={qrBlobUrl || `/api/documents/${doc.id}/qr`}
                        alt={`Verification QR Code for ${doc.title}`}
                        className="qr-image"
                        data-testid="doc-qr-image"
                      />
                    )}
                    <div className="qr-actions-row">
                      <button
                        onClick={handleDownloadQr}
                        className="btn-qr-action"
                        title="Download QR Code PNG"
                      >
                        ⬇ Download QR
                      </button>
                      <button
                        onClick={handlePrintQr}
                        className="btn-qr-action"
                        title="Print Verification Certificate"
                      >
                        🖨️ Print
                      </button>
                    </div>
                  </div>

                  <div className="qr-meta-info">
                    <div className="qr-meta-row">
                      <span className="qr-meta-label">Verification ID:</span>
                      <code className="qr-id-code">{doc.verificationId}</code>
                    </div>

                    {/* Admin Revocation action */}
                    {isAdmin && doc.verificationStatus !== "REVOKED" && (
                      <div className="qr-admin-actions">
                        <button
                          onClick={() => setShowRevokeQrModal(true)}
                          className="btn-revoke-qr"
                        >
                          ⚠️ Revoke Document Verification
                        </button>
                      </div>
                    )}

                    {doc.verificationStatus === "REVOKED" && (
                      <div className="qr-revoked-warning">
                        ⚠️ <strong>Certificate Revoked:</strong> This document's verification certificate has been officially revoked. Public scans will flag it as INVALID / REVOKED.
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                <div className="qr-unverified-state">
                  <p className="unverified-text">
                    This document does not yet have an official verification certificate or QR code generated.
                    Verification is issued for approved documents.
                  </p>
                  {isAdmin && (
                    <button
                      onClick={handleGenerateVerification}
                      disabled={generatingQr}
                      className="btn-generate-qr"
                    >
                      {generatingQr ? "Generating QR..." : "✨ Generate Verification & QR Code"}
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 2: VERSION HISTORY */}
        {activeTab === "versions" && (
          <div className="tab-pane">
            <div className="versions-header-row">
              <h3>Version Timeline</h3>
              <p className="versions-subtext">
                Every uploaded version is immutable. Restoring an earlier version creates a new version, preserving full historical auditability.
              </p>
            </div>

            {loadingVersions ? (
              <div className="loading-state">
                <div className="spinner"></div>
                <p>Loading version history...</p>
              </div>
            ) : versions.length === 0 ? (
              <p className="no-versions-msg">No versions recorded yet.</p>
            ) : (
              <div className="table-responsive">
                <table className="versions-table">
                  <thead>
                    <tr>
                      <th className="version-th">Version</th>
                      <th className="uploader-th">Uploaded By</th>
                      <th className="date-th">Date & Time</th>
                      <th className="note-th">Note / Description</th>
                      <th className="size-th">Size</th>
                      <th className="actions-header">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {versions.map((ver) => {
                      const isCurrent = ver.isCurrent || ver.versionNumber === doc.currentVersion;
                      return (
                        <tr
                          key={ver.id}
                          className={`version-row ${isCurrent ? "current-version-row" : ""}`}
                        >
                          <td className="version-col">
                            <div className="version-cell-badge">
                              <span className="version-tag">v{ver.versionNumber}</span>
                              {isCurrent && <span className="current-badge">Current</span>}
                            </div>
                          </td>
                          <td className="uploader-col">
                            <span className="uploader-name">
                              {ver.uploadedByName || ver.uploadedByEmail || "User"}
                            </span>
                          </td>
                          <td className="date-cell">{formatDate(ver.createdAt)}</td>
                          <td className="note-cell">
                            {ver.changeDescription || `Version ${ver.versionNumber}`}
                          </td>
                          <td className="size-cell">{formatFileSize(ver.fileSize)}</td>
                          <td className="version-actions-cell">
                            <button
                              className="action-btn view-btn"
                              title="View this version inline"
                              onClick={() => handleViewVersion(ver)}
                            >
                              👁️ View
                            </button>
                            <button
                              className="action-btn download-btn"
                              title="Download this version"
                              onClick={() => handleDownloadVersion(ver)}
                            >
                              ⬇️ Download
                            </button>
                            {!isCurrent && (
                              <button
                                className="action-btn restore-btn"
                                title="Restore as new version"
                                onClick={() => setRestoreConfirmVersion(ver)}
                              >
                                ♻️ Restore
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* TAB 3: APPROVAL HISTORY (PHASE 8) */}
        {activeTab === "approvals" && (
          <div className="tab-pane">
            <div className="tab-pane-header">
              <div>
                <h3>🛡️ Document Approval History</h3>
                <p className="tab-subtitle">
                  Audit trail of review decisions and verification issuance.
                </p>
              </div>
              {doc.status === "REJECTED" &&
                (isAdmin ||
                  (currentUser?.email &&
                    doc.ownerEmail &&
                    currentUser.email.toLowerCase() === doc.ownerEmail.toLowerCase())) && (
                  <button
                    className="btn-resubmit-header"
                    onClick={handleResubmit}
                    disabled={resubmitting}
                  >
                    {resubmitting ? "Resubmitting..." : "🔄 Resubmit for Approval"}
                  </button>
                )}
            </div>

            {loadingApprovals ? (
              <div className="loading-state">
                <div className="spinner"></div>
                <p>Loading approval history...</p>
              </div>
            ) : approvals.length === 0 ? (
              <div className="empty-state">
                <span className="empty-icon">🛡️</span>
                <h3>No approval records</h3>
                <p>No approval actions have been recorded yet for this document.</p>
              </div>
            ) : (
              <div className="approval-history-timeline">
                {approvals.map((item, idx) => (
                  <div
                    key={item.id || idx}
                    className={`timeline-item status-${(item.status || "PENDING").toLowerCase()}`}
                  >
                    <div className="timeline-node">
                      <span className="node-icon">
                        {item.status === "APPROVED"
                          ? "✅"
                          : item.status === "REJECTED"
                          ? "❌"
                          : "⏳"}
                      </span>
                    </div>
                    <div className="timeline-card">
                      <div className="timeline-card-header">
                        <div className="timeline-status-row">
                          <span
                            className={`status-badge ${(item.status || "PENDING").toLowerCase()}`}
                          >
                            {item.status}
                          </span>
                          <span className="timeline-version-tag">Version v{item.version || 1}</span>
                        </div>
                        <span className="timeline-date">{formatDate(item.createdAt)}</span>
                      </div>
                      <div className="timeline-card-body">
                        <p className="timeline-reviewer">
                          <strong>Decision Maker:</strong>{" "}
                          {item.reviewerName
                            ? `${item.reviewerName} (${item.reviewerEmail || "Reviewer"})`
                            : "Pending reviewer action"}
                        </p>
                        {item.comments && (
                          <div className="timeline-comments-box">
                            <span className="comments-heading">Reviewer Comments / Reason:</span>
                            <p className="comments-text">{item.comments}</p>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* UPLOAD NEW VERSION MODAL */}
      {showUploadVersionModal && (
        <div
          className="modal-backdrop"
          onClick={() => !uploadingVersion && setShowUploadVersionModal(false)}
        >
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>📦 Upload New Version (v{(doc.currentVersion || 1) + 1})</h3>
              <button
                className="modal-close-btn"
                disabled={uploadingVersion}
                onClick={() => setShowUploadVersionModal(false)}
              >
                ✕
              </button>
            </div>
            <div className="modal-body">
              {versionError && (
                <div className="modal-alert-error">
                  <span>⚠️</span>
                  <span>{versionError}</span>
                </div>
              )}

              {/* File Dropzone */}
              <div
                className={`version-dropzone ${versionFile ? "has-file" : ""}`}
                onClick={() => versionFileInputRef.current && versionFileInputRef.current.click()}
              >
                <input
                  type="file"
                  ref={versionFileInputRef}
                  style={{ display: "none" }}
                  onChange={handleVersionFileChange}
                  accept=".pdf,.docx,.png,.jpg,.jpeg"
                />
                {versionFile ? (
                  <div className="version-file-selected">
                    <span className="file-icon">📄</span>
                    <div>
                      <strong>{versionFile.name}</strong>
                      <span className="file-size-sub">
                        ({(versionFile.size / 1024).toFixed(1)} KB)
                      </span>
                    </div>
                  </div>
                ) : (
                  <div className="version-dropzone-prompt">
                    <span className="upload-icon">☁️</span>
                    <p>Click to browse or drop replacement file</p>
                    <small>Formats: PDF, DOCX (≤10MB) | PNG, JPG, JPEG (≤5MB)</small>
                  </div>
                )}
              </div>

              <div className="form-group" style={{ marginTop: "16px" }}>
                <label>Change Description / Release Note</label>
                <textarea
                  rows="3"
                  value={versionDescription}
                  onChange={(e) => setVersionDescription(e.target.value)}
                  placeholder="e.g. Updated tax figures, corrected typographical error in section 3..."
                />
              </div>

              {uploadingVersion && (
                <div className="progress-container">
                  <div className="progress-labels">
                    <span>Uploading & Verifying Checksum...</span>
                    <span>{versionProgress}%</span>
                  </div>
                  <div className="progress-bar-track">
                    <div
                      className="progress-bar-fill"
                      style={{ width: `${versionProgress}%` }}
                    />
                  </div>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button
                className="btn-cancel"
                disabled={uploadingVersion}
                onClick={() => setShowUploadVersionModal(false)}
              >
                Cancel
              </button>
              <button
                className="btn-save"
                disabled={uploadingVersion || !versionFile}
                onClick={handleUploadNewVersionSubmit}
              >
                {uploadingVersion ? `Uploading (${versionProgress}%)...` : "Upload Version"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* RESTORE VERSION CONFIRMATION MODAL */}
      {restoreConfirmVersion && (
        <div
          className="modal-backdrop"
          onClick={() => !restoringVersion && setRestoreConfirmVersion(null)}
        >
          <div className="modal-content confirm-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>♻️ Restore Version v{restoreConfirmVersion.versionNumber}?</h3>
              <button
                className="modal-close-btn"
                disabled={restoringVersion}
                onClick={() => setRestoreConfirmVersion(null)}
              >
                ✕
              </button>
            </div>
            <div className="modal-body">
              <p>
                You are about to restore <strong>Version v{restoreConfirmVersion.versionNumber}</strong>.
              </p>
              <p className="warning-text" style={{ background: "#ecfdf5", color: "#065f46" }}>
                ✨ <strong>Historical Integrity Preserved:</strong> Restoring creates a brand new version <strong>v{(doc.currentVersion || 1) + 1}</strong> copying this version's exact contents. Previous versions will never be deleted or overwritten.
              </p>
            </div>
            <div className="modal-footer">
              <button
                className="btn-cancel"
                disabled={restoringVersion}
                onClick={() => setRestoreConfirmVersion(null)}
              >
                Cancel
              </button>
              <button
                className="btn-save"
                disabled={restoringVersion}
                onClick={handleConfirmRestore}
              >
                {restoringVersion ? "Restoring..." : `Restore as v${(doc.currentVersion || 1) + 1}`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* PREVIEW MODAL */}
      {showPreviewModal && (
        <div className="modal-backdrop" onClick={closePreview}>
          <div className="modal-content preview-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>👁️ Preview: {previewTitle}</h3>
              <button className="modal-close-btn" onClick={closePreview}>
                ✕
              </button>
            </div>
            <div className="preview-body">
              {loadingPreview ? (
                <div className="loading-state">
                  <div className="spinner"></div>
                  <p>Streaming document...</p>
                </div>
              ) : previewBlobUrl ? (
                previewIsImage ? (
                  <img
                    src={previewBlobUrl}
                    alt={previewTitle}
                    className="image-preview"
                  />
                ) : (
                  <iframe
                    src={previewBlobUrl}
                    title={previewTitle}
                    className="iframe-preview"
                  />
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

      {/* DELETE CONFIRMATION MODAL */}
      {showDeleteModal && (
        <div className="modal-backdrop" onClick={() => setShowDeleteModal(false)}>
          <div className="modal-content confirm-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>🗑️ Move Document to Trash?</h3>
              <button className="modal-close-btn" onClick={() => setShowDeleteModal(false)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <p>
                Are you sure you want to delete <strong>"{docTitle}"</strong>?
              </p>
              <p className="warning-text">
                This document will be soft-deleted. The physical files and full version history remain securely preserved on disk and can be restored anytime from your <strong>Trash Bin</strong>.
              </p>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={() => setShowDeleteModal(false)}>
                Cancel
              </button>
              <button className="btn-danger-confirm" onClick={handleDelete}>
                Move to Trash
              </button>
            </div>
          </div>
        </div>
      )}

      {/* REVOKE QR VERIFICATION MODAL */}
      {showRevokeQrModal && (
        <div className="modal-backdrop" onClick={() => setShowRevokeQrModal(false)}>
          <div className="modal-content confirm-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>⚠️ Revoke Verification Certificate?</h3>
              <button className="modal-close-btn" onClick={() => setShowRevokeQrModal(false)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <p>
                Are you sure you want to revoke the verification for <strong>"{doc?.title}"</strong>?
              </p>
              <div style={{ background: "#f8fafc", padding: "10px", borderRadius: "6px", margin: "10px 0", borderLeft: "3px solid #dc2626" }}>
                <div>Verification ID: <code>{doc?.verificationId}</code></div>
              </div>
              <p className="warning-text">
                This will immediately invalidate the verification certificate. Scanning the QR code or visiting the public link will display that this document is <strong>REVOKED</strong>.
              </p>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={() => setShowRevokeQrModal(false)} disabled={revokingQr}>
                Cancel
              </button>
              <button className="btn-danger-confirm" onClick={handleRevokeVerification} disabled={revokingQr}>
                {revokingQr ? "Revoking..." : "Revoke Verification"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* SHARE DOCUMENT MODAL (PHASE 9) */}
      {showShareModal && (
        <div className="modal-backdrop" onClick={() => setShowShareModal(false)}>
          <div className="modal-content share-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>🔗 Share Document — {docTitle}</h3>
              <button className="modal-close-btn" onClick={() => setShowShareModal(false)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <form onSubmit={handleShareSubmit} className="share-form">
                {shareError && <div className="share-error-alert">⚠️ {shareError}</div>}
                <div className="share-form-row">
                  <div className="share-form-group flex-2">
                    <label htmlFor="share-email-input">Recipient Email *</label>
                    <input
                      id="share-email-input"
                      type="email"
                      value={shareEmail}
                      onChange={(e) => setShareEmail(e.target.value)}
                      placeholder="e.g. colleague@securevault.com"
                      required
                    />
                  </div>
                  <div className="share-form-group flex-1">
                    <label htmlFor="share-perm-select">Permission *</label>
                    <select
                      id="share-perm-select"
                      value={sharePermission}
                      onChange={(e) => setSharePermission(e.target.value)}
                    >
                      <option value="VIEW">VIEW (Read-only)</option>
                      <option value="DOWNLOAD">DOWNLOAD (Full Access)</option>
                    </select>
                  </div>
                </div>

                <div className="share-form-group">
                  <label htmlFor="share-expiry-input">Expiration (Optional)</label>
                  <input
                    id="share-expiry-input"
                    type="datetime-local"
                    value={shareExpiresAt}
                    onChange={(e) => setShareExpiresAt(e.target.value)}
                  />
                  <small className="help-text">Leave blank for indefinite access until manually revoked.</small>
                </div>

                <div className="share-form-actions">
                  <button type="submit" className="btn-share-submit" disabled={submittingShare}>
                    {submittingShare ? "Granting Access..." : "➕ Grant Access"}
                  </button>
                </div>
              </form>

              <hr className="share-divider" />

              <div className="existing-shares-section">
                <h4>👥 Current Access List ({shares.length})</h4>
                {loadingShares ? (
                  <p className="loading-shares-text">Loading access records...</p>
                ) : shares.length === 0 ? (
                  <p className="no-shares-text">This document has not been shared with any individual users yet.</p>
                ) : (
                  <div className="shares-list-table-wrapper">
                    <table className="shares-list-table">
                      <thead>
                        <tr>
                          <th>User</th>
                          <th>Permission</th>
                          <th>Expires</th>
                          <th>Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {shares.map((s) => (
                          <tr key={s.id} className={s.expired ? "expired-row" : ""}>
                            <td>
                              <strong>{s.sharedWithName || s.sharedWithEmail}</strong>
                              <span className="share-email-sub">{s.sharedWithEmail}</span>
                            </td>
                            <td>
                              <span className={`perm-badge ${s.permission?.toLowerCase()}`}>
                                {s.permission}
                              </span>
                            </td>
                            <td>
                              {s.expired ? (
                                <span className="status-tag expired">Expired</span>
                              ) : s.expiresAt ? (
                                new Date(s.expiresAt).toLocaleDateString()
                              ) : (
                                "Never"
                              )}
                            </td>
                            <td>
                              <button
                                className="btn-revoke-share"
                                onClick={() => handleRevokeShare(s.id, s.sharedWithEmail)}
                                title="Revoke access"
                              >
                                ✕ Revoke
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={() => setShowShareModal(false)}>
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
