import React, { useEffect, useState, useCallback } from "react";
import { fetchAdminAuditLogs, exportAdminAuditLogs } from "../services/api";
import "./AdminAuditLogs.css";

const ACTION_OPTIONS = [
  "ALL",
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "LOGOUT",
  "REGISTER",
  "UPLOAD",
  "VIEW",
  "DOWNLOAD",
  "UPDATE",
  "DELETE",
  "RESTORE",
  "VERSION_ADD",
  "VERSION_RESTORE",
  "SHARE_CREATE",
  "SHARE_REVOKE",
  "APPROVE",
  "REJECT",
  "VERIFY",
  "ROLE_CHANGE",
  "USER_DISABLE",
  "USER_ENABLE",
];

const ENTITY_OPTIONS = [
  "ALL",
  "DOCUMENT",
  "DOCUMENT_VERSION",
  "DOCUMENT_SHARE",
  "DOCUMENT_APPROVAL",
  "VERIFICATION_RECORD",
  "USER",
];

export default function AdminAuditLogs() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [userFilter, setUserFilter] = useState("");
  const [actionFilter, setActionFilter] = useState("ALL");
  const [entityFilter, setEntityFilter] = useState("ALL");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  // Pagination
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [exporting, setExporting] = useState(false);

  const loadAuditLogs = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {
        page,
        size: pageSize,
        sort: "timestamp",
        direction: "desc",
      };
      if (userFilter.trim()) params.user = userFilter.trim();
      if (actionFilter && actionFilter !== "ALL") params.action = actionFilter;
      if (entityFilter && entityFilter !== "ALL") params.entityType = entityFilter;
      if (fromDate) params.from = fromDate;
      if (toDate) params.to = toDate;

      const res = await fetchAdminAuditLogs(params);
      if (res.data) {
        setLogs(Array.isArray(res.data.content) ? res.data.content : []);
        setTotalPages(res.data.totalPages || 0);
        setTotalElements(res.data.totalElements || 0);
      } else {
        setLogs([]);
      }
    } catch (err) {
      console.error("Failed to load audit logs:", err);
      setError(err.response?.data?.message || "Failed to load audit logs");
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, userFilter, actionFilter, entityFilter, fromDate, toDate]);

  useEffect(() => {
    loadAuditLogs();
  }, [loadAuditLogs]);

  const handleExportCsv = async () => {
    try {
      setExporting(true);
      const params = {};
      if (userFilter.trim()) params.user = userFilter.trim();
      if (actionFilter && actionFilter !== "ALL") params.action = actionFilter;
      if (entityFilter && entityFilter !== "ALL") params.entityType = entityFilter;
      if (fromDate) params.from = fromDate;
      if (toDate) params.to = toDate;

      const res = await exportAdminAuditLogs(params);
      const blob = new Blob([res.data], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `audit_logs_${new Date().toISOString().split("T")[0]}.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error("Failed to export audit logs:", err);
      alert("Failed to export audit logs CSV.");
    } finally {
      setExporting(false);
    }
  };

  const resetFilters = () => {
    setUserFilter("");
    setActionFilter("ALL");
    setEntityFilter("ALL");
    setFromDate("");
    setToDate("");
    setPage(0);
  };

  const getActionBadgeClass = (action) => {
    if (!action) return "neutral";
    const act = action.toUpperCase();
    if (act.includes("APPROVE") || act.includes("SUCCESS") || act.includes("RESTORE")) return "success";
    if (act.includes("REJECT") || act.includes("FAIL") || act.includes("DELETE") || act.includes("REVOKE") || act.includes("DISABLE")) return "danger";
    if (act.includes("UPLOAD") || act.includes("ADD") || act.includes("REGISTER") || act.includes("CREATE")) return "primary";
    if (act.includes("VIEW") || act.includes("DOWNLOAD") || act.includes("VERIFY")) return "info";
    return "warning";
  };

  return (
    <div className="audit-logs-container">
      {/* Header Banner */}
      <div className="audit-header-card">
        <div className="audit-header-info">
          <h1>📜 System Activity & Audit Trail</h1>
          <p>
            Immutable, append-only security logs for compliance, incident investigation, and forensic auditing. Tracks all authentications, approvals, document lifecycle events, and shares.
          </p>
        </div>
        <div className="audit-header-actions">
          <div className="stat-pill">
            <span className="stat-label">Total Events:</span>
            <span className="stat-value">{totalElements}</span>
          </div>
          <button
            className="btn-export-csv"
            onClick={handleExportCsv}
            disabled={exporting}
            title="Export filtered records as CSV"
          >
            {exporting ? "Generating CSV..." : "📥 Export CSV"}
          </button>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="audit-filters-bar">
        <div className="filter-group">
          <label htmlFor="filter-user">User Email</label>
          <input
            id="filter-user"
            type="text"
            placeholder="Search email..."
            value={userFilter}
            onChange={(e) => {
              setUserFilter(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <div className="filter-group">
          <label htmlFor="filter-action">Action</label>
          <select
            id="filter-action"
            value={actionFilter}
            onChange={(e) => {
              setActionFilter(e.target.value);
              setPage(0);
            }}
          >
            {ACTION_OPTIONS.map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label htmlFor="filter-entity">Entity Type</label>
          <select
            id="filter-entity"
            value={entityFilter}
            onChange={(e) => {
              setEntityFilter(e.target.value);
              setPage(0);
            }}
          >
            {ENTITY_OPTIONS.map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label htmlFor="filter-from">From Date</label>
          <input
            id="filter-from"
            type="date"
            value={fromDate}
            onChange={(e) => {
              setFromDate(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <div className="filter-group">
          <label htmlFor="filter-to">To Date</label>
          <input
            id="filter-to"
            type="date"
            value={toDate}
            onChange={(e) => {
              setToDate(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <div className="filter-actions">
          <button className="btn-reset-filters" onClick={resetFilters}>
            Reset
          </button>
        </div>
      </div>

      {/* Audit Logs Table */}
      <div className="audit-table-wrapper">
        {loading ? (
          <div className="loading-state">
            <div className="spinner"></div>
            <p>Loading audit trail...</p>
          </div>
        ) : error ? (
          <div className="error-state">
            <p>⚠️ {error}</p>
            <button className="btn-retry" onClick={loadAuditLogs}>
              Retry
            </button>
          </div>
        ) : logs.length === 0 ? (
          <div className="empty-state">
            <span className="empty-icon">🛡️</span>
            <h3>No Audit Logs Found</h3>
            <p>No activity records match your current filter parameters.</p>
          </div>
        ) : (
          <table className="audit-table" aria-label="System activity log table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>User</th>
                <th>Action</th>
                <th>Target Entity</th>
                <th>Client IP</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id} className="audit-row">
                  <td className="time-col">
                    {log.timestamp
                      ? new Date(log.timestamp).toLocaleString("en-US", {
                          dateStyle: "medium",
                          timeStyle: "medium",
                        })
                      : "N/A"}
                  </td>
                  <td>
                    <div className="user-col-info">
                      <strong>{log.userName || log.userEmail || log.performedByEmail || "System / Public"}</strong>
                      <span className="user-email-sub">{log.userEmail || log.performedByEmail || "Anonymous"}</span>
                    </div>
                  </td>
                  <td>
                    <span className={`action-badge ${getActionBadgeClass(log.action)}`}>
                      {log.action}
                    </span>
                  </td>
                  <td>
                    <div className="entity-col-info">
                      <span className="entity-type-tag">{log.entityType || "N/A"}</span>
                      {log.entityId && <span className="entity-id-tag">#{log.entityId}</span>}
                    </div>
                  </td>
                  <td>
                    <code className="ip-code">{log.ipAddress || "127.0.0.1"}</code>
                  </td>
                  <td className="details-col">
                    <span className="details-text" title={log.details}>
                      {log.details || "—"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="audit-pagination">
            <button
              className="btn-page"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Previous
            </button>
            <span className="page-indicator">
              Page {page + 1} of {totalPages} ({totalElements} total entries)
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
    </div>
  );
}
