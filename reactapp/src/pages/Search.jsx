import React, { useState, useEffect, useCallback, useRef } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { useSelector } from "react-redux";
import { searchDocuments, downloadDocument } from "../services/api";
import HighlightSnippet from "../components/HighlightSnippet";
import "./Search.css";

const CATEGORIES = [
  "ALL",
  "ACADEMICS",
  "IDENTIFICATION",
  "FINANCIAL",
  "LEGAL",
  "MEDICAL",
  "CERTIFICATE",
  "GENERAL"
];

const STATUSES = ["ALL", "ACTIVE", "PENDING", "APPROVED", "REJECTED"];

export default function Search() {
  const [searchParams, setSearchParams] = useSearchParams();

  const reduxUser = useSelector((state) => state.auth?.user);
  let user = reduxUser;
  if (!user) {
    try {
      user = JSON.parse(localStorage.getItem("user") || localStorage.getItem("dl_user") || "null");
    } catch {
      user = null;
    }
  }
  const isAdmin = user?.role === "ADMIN" || (Array.isArray(user?.roles) && user.roles.some((r) => r.toUpperCase().includes("ADMIN")));

  // Query state initialized from URL search params
  const initialQ = searchParams.get("q") || "";
  const initialCategory = searchParams.get("category") || "ALL";
  const initialStatus = searchParams.get("status") || "ALL";
  const initialOwner = searchParams.get("owner") || "";
  const initialTag = searchParams.get("tag") || "";
  const initialFrom = searchParams.get("from") || "";
  const initialTo = searchParams.get("to") || "";
  const initialPage = parseInt(searchParams.get("page") || "0", 10);

  const [searchInput, setSearchInput] = useState(initialQ);
  const [debouncedQ, setDebouncedQ] = useState(initialQ);
  const [category, setCategory] = useState(initialCategory);
  const [status, setStatus] = useState(initialStatus);
  const [owner, setOwner] = useState(initialOwner);
  const [tag, setTag] = useState(initialTag);
  const [fromDate, setFromDate] = useState(initialFrom);
  const [toDate, setToDate] = useState(initialTo);
  const [page, setPage] = useState(initialPage);
  const [pageSize] = useState(10);

  const [results, setResults] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [downloadingId, setDownloadingId] = useState(null);

  const debounceTimerRef = useRef(null);

  // Debounce input typing (400 ms)
  useEffect(() => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }
    debounceTimerRef.current = setTimeout(() => {
      setDebouncedQ(searchInput);
      setPage(0);
    }, 400);

    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [searchInput]);

  // Sync state to URL search parameters
  useEffect(() => {
    const params = {};
    if (debouncedQ.trim()) params.q = debouncedQ.trim();
    if (category && category !== "ALL") params.category = category;
    if (status && status !== "ALL") params.status = status;
    if (owner.trim()) params.owner = owner.trim();
    if (tag.trim()) params.tag = tag.trim();
    if (fromDate) params.from = fromDate;
    if (toDate) params.to = toDate;
    if (page > 0) params.page = page.toString();

    setSearchParams(params, { replace: true });
  }, [debouncedQ, category, status, owner, tag, fromDate, toDate, page, setSearchParams]);

  // Execute Search API call
  const performSearch = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const params = {
        page,
        size: pageSize
      };
      if (debouncedQ.trim()) params.q = debouncedQ.trim();
      if (category && category !== "ALL") params.category = category;
      if (status && status !== "ALL") params.status = status;
      if (owner.trim()) params.owner = owner.trim();
      if (tag.trim()) params.tag = tag.trim();
      if (fromDate) params.from = fromDate;
      if (toDate) params.to = toDate;

      const res = await searchDocuments(params);
      if (res.data) {
        setResults(Array.isArray(res.data.content) ? res.data.content : []);
        setTotalElements(res.data.totalElements || 0);
        setTotalPages(res.data.totalPages || 0);
      } else {
        setResults([]);
        setTotalElements(0);
        setTotalPages(0);
      }
    } catch (err) {
      console.error("Search error:", err);
      setError(err.response?.data?.message || "Failed to search documents.");
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, [debouncedQ, category, status, owner, tag, fromDate, toDate, page, pageSize]);

  useEffect(() => {
    performSearch();
  }, [performSearch]);

  const handleClearFilters = () => {
    setSearchInput("");
    setDebouncedQ("");
    setCategory("ALL");
    setStatus("ALL");
    setOwner("");
    setTag("");
    setFromDate("");
    setToDate("");
    setPage(0);
  };

  const handleDownload = async (docId, fileName) => {
    try {
      setDownloadingId(docId);
      const res = await downloadDocument(docId);
      const blob = new Blob([res.data]);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", fileName || `document_${docId}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error("Error downloading file:", err);
      alert("Failed to download file.");
    } finally {
      setDownloadingId(null);
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
    if (!isoString) return "-";
    try {
      return new Date(isoString).toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric"
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="search-page-container">
      {/* Header */}
      <div className="search-header">
        <h1 className="search-title">Content & Document Search</h1>
        <p className="search-subtitle">
          Search document metadata and extracted text from scanned PDFs, images, and DOCX files.
        </p>
      </div>

      {/* Main Search Bar */}
      <div className="search-bar-wrapper">
        <div className="search-input-box">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            className="search-input"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by keywords, CGPA, numbers, title, text inside scanned docs..."
            autoFocus
          />
          {searchInput && (
            <button
              className="search-clear-btn"
              onClick={() => setSearchInput("")}
              title="Clear search"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="search-filters-panel">
        <div className="filter-group">
          <label>Category</label>
          <select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setPage(0);
            }}
          >
            {CATEGORIES.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label>Status</label>
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            {STATUSES.map((st) => (
              <option key={st} value={st}>
                {st}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label>Tag</label>
          <input
            type="text"
            placeholder="e.g. invoice, vit"
            value={tag}
            onChange={(e) => {
              setTag(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <div className="filter-group">
          <label>From Date</label>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => {
              setFromDate(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <div className="filter-group">
          <label>To Date</label>
          <input
            type="date"
            value={toDate}
            onChange={(e) => {
              setToDate(e.target.value);
              setPage(0);
            }}
          />
        </div>

        {isAdmin && (
          <div className="filter-group">
            <label>Owner Email</label>
            <input
              type="text"
              placeholder="e.g. alice@example.com"
              value={owner}
              onChange={(e) => {
                setOwner(e.target.value);
                setPage(0);
              }}
            />
          </div>
        )}

        <div className="filter-actions">
          <button
            type="button"
            className="clear-filters-btn"
            onClick={handleClearFilters}
          >
            Reset Filters
          </button>
        </div>
      </div>

      {/* Results Header / Stats */}
      <div className="search-stats-bar">
        <span>
          {loading ? (
            "Searching documents..."
          ) : (
            <>
              Found <strong>{totalElements}</strong> {totalElements === 1 ? "document" : "documents"}
              {debouncedQ.trim() && (
                <span> for query "<strong>{debouncedQ}</strong>"</span>
              )}
            </>
          )}
        </span>
      </div>

      {error && <div className="search-error-banner">{error}</div>}

      {/* Results List */}
      {loading ? (
        <div className="search-loading-container">
          <div className="spinner"></div>
          <p>Scanning index and document contents...</p>
        </div>
      ) : results.length > 0 ? (
        <div className="search-results-list">
          {results.map((item) => (
            <div key={item.id} className="search-result-card">
              <div className="card-top-row">
                <div className="card-title-group">
                  <Link to={`/documentsfetch/${item.id}`} className="result-title-link">
                    <HighlightSnippet snippet={item.title || item.originalFileName} query={debouncedQ} />
                  </Link>
                  <span className={`status-pill status-${(item.status || "ACTIVE").toLowerCase()}`}>
                    {item.status || "ACTIVE"}
                  </span>
                  <span className="category-pill">{item.category || "GENERAL"}</span>
                </div>

                <div className="badge-group">
                  {/* Match Source Badge */}
                  {item.matchSource === "content" ? (
                    <span className="match-source-badge content" title="Matched inside extracted document content (OCR)">
                      📄 Content Match
                    </span>
                  ) : (
                    <span className="match-source-badge metadata" title="Matched in document metadata (title, description, tags, filename)">
                      🏷️ Metadata Match
                    </span>
                  )}

                  {/* OCR Status Badge */}
                  {item.ocrStatus === "DONE" && (
                    <span className="ocr-status-pill done" title="OCR processing completed">
                      ✓ OCR Ready
                    </span>
                  )}
                  {item.ocrStatus === "PENDING" && (
                    <span className="ocr-status-pill pending" title="OCR processing in progress">
                      ⏳ OCR Pending
                    </span>
                  )}
                  {item.ocrStatus === "FAILED" && (
                    <span className="ocr-status-pill failed" title="OCR processing failed">
                      ⚠️ OCR Failed
                    </span>
                  )}
                  {item.ocrStatus === "SKIPPED" && (
                    <span className="ocr-status-pill skipped" title="OCR skipped for this file type">
                      ⚪ OCR Skipped
                    </span>
                  )}
                </div>
              </div>

              {/* Snippet Area with Safe Highlighting */}
              <div className="snippet-container">
                <span className="snippet-label">Snippet:</span>
                <p className="snippet-text">
                  <HighlightSnippet snippet={item.snippet} query={debouncedQ} />
                </p>
              </div>

              {/* Tags if present */}
              {item.tags && item.tags.length > 0 && (
                <div className="search-tags-row">
                  {item.tags.map((t, i) => (
                    <span key={i} className="search-tag-chip">
                      #{t}
                    </span>
                  ))}
                </div>
              )}

              {/* Metadata Footer */}
              <div className="card-footer-meta">
                <div className="meta-left">
                  <span>📁 {item.originalFileName}</span>
                  <span>📦 {formatFileSize(item.fileSize)}</span>
                  <span>🔄 Version v{item.currentVersion || 1}</span>
                  <span>📅 {formatDate(item.createdAt)}</span>
                  {isAdmin && item.ownerEmail && (
                    <span>👤 {item.ownerEmail}</span>
                  )}
                </div>

                <div className="meta-actions">
                  <Link to={`/documentsfetch/${item.id}`} className="action-btn view-btn">
                    View
                  </Link>
                  <button
                    className="action-btn download-btn"
                    onClick={() => handleDownload(item.id, item.originalFileName)}
                    disabled={downloadingId === item.id}
                  >
                    {downloadingId === item.id ? "Downloading..." : "Download"}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        /* Empty State */
        <div className="search-empty-state">
          <div className="empty-icon">🔎</div>
          <h3>No matching documents found</h3>
          <p>
            {debouncedQ.trim()
              ? `We couldn't find any documents or OCR content matching "${debouncedQ}". Try searching with different keywords or clearing filters.`
              : "No documents match the selected filters. Try broadening your criteria."}
          </p>
          <button className="empty-reset-btn" onClick={handleClearFilters}>
            Clear All Filters
          </button>
        </div>
      )}

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="search-pagination">
          <button
            className="pagination-btn"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            ← Previous
          </button>

          <span className="pagination-info">
            Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
          </span>

          <button
            className="pagination-btn"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next →
          </button>
        </div>
      )}
    </div>
  );
}
