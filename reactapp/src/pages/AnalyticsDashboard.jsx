import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { useSelector } from "react-redux";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  PieChart,
  Pie,
  Cell,
} from "recharts";
import {
  fetchAnalyticsSummary,
  fetchUploadsByMonth,
  fetchApprovalsByMonth,
  fetchDocumentsByCategory,
  fetchRecentActivity,
} from "../services/api";
import "./AnalyticsDashboard.css";

// Dynamic category palette
const CATEGORY_COLORS = [
  "#1D3B66", // Navy 700
  "#F5A623", // Gold Accent
  "#10b981", // Emerald
  "#f59e0b", // Amber
  "#06b6d4", // Cyan
  "#3b82f6", // Blue
  "#64748b", // Slate
  "#ec4899", // Pink
];

// Helper: Format bytes to human readable string (KB, MB, GB, etc.)
export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined || isNaN(bytes) || bytes <= 0) {
    return "0 B";
  }
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const val = parseFloat((bytes / Math.pow(k, i)).toFixed(2));
  return `${val} ${sizes[i] || "B"}`;
}

// Helper: Format date string
export function formatDate(dt) {
  if (!dt) return "—";
  try {
    const d = new Date(dt);
    return d.toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return String(dt);
  }
}

// Helper: Action badge class name
export function getActionBadgeClass(action) {
  if (!action) return "action-badge default";
  const act = action.toUpperCase();
  if (act.includes("UPLOAD")) return "action-badge upload";
  if (act.includes("APPROV")) return "action-badge approve";
  if (act.includes("REJECT")) return "action-badge reject";
  if (act.includes("DELETE") || act.includes("TRASH")) return "action-badge delete";
  if (act.includes("SHARE")) return "action-badge share";
  if (act.includes("LOGIN")) return "action-badge login";
  if (act.includes("VERIF")) return "action-badge verify";
  return "action-badge default";
}

export default function AnalyticsDashboard() {
  // Extract user & roles
  const reduxUser = useSelector((state) => state.auth?.user);
  let user = reduxUser;
  if (!user) {
    try {
      user = JSON.parse(
        localStorage.getItem("user") || localStorage.getItem("dl_user") || "null"
      );
    } catch {
      user = null;
    }
  }

  const userRoles = [];
  if (user?.role) {
    userRoles.push(user.role.toUpperCase().replace("ROLE_", ""));
  }
  if (Array.isArray(user?.roles)) {
    user.roles.forEach((r) => {
      userRoles.push(r.toUpperCase().replace("ROLE_", ""));
    });
  }

  const isAdmin = userRoles.includes("ADMIN");
  const isEmployee = userRoles.includes("EMPLOYEE");
  const isReviewer = userRoles.includes("REVIEWER");
  const isVerifierOnly =
    userRoles.includes("VERIFIER") && !isAdmin && !isEmployee && !isReviewer;

  // Primary active role for badge display
  let primaryRole = "EMPLOYEE";
  if (isAdmin) primaryRole = "ADMIN";
  else if (isReviewer) primaryRole = "REVIEWER";
  else if (isEmployee) primaryRole = "EMPLOYEE";
  else if (isVerifierOnly) primaryRole = "VERIFIER";

  // Data states
  const [summary, setSummary] = useState(null);
  const [uploadsByMonth, setUploadsByMonth] = useState([]);
  const [approvalsByMonth, setApprovalsByMonth] = useState([]);
  const [categories, setCategories] = useState([]);
  const [recentActivity, setRecentActivity] = useState([]);

  // UI states
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const loadData = useCallback(async (isRefresh = false) => {
    if (isVerifierOnly) {
      setLoading(false);
      return;
    }

    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);

    try {
      const [
        summaryRes,
        uploadsRes,
        approvalsRes,
        categoriesRes,
        activityRes,
      ] = await Promise.all([
        fetchAnalyticsSummary(),
        fetchUploadsByMonth(),
        fetchApprovalsByMonth(),
        fetchDocumentsByCategory(),
        fetchRecentActivity(),
      ]);

      setSummary(summaryRes.data || null);
      setUploadsByMonth(uploadsRes.data || []);
      setApprovalsByMonth(approvalsRes.data || []);
      setCategories(categoriesRes.data || []);
      setRecentActivity(activityRes.data || []);
    } catch (err) {
      console.error("Error loading analytics data:", err);
      if (err.response?.status === 403) {
        setError("You do not have permission to view analytics.");
      } else {
        setError(
          err.response?.data?.message ||
            "Failed to load analytics dashboard data. Please check your connection and retry."
        );
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [isVerifierOnly]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // If role is VERIFIER only -> 403 Forbidden display
  if (isVerifierOnly) {
    return (
      <div className="analytics-container">
        <div className="analytics-forbidden-card" role="alert">
          <div className="forbidden-icon">🚫</div>
          <h2>Access Restricted</h2>
          <p>
            The Analytics Dashboard is restricted to Administrators, Reviewers, and
            Document Owners. Verifier accounts only have access to document verification.
          </p>
          <Link to="/verifier" className="btn-primary-home">
            Go to Verifier Console
          </Link>
        </div>
      </div>
    );
  }

  // Calculate category totals for donut percentages
  const totalCategoryDocs = categories.reduce((sum, c) => sum + (c.count || 0), 0);

  return (
    <div className="analytics-container" data-testid="analytics-dashboard">
      {/* Header */}
      <div className="analytics-header">
        <div className="analytics-title-group">
          <h1>📊 Analytics Dashboard</h1>
          <p>
            Real-time aggregate performance, document metrics, and operational
            trends.
          </p>
        </div>
        <div className="analytics-actions">
          <span className={`role-badge ${primaryRole.toLowerCase()}`}>
            ● {primaryRole} Scope
          </span>
          <button
            className="btn-refresh"
            onClick={() => loadData(true)}
            disabled={loading || refreshing}
            title="Refresh dashboard metrics"
          >
            <span className={refreshing ? "spin-icon" : ""}>🔄</span> Refresh
          </button>
        </div>
      </div>

      {/* Scope Banner */}
      <div className="scope-banner" role="status">
        <span>ℹ️</span>
        <div>
          {isAdmin && (
            <span>
              <strong>System-Wide View:</strong> Showing aggregate statistics
              across all users, document categories, and system-wide storage.
            </span>
          )}
          {isEmployee && !isAdmin && (
            <span>
              <strong>Personal View:</strong> Showing statistics scoped strictly to
              your uploaded documents, your storage usage, and personal activity.
            </span>
          )}
          {isReviewer && !isAdmin && (
            <span>
              <strong>Reviewer View:</strong> Showing approval queue status and
              your personal approval decisions and activity.
            </span>
          )}
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="analytics-error-card" role="alert">
          <h3>Unable to Load Analytics</h3>
          <p>{error}</p>
          <button className="btn-retry" onClick={() => loadData(false)}>
            Try Again
          </button>
        </div>
      )}

      {/* Skeletons when Loading */}
      {loading && !error && (
        <>
          <div className="stat-cards-grid">
            <div className="skeleton skeleton-stat-card"></div>
            <div className="skeleton skeleton-stat-card"></div>
            <div className="skeleton skeleton-stat-card"></div>
            <div className="skeleton skeleton-stat-card"></div>
            <div className="skeleton skeleton-stat-card"></div>
            <div className="skeleton skeleton-stat-card"></div>
          </div>
          <div className="charts-grid">
            <div className="skeleton skeleton-chart"></div>
            <div className="skeleton skeleton-chart"></div>
          </div>
          <div className="skeleton skeleton-table"></div>
        </>
      )}

      {/* Main Content once loaded */}
      {!loading && !error && (
        <>
          {/* Stat Cards */}
          <div className="stat-cards-grid" data-testid="stat-cards-grid">
            {/* 1. Total Documents */}
            <div className="stat-card" data-testid="card-total-docs">
              <div className="stat-card-header">
                <span className="stat-card-title">
                  {isReviewer && !isAdmin ? "Total Processed" : "Total Documents"}
                </span>
                <div className="stat-card-icon icon-blue">📄</div>
              </div>
              <div className="stat-card-value">
                {summary?.totalDocuments !== undefined ? summary.totalDocuments : 0}
              </div>
              <div className="stat-card-subtext">
                {isReviewer && !isAdmin
                  ? "Pending queue + decisions"
                  : "Active documents in vault"}
              </div>
            </div>

            {/* 2. Pending Approvals */}
            <div className="stat-card" data-testid="card-pending">
              <div className="stat-card-header">
                <span className="stat-card-title">Pending Approvals</span>
                <div className="stat-card-icon icon-amber">⏳</div>
              </div>
              <div className="stat-card-value">
                {summary?.pendingApprovals !== undefined ? summary.pendingApprovals : 0}
              </div>
              <div className="stat-card-subtext">Awaiting reviewer action</div>
            </div>

            {/* 3. Approved Documents */}
            <div className="stat-card" data-testid="card-approved">
              <div className="stat-card-header">
                <span className="stat-card-title">Approved</span>
                <div className="stat-card-icon icon-green">✅</div>
              </div>
              <div className="stat-card-value">
                {summary?.approvedDocuments !== undefined ? summary.approvedDocuments : 0}
              </div>
              <div className="stat-card-subtext">
                {isReviewer && !isAdmin ? "Approved by you" : "Successfully approved"}
              </div>
            </div>

            {/* 4. Rejected Documents */}
            <div className="stat-card" data-testid="card-rejected">
              <div className="stat-card-header">
                <span className="stat-card-title">Rejected</span>
                <div className="stat-card-icon icon-red">❌</div>
              </div>
              <div className="stat-card-value">
                {summary?.rejectedDocuments !== undefined ? summary.rejectedDocuments : 0}
              </div>
              <div className="stat-card-subtext">
                {isReviewer && !isAdmin ? "Rejected by you" : "Rejected documents"}
              </div>
            </div>

            {/* 5. Verified Documents */}
            <div className="stat-card" data-testid="card-verified">
              <div className="stat-card-header">
                <span className="stat-card-title">Verified Docs</span>
                <div className="stat-card-icon icon-cyan">🛡️</div>
              </div>
              <div className="stat-card-value">
                {summary?.verifiedDocuments !== undefined ? summary.verifiedDocuments : 0}
              </div>
              <div className="stat-card-subtext">With secure QR badge</div>
            </div>

            {/* 6. Active Users (Admin Only) */}
            {isAdmin && (
              <div className="stat-card" data-testid="card-active-users">
                <div className="stat-card-header">
                  <span className="stat-card-title">Active Users (30d)</span>
                  <div className="stat-card-icon icon-purple">👥</div>
                </div>
                <div className="stat-card-value">
                  {summary?.activeUsers !== undefined ? summary.activeUsers : 0}
                </div>
                <div className="stat-card-subtext">Users with audit activity</div>
              </div>
            )}

            {/* 7. Storage Used (Admin and Employee) */}
            {!isReviewer || isAdmin ? (
              <div className="stat-card" data-testid="card-storage">
                <div className="stat-card-header">
                  <span className="stat-card-title">Storage Used</span>
                  <div className="stat-card-icon icon-purple">💾</div>
                </div>
                <div className="stat-card-value">
                  {formatBytes(summary?.storageUsedBytes)}
                </div>
                <div className="stat-card-subtext">
                  {summary?.storageUsedBytes ? `${summary.storageUsedBytes.toLocaleString()} bytes` : "0 bytes"}
                </div>
              </div>
            ) : null}
          </div>

          {/* Charts Row 1: Uploads & Approvals */}
          <div className="charts-grid">
            {/* Chart 1: Uploads by Month (12 Months) */}
            <div className="chart-card" data-testid="chart-uploads">
              <div className="chart-card-header">
                <div>
                  <h3 className="chart-card-title">📈 Uploads by Month</h3>
                  <div className="chart-card-subtitle">Last 12 calendar months</div>
                </div>
              </div>
              <div className="chart-container">
                {uploadsByMonth.length === 0 ? (
                  <div className="chart-empty">
                    <span className="chart-empty-icon">📂</span>
                    <span>No upload records in the past 12 months</span>
                  </div>
                ) : (
                  <ResponsiveContainer width="100%" height={300}>
                    <BarChart
                      data={uploadsByMonth}
                      margin={{ top: 10, right: 10, left: -20, bottom: 20 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                      <XAxis
                        dataKey="label"
                        tick={{ fontSize: 11, fill: "#64748b" }}
                        angle={-30}
                        textAnchor="end"
                        interval={0}
                      />
                      <YAxis
                        allowDecimals={false}
                        tick={{ fontSize: 11, fill: "#64748b" }}
                      />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0f172a",
                          color: "#f8fafc",
                          borderRadius: 8,
                          border: "none",
                          boxShadow: "0 10px 15px -3px rgba(0,0,0,0.1)",
                        }}
                      />
                      <Legend />
                      <Bar
                        dataKey="count"
                        name="Uploads"
                        fill="#1D3B66"
                        radius={[4, 4, 0, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            {/* Chart 2: Documents by Category (Donut) */}
            <div className="chart-card" data-testid="chart-categories">
              <div className="chart-card-header">
                <div>
                  <h3 className="chart-card-title">📑 Documents by Category</h3>
                  <div className="chart-card-subtitle">Distribution breakdown</div>
                </div>
              </div>
              <div className="chart-container">
                {categories.length === 0 || totalCategoryDocs === 0 ? (
                  <div className="chart-empty">
                    <span className="chart-empty-icon">🗂️</span>
                    <span>No categorized documents available</span>
                  </div>
                ) : (
                  <>
                    <ResponsiveContainer width="100%" height={200}>
                      <PieChart>
                        <Pie
                          data={categories}
                          dataKey="count"
                          nameKey="category"
                          cx="50%"
                          cy="50%"
                          innerRadius={50}
                          outerRadius={80}
                          paddingAngle={3}
                        >
                          {categories.map((entry, index) => (
                            <Cell
                              key={`cell-${index}`}
                              fill={CATEGORY_COLORS[index % CATEGORY_COLORS.length]}
                            />
                          ))}
                        </Pie>
                        <Tooltip
                          contentStyle={{
                            backgroundColor: "#0f172a",
                            color: "#f8fafc",
                            borderRadius: 8,
                            border: "none",
                          }}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                    <div className="category-summary-list">
                      {categories.map((cat, idx) => (
                        <div key={idx} className="category-summary-item">
                          <div className="category-name-group">
                            <span
                              className="category-color-dot"
                              style={{
                                backgroundColor:
                                  CATEGORY_COLORS[idx % CATEGORY_COLORS.length],
                              }}
                            />
                            <span>{cat.category || "Uncategorized"}</span>
                          </div>
                          <span className="category-count">
                            {cat.count}{" "}
                            <small style={{ color: "#94a3b8", fontWeight: 400 }}>
                              (
                              {totalCategoryDocs > 0
                                ? ((cat.count / totalCategoryDocs) * 100).toFixed(1)
                                : 0}
                              %)
                            </small>
                          </span>
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>

          {/* Charts Row 2: Approvals by Month */}
          <div className="chart-card" style={{ marginBottom: "28px" }} data-testid="chart-approvals">
            <div className="chart-card-header">
              <div>
                <h3 className="chart-card-title">⚖️ Approvals & Rejections by Month</h3>
                <div className="chart-card-subtitle">
                  Workflow decisions over the last 12 months
                </div>
              </div>
            </div>
            <div className="chart-container">
              {approvalsByMonth.length === 0 ? (
                <div className="chart-empty">
                  <span className="chart-empty-icon">📊</span>
                  <span>No approval activity in the past 12 months</span>
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart
                    data={approvalsByMonth}
                    margin={{ top: 10, right: 10, left: -20, bottom: 20 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                    <XAxis
                      dataKey="label"
                      tick={{ fontSize: 11, fill: "#64748b" }}
                      angle={-30}
                      textAnchor="end"
                      interval={0}
                    />
                    <YAxis
                      allowDecimals={false}
                      tick={{ fontSize: 11, fill: "#64748b" }}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#0f172a",
                        color: "#f8fafc",
                        borderRadius: 8,
                        border: "none",
                      }}
                    />
                    <Legend />
                    <Bar
                      dataKey="approved"
                      name="Approved"
                      fill="#10b981"
                      radius={[4, 4, 0, 0]}
                    />
                    <Bar
                      dataKey="rejected"
                      name="Rejected"
                      fill="#ef4444"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* Recent Activity Feed */}
          <div className="activity-card" data-testid="activity-feed">
            <div className="chart-card-header" style={{ marginBottom: 0 }}>
              <div>
                <h3 className="chart-card-title">🕒 Recent Activity</h3>
                <div className="chart-card-subtitle">
                  Last 10 recorded actions matching your access scope
                </div>
              </div>
            </div>

            {recentActivity.length === 0 ? (
              <div className="chart-empty" style={{ height: "180px" }}>
                <span className="chart-empty-icon">📝</span>
                <span>No recent activity logged</span>
              </div>
            ) : (
              <div className="activity-table-wrapper">
                <table className="activity-table">
                  <thead>
                    <tr>
                      <th>Action</th>
                      <th>User</th>
                      <th>Target</th>
                      <th>Details</th>
                      <th>IP Address</th>
                      <th>Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentActivity.map((act) => (
                      <tr key={act.id}>
                        <td>
                          <span className={getActionBadgeClass(act.action)}>
                            {act.action}
                          </span>
                        </td>
                        <td>
                          <strong>{act.userName || "System"}</strong>
                          <div style={{ fontSize: "11px", color: "#64748b" }}>
                            {act.userEmail || "—"}
                          </div>
                        </td>
                        <td>
                          {act.entityType} {act.entityId ? `#${act.entityId}` : ""}
                        </td>
                        <td>{act.details || "—"}</td>
                        <td>
                          <code>{act.ipAddress || "—"}</code>
                        </td>
                        <td className="timestamp-cell">
                          {formatDate(act.timestamp)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
