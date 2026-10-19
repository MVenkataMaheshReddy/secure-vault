import React, { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  fetchFolders,
  fetchDocumentsPaginated,
  fetchDocumentsMetadata,
  downloadDocument,
  viewDocument,
  updateDocument,
  deleteDocument,
  resubmitDocument,
} from "../services/api";
import "./Documents.css";

export default function Documents() {
  const navigate = useNavigate();

  // Tab: "documents" or "folders"
  const [activeTab, setActiveTab] = useState("documents");

  // Document state
  const [docs, setDocs] = useState([]);
  const [folders, setFolders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);

  // Pagination state
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Filters state
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [status, setStatus] = useState("");
  const [sort, setSort] = useState("createdAt,desc");

  // Modals state
  const [openFolder, setOpenFolder] = useState(null);
  const [deleteModalDoc, setDeleteModalDoc] = useState(null);
  const [editModalDoc, setEditModalDoc] = useState(null);
  const [previewDoc, setPreviewDoc] = useState(null);
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);

  // Edit form state
  const [editTitle, setEditTitle] = useState("");
  const [editCategory, setEditCategory] = useState("GENERAL");
  const [editDescription, setEditDescription] = useState("");
  const [editTags, setEditTags] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);

  const showToast = (message, type = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const loadDocuments = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const params = {
        page,
        size,
        sort,
      };
      if (category) params.category = category;
      if (status) params.status = status;
      if (search.trim()) params.search = search.trim();

      const res = await fetchDocumentsPaginated(params);
      if (res.data && res.data.content) {
        setDocs(res.data.content);
        setTotalPages(res.data.totalPages || 1);
        setTotalElements(res.data.totalElements || 0);
      } else if (Array.isArray(res.data)) {
        setDocs(res.data);
        setTotalPages(1);
        setTotalElements(res.data.length);
      } else {
        setDocs([]);
      }
    } catch (err) {
      console.error("Error loading documents:", err);
      setError("Failed to load documents.");
      // Fallback for legacy
      try {
        const legacyRes = await fetchDocumentsMetadata();
        setDocs(Array.isArray(legacyRes.data) ? legacyRes.data : []);
      } catch (e) {
        // ignore
      }
    } finally {
      setLoading(false);
    }
  }, [page, size, sort, category, status, search]);

  const loadFoldersData = async () => {
    try {
      const folderRes = await fetchFolders();
      setFolders(Array.isArray(folderRes.data) ? folderRes.data : []);
    } catch (err) {
      console.error("Error loading folders:", err);
    }
  };

  useEffect(() => {
    loadDocuments();
    loadFoldersData();
  }, [loadDocuments]);

  // Clean up preview blob URL on unmount or close
  useEffect(() => {
    return () => {
      if (previewBlobUrl) {
        window.URL.revokeObjectURL(previewBlobUrl);
      }
    };
  }, [previewBlobUrl]);

  // Handle Download
  const handleDownload = async (doc) => {
    try {
      const res = await downloadDocument(doc.id || doc.doc_id);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", doc.originalFileName || doc.doc_name || "document");
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

  // Handle View Inline
  const handleViewInline = async (doc) => {
    try {
      setLoadingPreview(true);
      setPreviewDoc(doc);
      if (previewBlobUrl) {
        window.URL.revokeObjectURL(previewBlobUrl);
        setPreviewBlobUrl(null);
      }

      const res = await viewDocument(doc.id || doc.doc_id);
      const mime = res.headers["content-type"] || doc.contentType || "application/pdf";
      const blob = new Blob([res.data], { type: mime });
      const url = window.URL.createObjectURL(blob);
      setPreviewBlobUrl(url);
    } catch (err) {
      console.error("View failed:", err);
      showToast("Unable to load document preview", "error");
      setPreviewDoc(null);
    } finally {
      setLoadingPreview(false);
    }
  };

  const closePreview = () => {
    if (previewBlobUrl) {
      window.URL.revokeObjectURL(previewBlobUrl);
      setPreviewBlobUrl(null);
    }
    setPreviewDoc(null);
  };

  // Handle Edit Open
  const openEditModal = (doc) => {
    setEditModalDoc(doc);
    setEditTitle(doc.title || doc.doc_name || "");
    setEditCategory(doc.category || "GENERAL");
    setEditDescription(doc.description || "");
    setEditTags(doc.tags ? Array.from(doc.tags).join(", ") : "");
  };

  // Handle Edit Save
  const handleSaveEdit = async () => {
    if (!editModalDoc) return;
    try {
      setSavingEdit(true);
      const tagsArray = editTags
        .split(",")
        .map((t) => t.trim())
        .filter((t) => t.length > 0);

      await updateDocument(editModalDoc.id || editModalDoc.doc_id, {
        title: editTitle.trim(),
        category: editCategory,
        description: editDescription.trim(),
        tags: tagsArray,
      });

      showToast("Document details updated successfully");
      setEditModalDoc(null);
      loadDocuments();
    } catch (err) {
      console.error("Update failed:", err);
      showToast(err.response?.data?.message || "Failed to update document", "error");
    } finally {
      setSavingEdit(false);
    }
  };

  // Handle Delete Confirmation
  const confirmDelete = async () => {
    if (!deleteModalDoc) return;
    try {
      await deleteDocument(deleteModalDoc.id || deleteModalDoc.doc_id);
      showToast(`"${deleteModalDoc.title || deleteModalDoc.originalFileName}" moved to Trash`);
      setDeleteModalDoc(null);
      loadDocuments();
    } catch (err) {
      console.error("Delete failed:", err);
      showToast(err.response?.data?.message || "Failed to delete document", "error");
    }
  };

  const handleResubmit = async (doc) => {
    const docId = doc.id || doc.doc_id;
    try {
      await resubmitDocument(docId);
      showToast(`Document "${doc.title || doc.originalFileName}" resubmitted for approval!`);
      loadDocuments();
    } catch (err) {
      showToast(err.response?.data?.message || "Failed to resubmit document", "error");
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
    return new Date(isoString).toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  };

  // Get documents by folderId for folder view
  const getDocsByFolder = (folderId) =>
    docs.filter((doc) => doc.folderId === folderId);

  return (
    <div className="documents-page">
      {/* Toast Notification */}
      {toast && (
        <div className={`toast-notification ${toast.type}`}>
          <span>{toast.type === "success" ? "✅" : "⚠️"}</span>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Top Header */}
      <div className="documents-header">
        <div>
          <h1 className="page-title">🔐 Documents & Storage</h1>
          <p className="page-subtitle">
            Manage, verify, edit, and access all your secured documents.
          </p>
        </div>
        <div className="header-actions">
          <button
            className="btn-trash-nav"
            onClick={() => navigate("/trash")}
            title="View Deleted Documents"
          >
            🗑️ Trash Bin
          </button>
          <button
            className="btn-upload-primary"
            onClick={() => navigate("/upload")}
          >
            + Upload Document
          </button>
        </div>
      </div>

      {/* View Switcher Tabs */}
      <div className="tabs-bar">
        <button
          className={`tab-btn ${activeTab === "documents" ? "active" : ""}`}
          onClick={() => setActiveTab("documents")}
        >
          📄 All Documents ({totalElements})
        </button>
        <button
          className={`tab-btn ${activeTab === "folders" ? "active" : ""}`}
          onClick={() => setActiveTab("folders")}
        >
          📂 Your Folders ({folders.length})
        </button>
      </div>

      {/* TAB 1: ALL DOCUMENTS TABLE VIEW */}
      {activeTab === "documents" && (
        <div className="tab-content">
          {/* Search & Filter Controls */}
          <div className="filters-container">
            <div className="search-box">
              <span className="search-icon">🔍</span>
              <input
                type="text"
                placeholder="Search by title, filename, or description..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
              />
              {search && (
                <button className="clear-search" onClick={() => setSearch("")}>
                  ✕
                </button>
              )}
            </div>

            <div className="filter-group">
              <select
                value={category}
                onChange={(e) => {
                  setCategory(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All Categories</option>
                <option value="GENERAL">General</option>
                <option value="ID">Identification (ID)</option>
                <option value="FINANCIAL">Financial</option>
                <option value="LEGAL">Legal</option>
                <option value="MEDICAL">Medical</option>
                <option value="EDUCATION">Education</option>
                <option value="PERSONAL">Personal</option>
              </select>

              <select
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All Statuses</option>
                <option value="ACTIVE">Active</option>
                <option value="APPROVED">Approved</option>
                <option value="PENDING">Pending</option>
                <option value="REJECTED">Rejected</option>
              </select>

              <select
                value={sort}
                onChange={(e) => {
                  setSort(e.target.value);
                  setPage(0);
                }}
              >
                <option value="createdAt,desc">Newest First</option>
                <option value="createdAt,asc">Oldest First</option>
                <option value="title,asc">Title (A-Z)</option>
                <option value="title,desc">Title (Z-A)</option>
              </select>
            </div>
          </div>

          {loading ? (
            <div className="loading-state">
              <div className="spinner"></div>
              <p>Loading your secured documents...</p>
            </div>
          ) : error ? (
            <div className="error-state">
              <p className="error">{error}</p>
              <button className="btn-retry" onClick={loadDocuments}>
                Retry
              </button>
            </div>
          ) : docs.length === 0 ? (
            <div className="empty-state">
              <span className="empty-icon">📁</span>
              <h3>No documents found</h3>
              <p>No documents match your filter criteria or locker is empty.</p>
              <button
                className="btn-upload-primary"
                onClick={() => navigate("/upload")}
              >
                Upload your first document
              </button>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="documents-table">
                <thead>
                  <tr>
                    <th>Title & File</th>
                    <th>Category</th>
                    <th>Size & Type</th>
                    <th>Status</th>
                    <th>Uploaded</th>
                    <th className="actions-header">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {docs.map((doc) => {
                    const docId = doc.id || doc.doc_id;
                    const docTitle = doc.title || doc.doc_name || doc.originalFileName;
                    const isImageOrPdf =
                      doc.contentType === "application/pdf" ||
                      doc.contentType?.startsWith("image/") ||
                      doc.originalFileName?.endsWith(".pdf") ||
                      doc.originalFileName?.match(/\.(png|jpg|jpeg)$/i);

                    return (
                      <tr key={docId} className="doc-row">
                        <td className="doc-title-cell">
                          <div className="doc-meta-block">
                            <span className="file-badge-icon">
                              {doc.contentType?.includes("pdf") || doc.originalFileName?.endsWith(".pdf")
                                ? "📕"
                                : doc.contentType?.startsWith("image") || doc.originalFileName?.match(/\.(png|jpg|jpeg)$/i)
                                ? "🖼️"
                                : "📄"}
                            </span>
                            <div>
                              <strong
                                className="doc-link-title"
                                onClick={() => navigate(`/documentsfetch/${docId}`)}
                              >
                                {docTitle}
                              </strong>
                              <span className="doc-subtext">
                                {doc.originalFileName} • v{doc.currentVersion || 1}
                              </span>
                              {doc.tags && doc.tags.size > 0 && (
                                <div className="tags-container">
                                  {Array.from(doc.tags).map((tag) => (
                                    <span key={tag} className="tag-pill">
                                      #{tag}
                                    </span>
                                  ))}
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                        <td>
                          <span className="category-badge">{doc.category || "GENERAL"}</span>
                        </td>
                        <td>
                          <div className="size-type-cell">
                            <span>{formatFileSize(doc.fileSize)}</span>
                            <span className="type-subtext">
                              {doc.contentType ? doc.contentType.split("/")[1]?.toUpperCase() : "FILE"}
                            </span>
                          </div>
                        </td>
                        <td>
                          <span className={`status-badge ${(doc.status || "ACTIVE").toLowerCase()}`}>
                            {doc.status || "ACTIVE"}
                          </span>
                          {doc.status === "REJECTED" && doc.rejectionComment && (
                            <div className="rejection-comment-hint" title={doc.rejectionComment} style={{ fontSize: "11px", color: "#dc2626", marginTop: "4px", maxWidth: "160px", wordBreak: "break-word" }}>
                              <strong>Reason:</strong> {doc.rejectionComment}
                            </div>
                          )}
                        </td>
                        <td className="date-cell">{formatDate(doc.createdAt)}</td>
                        <td className="actions-cell">
                          {isImageOrPdf && (
                            <button
                              className="action-btn view-btn"
                              title="View Document Inline"
                              onClick={() => handleViewInline(doc)}
                            >
                              👁️ View
                            </button>
                          )}
                          <button
                            className="action-btn download-btn"
                            title="Download Document"
                            onClick={() => handleDownload(doc)}
                          >
                            ⬇️ Download
                          </button>
                          <button
                            className="action-btn edit-btn"
                            title="Edit Metadata"
                            onClick={() => openEditModal(doc)}
                          >
                            ✏️ Edit
                          </button>
                          {doc.status === "REJECTED" && (
                            <button
                              className="action-btn resubmit-btn"
                              title="Resubmit Document for Review"
                              onClick={() => handleResubmit(doc)}
                              style={{ backgroundColor: "#0284c7", color: "#fff", borderColor: "#0284c7" }}
                            >
                              🔄 Resubmit
                            </button>
                          )}
                          <button
                            className="action-btn delete-btn"
                            title="Move to Trash"
                            onClick={() => setDeleteModalDoc(doc)}
                          >
                            🗑️ Delete
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {/* Pagination Controls */}
              <div className="pagination-bar">
                <div className="pagination-info">
                  Showing Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong> ({totalElements} total items)
                </div>
                <div className="pagination-buttons">
                  <select
                    className="page-size-select"
                    value={size}
                    onChange={(e) => {
                      setSize(Number(e.target.value));
                      setPage(0);
                    }}
                  >
                    <option value={5}>5 per page</option>
                    <option value={10}>10 per page</option>
                    <option value={25}>25 per page</option>
                  </select>
                  <button
                    className="pager-btn"
                    disabled={page === 0}
                    onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
                  >
                    ← Previous
                  </button>
                  <button
                    className="pager-btn"
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage((prev) => prev + 1)}
                  >
                    Next →
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: FOLDERS VIEW (Backwards compatibility) */}
      {activeTab === "folders" && (
        <div className="tab-content folders-tab">
          <h2 className="section-title">📂 Your Folders</h2>
          {folders.length === 0 ? (
            <p className="no-folders">No folders available.</p>
          ) : (
            <div className="folders-list">
              {folders.map((folder) => {
                const folderDocs = getDocsByFolder(folder.fol_id);
                return (
                  <div key={folder.fol_id} className="folder-card">
                    <div
                      className="folder-header"
                      onClick={() =>
                        setOpenFolder(
                          openFolder === folder.fol_id ? null : folder.fol_id
                        )
                      }
                    >
                      <span className="folder-name">📁 {folder.fol_name}</span>
                      <span className="folder-count">{folderDocs.length} file(s)</span>
                    </div>

                    {openFolder === folder.fol_id && (
                      <ul className="docs-list">
                        {folderDocs.length === 0 ? (
                          <li className="no-files">No files in this folder.</li>
                        ) : (
                          folderDocs.map((doc) => (
                            <li key={doc.id || doc.doc_id} className="doc-item">
                              <span>📄 {doc.title || doc.doc_name}</span>
                              <div className="doc-item-actions">
                                <button
                                  onClick={() =>
                                    navigate(`/documentsfetch/${doc.id || doc.doc_id}`)
                                  }
                                  className="open-doc-btn"
                                >
                                  Details
                                </button>
                                <button
                                  onClick={() => handleDownload(doc)}
                                  className="download-sm-btn"
                                >
                                  Download
                                </button>
                              </div>
                            </li>
                          ))
                        )}
                      </ul>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* PREVIEW INLINE MODAL */}
      {previewDoc && (
        <div className="modal-backdrop" onClick={closePreview}>
          <div className="modal-content preview-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>
                👁️ Preview: {previewDoc.title || previewDoc.originalFileName}
              </h3>
              <button className="modal-close-btn" onClick={closePreview}>
                ✕
              </button>
            </div>
            <div className="preview-body">
              {loadingPreview ? (
                <div className="loading-state">
                  <div className="spinner"></div>
                  <p>Streaming document preview...</p>
                </div>
              ) : previewBlobUrl ? (
                previewDoc.contentType?.startsWith("image") ||
                previewDoc.originalFileName?.match(/\.(png|jpg|jpeg)$/i) ? (
                  <img
                    src={previewBlobUrl}
                    alt={previewDoc.title}
                    className="image-preview"
                  />
                ) : (
                  <iframe
                    src={previewBlobUrl}
                    title={previewDoc.title}
                    className="iframe-preview"
                  />
                )
              ) : (
                <p>Preview unavailable for this format.</p>
              )}
            </div>
            <div className="modal-footer">
              <button
                className="btn-download-modal"
                onClick={() => handleDownload(previewDoc)}
              >
                ⬇️ Download Original
              </button>
              <button className="btn-cancel" onClick={closePreview}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* EDIT METADATA MODAL */}
      {editModalDoc && (
        <div className="modal-backdrop" onClick={() => setEditModalDoc(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>✏️ Edit Document Details</h3>
              <button className="modal-close-btn" onClick={() => setEditModalDoc(null)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>Document Title</label>
                <input
                  type="text"
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Category</label>
                <select
                  value={editCategory}
                  onChange={(e) => setEditCategory(e.target.value)}
                >
                  <option value="GENERAL">General</option>
                  <option value="ID">Identification (ID / Passport)</option>
                  <option value="FINANCIAL">Financial / Bank</option>
                  <option value="LEGAL">Legal / Contracts</option>
                  <option value="MEDICAL">Medical Records</option>
                  <option value="EDUCATION">Educational Certificates</option>
                  <option value="PERSONAL">Personal</option>
                </select>
              </div>

              <div className="form-group">
                <label>Tags (comma-separated)</label>
                <input
                  type="text"
                  value={editTags}
                  onChange={(e) => setEditTags(e.target.value)}
                  placeholder="e.g. 2026, passport, verified"
                />
              </div>

              <div className="form-group">
                <label>Description</label>
                <textarea
                  rows="3"
                  value={editDescription}
                  onChange={(e) => setEditDescription(e.target.value)}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={() => setEditModalDoc(null)}>
                Cancel
              </button>
              <button
                className="btn-save"
                onClick={handleSaveEdit}
                disabled={savingEdit}
              >
                {savingEdit ? "Saving..." : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {deleteModalDoc && (
        <div className="modal-backdrop" onClick={() => setDeleteModalDoc(null)}>
          <div className="modal-content confirm-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>🗑️ Move Document to Trash?</h3>
              <button className="modal-close-btn" onClick={() => setDeleteModalDoc(null)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <p>
                Are you sure you want to delete <strong>"{deleteModalDoc.title || deleteModalDoc.originalFileName}"</strong>?
              </p>
              <p className="warning-text">
                This document will be soft-deleted. The physical file remains securely preserved on disk and can be restored anytime from your <strong>Trash Bin</strong>.
              </p>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={() => setDeleteModalDoc(null)}>
                Cancel
              </button>
              <button className="btn-danger-confirm" onClick={confirmDelete}>
                Move to Trash
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
