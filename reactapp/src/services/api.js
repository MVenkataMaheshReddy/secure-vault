import axios from "axios";

const API_BASE = process.env.REACT_APP_API_BASE_URL || "http://localhost:8080";

// Axios instance
const http = axios.create({
  baseURL: API_BASE,
  headers: { "Content-Type": "application/json" },
});

// Request interceptor: Attach JWT token to every request
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token") || localStorage.getItem("accessToken");
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Logout and redirect to /login on 401 Unauthorized
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Clear stored credentials
      localStorage.removeItem("token");
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("user");
      localStorage.removeItem("dl_user");

      // Redirect to /login if not already on /login or /register
      if (
        typeof window !== "undefined" &&
        window.location.pathname !== "/login" &&
        window.location.pathname !== "/register"
      ) {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

// ==================== AUTH ====================
export const loginUser = (credentials) => http.post("/api/auth/login", credentials);
export const registerUser = (userData) => http.post("/api/auth/register", userData);
export const getCurrentUser = () => http.get("/api/auth/me");
export const logoutUser = () => {
  const token = localStorage.getItem("token") || localStorage.getItem("accessToken");
  return http.post(
    "/api/auth/logout",
    {},
    token ? { headers: { Authorization: `Bearer ${token}` } } : {}
  );
};
export const refreshToken = (refreshToken) => http.post("/api/auth/refresh", { refreshToken });

export default http;

// ==================== USERS ====================
export const createUser = (userData) => http.post("/users/create", userData);
export const fetchUsers = () => http.get("/users/fetch");
export const updateUser = (id, updatedData) => http.put(`/users/update/${id}`, updatedData);
export const deleteUser = (id) => http.delete(`/users/delete/${id}`);

// ==================== FOLDERS ====================
export const fetchFolders = () => http.get("/folders/fetch");
export const createFolder = (folder) => http.post("/folders/create", folder);
export const updateFolder = (id, updatedData) => http.put(`/folders/update/${id}`, updatedData);
export const deleteFolder = (id) => http.delete(`/folders/delete/${id}`);

// ==================== DOCUMENTS ====================
export const uploadDocument = (formData, config = {}) =>
  http.post("/api/documents", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    ...config,
  });
export const fetchDocumentsPaginated = (params = {}) =>
  http.get("/api/documents", { params });
export const fetchDocumentsMetadata = () => http.get("/documents");
export const downloadDocument = (id) =>
  http.get(`/api/documents/${id}/download`, { responseType: "blob" });
export const viewDocument = (id) =>
  http.get(`/api/documents/${id}/view`, { responseType: "blob" });
export const fetchDocumentById = (id) => http.get(`/api/documents/${id}`);
export const updateDocument = (id, data) => http.put(`/api/documents/${id}`, data);
export const deleteDocument = (id) => http.delete(`/api/documents/${id}`);
export const fetchTrashDocuments = () => http.get("/api/documents/trash");
export const restoreDocument = (id) => http.post(`/api/documents/${id}/restore`);

// ==================== DOCUMENT VERSIONS (PHASE 5) ====================
export const fetchDocumentVersions = (documentId) =>
  http.get(`/api/documents/${documentId}/versions`);
export const fetchDocumentVersionMetadata = (documentId, versionId) =>
  http.get(`/api/documents/${documentId}/versions/${versionId}`);
export const uploadDocumentVersion = (documentId, formData, config = {}) =>
  http.post(`/api/documents/${documentId}/versions`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    ...config,
  });
export const downloadDocumentVersion = (documentId, versionId) =>
  http.get(`/api/documents/${documentId}/versions/${versionId}/download`, { responseType: "blob" });
export const viewDocumentVersion = (documentId, versionId) =>
  http.get(`/api/documents/${documentId}/versions/${versionId}/view`, { responseType: "blob" });
export const restoreDocumentVersion = (documentId, versionId) =>
  http.post(`/api/documents/${documentId}/versions/${versionId}/restore`);

// ==================== ACTIVITY LOGS ====================
export const createActivityLog = (log) => http.post("/activity-logs/create", log);
export const fetchActivityLogs = () => http.get("/activity-logs/fetch");
export const deleteActivityLog = (id) => http.delete(`/activity-logs/delete/${id}`);

// ==================== ADMIN USER MANAGEMENT ====================
export const fetchAdminUsers = () => http.get("/api/admin/users");
export const fetchAdminUserById = (id) => http.get(`/api/admin/users/${id}`);
export const updateAdminUserRoles = (id, roles) =>
  http.put(`/api/admin/users/${id}/roles`, { roles });
export const updateAdminUserEnabled = (id, enabled) =>
  http.put(`/api/admin/users/${id}/enabled`, { enabled });

// ==================== ANALYTICS (PHASE 10) ====================
export const fetchAnalytics = () => http.get("/api/analytics");
export const fetchAnalyticsSummary = () => http.get("/api/analytics/summary");
export const fetchUploadsByMonth = () => http.get("/api/analytics/uploads-by-month");
export const fetchApprovalsByMonth = () => http.get("/api/analytics/approvals-by-month");
export const fetchDocumentsByCategory = () => http.get("/api/analytics/documents-by-category");
export const fetchRecentActivity = () => http.get("/api/analytics/recent-activity");

// ==================== SEARCH & OCR (PHASE 6) ====================
export const searchDocuments = (params = {}) =>
  http.get("/api/documents/search", { params });

export const retryOcr = (documentId) =>
  http.post(`/api/documents/${documentId}/ocr/retry`);

// ==================== QR VERIFICATION (PHASE 7) ====================
export const getPublicVerification = (verificationId) =>
  http.get(`/api/verify/${verificationId}`);

export const checkFileAuthenticity = (verificationId, formData, config = {}) =>
  http.post(`/api/verify/${verificationId}/check-file`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    ...config,
  });

export const generateVerification = (documentId) =>
  http.post(`/api/documents/${documentId}/verification`);

export const revokeVerification = (documentId) =>
  http.put(`/api/documents/${documentId}/verification/revoke`);

export const fetchVerificationRecords = () =>
  http.get("/api/verify/records");

export const getDocumentQrCodeBlob = (documentId) =>
  http.get(`/api/documents/${documentId}/qr`, { responseType: "blob" });

export const getDocumentQrCodeUrl = (documentId) =>
  `${API_BASE}/api/documents/${documentId}/qr`;

// ==================== APPROVAL WORKFLOW (PHASE 8) ====================
export const fetchApprovals = (params = {}) =>
  http.get("/api/approvals", { params });

export const fetchApprovalById = (id) =>
  http.get(`/api/approvals/${id}`);

export const approveApproval = (id, comments = "") =>
  http.post(`/api/approvals/${id}/approve`, { comments });

export const rejectApproval = (id, comments) =>
  http.post(`/api/approvals/${id}/reject`, { comments });

export const fetchDocumentApprovals = (documentId) =>
  http.get(`/api/documents/${documentId}/approvals`);

export const resubmitDocument = (documentId) =>
  http.post(`/api/documents/${documentId}/resubmit`);

// ==================== ADMIN APPROVAL WORKFLOW ====================
export const fetchAdminApprovals = (params = {}) =>
  http.get("/api/admin/approvals", { params });

export const fetchAdminApprovalById = (id) =>
  http.get(`/api/admin/approvals/${id}`);

export const approveAdminApproval = (id, comments = "") =>
  http.post(`/api/admin/approvals/${id}/approve`, { comments });

export const rejectAdminApproval = (id, comments) =>
  http.post(`/api/admin/approvals/${id}/reject`, { comments });

export const fetchPendingApprovalsCount = () =>
  http.get("/api/admin/approvals/pending-count");

// ==================== SHARING & AUDIT LOGS (PHASE 9) ====================
export const shareDocument = (documentId, data) =>
  http.post(`/api/documents/${documentId}/shares`, data);

export const fetchDocumentShares = (documentId) =>
  http.get(`/api/documents/${documentId}/shares`);

export const revokeDocumentShare = (documentId, shareId) =>
  http.delete(`/api/documents/${documentId}/shares/${shareId}`);

export const fetchSharedWithMe = (params = {}) =>
  http.get("/api/documents/shared-with-me", { params });

export const fetchAdminAuditLogs = (params = {}) =>
  http.get("/api/admin/audit-logs", { params });

export const exportAdminAuditLogs = (params = {}) =>
  http.get("/api/admin/audit-logs/export", { params, responseType: "blob" });


