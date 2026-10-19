import React, { useEffect, useState } from "react";
import {
  fetchAdminUsers,
  updateAdminUserRoles,
  updateAdminUserEnabled,
  deleteUser,
} from "../services/api";
import "./Admin.css";

const AVAILABLE_ROLES = ["ADMIN", "EMPLOYEE", "REVIEWER", "VERIFIER"];

export default function Admin() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const response = await fetchAdminUsers();
      setUsers(Array.isArray(response.data) ? response.data : []);
      setError(null);
    } catch (err) {
      console.error(err);
      setError("Failed to load users from the server.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleRoleChange = async (userId, newRole) => {
    setError(null);
    setSuccess(null);
    setActionLoadingId(userId);

    try {
      const response = await updateAdminUserRoles(userId, [newRole]);
      const updated = response.data;
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, role: updated.role, roles: updated.roles } : u))
      );
      setSuccess(`Role updated to ${newRole} for user ID ${userId}. Takes effect on next login.`);
    } catch (err) {
      const msg = err.response?.data?.message || "Failed to update user role.";
      setError(msg);
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleToggleEnabled = async (userId, currentStatus) => {
    setError(null);
    setSuccess(null);
    setActionLoadingId(userId);

    const targetStatus = !currentStatus;
    try {
      const response = await updateAdminUserEnabled(userId, targetStatus);
      const updated = response.data;
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, enabled: updated.enabled } : u))
      );
      setSuccess(`User ID ${userId} has been ${targetStatus ? "enabled" : "disabled"}.`);
    } catch (err) {
      const msg = err.response?.data?.message || "Failed to update user status.";
      setError(msg);
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleDelete = async (userId) => {
    if (!window.confirm(`Are you sure you want to permanently delete user ID ${userId}?`)) {
      return;
    }

    setError(null);
    setSuccess(null);
    try {
      await deleteUser(userId);
      setUsers((prev) => prev.filter((u) => u.id !== userId));
      setSuccess(`User ID ${userId} deleted successfully.`);
    } catch (err) {
      const msg = err.response?.data?.message || "Failed to delete user.";
      setError(msg);
    }
  };

  const getPrimaryRole = (user) => {
    if (user.roles && user.roles.length > 0) {
      const r = Array.isArray(user.roles) ? user.roles[0] : Array.from(user.roles)[0];
      return r.replace("ROLE_", "");
    }
    if (user.role) {
      return user.role.replace("ROLE_", "");
    }
    return "EMPLOYEE";
  };

  return (
    <div style={{ maxWidth: "1100px", margin: "30px auto", padding: "0 20px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
        <div>
          <h1 style={{ fontSize: "28px", fontWeight: "bold", color: "#1f2937", margin: "0" }}>
            👥 User Management
          </h1>
          <p style={{ color: "#6b7280", margin: "5px 0 0 0" }}>
            Administer system roles, permissions, and account status
          </p>
        </div>
        <button
          onClick={loadUsers}
          style={{
            padding: "8px 16px",
            background: "var(--navy-700, #1D3B66)",
            color: "white",
            border: "none",
            borderRadius: "6px",
            cursor: "pointer",
            fontWeight: "500",
          }}
        >
          🔄 Refresh
        </button>
      </div>

      {error && (
        <div
          style={{
            background: "#fee2e2",
            border: "1px solid #f87171",
            color: "#b91c1c",
            padding: "12px 16px",
            borderRadius: "8px",
            marginBottom: "20px",
            fontWeight: "500",
          }}
        >
          ⚠️ {error}
        </div>
      )}

      {success && (
        <div
          style={{
            background: "#ecfdf5",
            border: "1px solid #34d399",
            color: "#065f46",
            padding: "12px 16px",
            borderRadius: "8px",
            marginBottom: "20px",
            fontWeight: "500",
          }}
        >
          ✅ {success}
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: "center", padding: "40px", color: "#6b7280" }}>Loading user accounts...</div>
      ) : (
        <div
          style={{
            background: "#fff",
            borderRadius: "10px",
            boxShadow: "0 2px 10px rgba(0,0,0,0.05)",
            overflow: "hidden",
            border: "1px solid #e5e7eb",
          }}
        >
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
            <thead>
              <tr style={{ background: "#f9fafb", borderBottom: "1px solid #e5e7eb", color: "#4b5563" }}>
                <th style={{ padding: "14px 16px", fontWeight: "600", fontSize: "14px" }}>ID</th>
                <th style={{ padding: "14px 16px", fontWeight: "600", fontSize: "14px" }}>Name / Username</th>
                <th style={{ padding: "14px 16px", fontWeight: "600", fontSize: "14px" }}>Email</th>
                <th style={{ padding: "14px 16px", fontWeight: "600", fontSize: "14px" }}>Role</th>
                <th style={{ padding: "14px 16px", fontWeight: "600", fontSize: "14px" }}>Status</th>
                <th style={{ padding: "14px 16px", fontWeight: "600", fontSize: "14px" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const primary = getPrimaryRole(u);
                const isActionBusy = actionLoadingId === u.id;

                return (
                  <tr key={u.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                    <td style={{ padding: "14px 16px", color: "#6b7280", fontWeight: "500" }}>#{u.id}</td>
                    <td style={{ padding: "14px 16px", fontWeight: "600", color: "#111827" }}>
                      {u.name || u.username}
                    </td>
                    <td style={{ padding: "14px 16px", color: "#4b5563" }}>{u.email}</td>
                    <td style={{ padding: "14px 16px" }}>
                      <select
                        value={primary}
                        disabled={isActionBusy}
                        onChange={(e) => handleRoleChange(u.id, e.target.value)}
                        style={{
                          padding: "6px 10px",
                          borderRadius: "6px",
                          border: "1px solid #d1d5db",
                          background: "#fff",
                          fontWeight: "500",
                          cursor: "pointer",
                        }}
                      >
                        {AVAILABLE_ROLES.map((role) => (
                          <option key={role} value={role}>
                            {role}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      <span
                        style={{
                          display: "inline-block",
                          padding: "3px 10px",
                          borderRadius: "12px",
                          fontSize: "12px",
                          fontWeight: "600",
                          background: u.enabled ? "#d1fae5" : "#fee2e2",
                          color: u.enabled ? "#065f46" : "#991b1b",
                        }}
                      >
                        {u.enabled ? "ACTIVE" : "DISABLED"}
                      </span>
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      <div style={{ display: "flex", gap: "8px" }}>
                        <button
                          onClick={() => handleToggleEnabled(u.id, u.enabled)}
                          disabled={isActionBusy}
                          style={{
                            padding: "6px 12px",
                            border: "none",
                            borderRadius: "6px",
                            cursor: "pointer",
                            fontSize: "12px",
                            fontWeight: "500",
                            background: u.enabled ? "#fef3c7" : "#d1fae5",
                            color: u.enabled ? "#92400e" : "#065f46",
                          }}
                        >
                          {u.enabled ? "Disable" : "Enable"}
                        </button>
                        <button
                          onClick={() => handleDelete(u.id)}
                          disabled={isActionBusy}
                          style={{
                            padding: "6px 12px",
                            border: "none",
                            borderRadius: "6px",
                            cursor: "pointer",
                            fontSize: "12px",
                            fontWeight: "500",
                            background: "#fee2e2",
                            color: "#dc2626",
                          }}
                        >
                          Delete
                        </button>
                      </div>
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
