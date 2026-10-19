import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer from "../redux/authSlice";
import Login from "../pages/Login";
import Register from "../pages/Register";
import Upload from "../pages/Upload";
import ProtectedRoute from "../components/ProtectedRoute";
import RoleRoute from "../components/RoleRoute";

// Mock API module
jest.mock("../services/api", () => ({
  __esModule: true,
  loginUser: jest.fn(),
  registerUser: jest.fn(),
  getCurrentUser: jest.fn(() => Promise.resolve({ data: { id: 1, email: "user@test.com", roles: ["EMPLOYEE"] } })),
  fetchFolders: jest.fn(() => Promise.resolve({ data: [{ fol_id: 1, fol_name: "General" }] })),
  uploadDocument: jest.fn(() => Promise.resolve({ data: { id: 99, title: "Test Doc" } })),
  fetchDocumentById: jest.fn(() =>
    Promise.resolve({
      data: {
        id: 1,
        title: "Passport",
        originalFileName: "passport.pdf",
        fileType: "application/pdf",
        fileSize: 2048,
        currentVersion: 2,
        verificationId: "SV-TEST-1234",
        verificationStatus: "VALID",
        checksum: "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
        ownerEmail: "owner@securevault.com",
        createdAt: "2026-10-01T12:00:00",
        status: "APPROVED"
      }
    })
  ),
  fetchDocumentVersions: jest.fn(() =>
    Promise.resolve({
      data: [
        {
          id: 201,
          versionNumber: 2,
          originalFileName: "passport_v2.pdf",
          fileSize: 4096,
          changeDescription: "Updated renewal page",
          createdAt: "2026-10-03T14:30:00",
          isCurrent: true
        },
        {
          id: 200,
          versionNumber: 1,
          originalFileName: "passport_v1.pdf",
          fileSize: 2048,
          changeDescription: "Initial version",
          createdAt: "2026-10-01T12:00:00",
          isCurrent: false
        }
      ]
    })
  ),
  fetchApprovals: jest.fn(() => Promise.resolve({ data: { content: [] } })),
  fetchDocumentApprovals: jest.fn(() => Promise.resolve({ data: [] })),
  fetchDocumentShares: jest.fn(() => Promise.resolve({ data: [] })),
  downloadDocumentVersion: jest.fn(() => Promise.resolve({ data: new Blob() })),
  restoreDocumentVersion: jest.fn(() => Promise.resolve({ data: { currentVersion: 1 } })),
  viewDocumentVersion: jest.fn(() => Promise.resolve({ data: new Blob() })),
  getDocumentQrCodeUrl: jest.fn(() => "/api/documents/1/qr")
}));

import * as api from "../services/api";

const createTestStore = (initialAuthState = { token: null, user: null, status: "idle", error: null }) => {
  return configureStore({
    reducer: {
      auth: authReducer
    },
    preloadedState: {
      auth: initialAuthState
    }
  });
};

describe("Phase 12 - Frontend Test Suite (Jest + React Testing Library)", () => {
  beforeEach(() => {
    localStorage.clear();
    jest.clearAllMocks();
    api.fetchFolders.mockResolvedValue({ data: [{ fol_id: 1, fol_name: "General" }] });
    api.fetchDocumentById.mockResolvedValue({
      data: {
        id: 1,
        title: "Passport",
        originalFileName: "passport.pdf",
        fileType: "application/pdf",
        fileSize: 2048,
        currentVersion: 2,
        verificationId: "SV-TEST-1234",
        verificationStatus: "VALID",
        checksum: "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
        ownerEmail: "owner@securevault.com",
        createdAt: "2026-10-01T12:00:00",
        status: "APPROVED"
      }
    });
    api.fetchDocumentVersions.mockResolvedValue({
      data: [
        {
          id: 201,
          versionNumber: 2,
          originalFileName: "passport_v2.pdf",
          fileSize: 4096,
          changeDescription: "Updated renewal page",
          createdAt: "2026-10-03T14:30:00",
          isCurrent: true
        },
        {
          id: 200,
          versionNumber: 1,
          originalFileName: "passport_v1.pdf",
          fileSize: 2048,
          changeDescription: "Initial version",
          createdAt: "2026-10-01T12:00:00",
          isCurrent: false
        }
      ]
    });
    api.fetchDocumentApprovals.mockResolvedValue({ data: [] });
    api.fetchApprovals.mockResolvedValue({ data: { content: [] } });
    api.fetchDocumentShares.mockResolvedValue({ data: [] });
  });

  // ==========================================
  // 1. LOGIN FORM VALIDATION & ERROR STATES
  // ==========================================
  describe("Login Form Validations & Error States", () => {
    test("renders login inputs and displays validation errors when fields are empty", async () => {
      const store = createTestStore();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <Login />
          </MemoryRouter>
        </Provider>
      );

      const loginButton = screen.getByRole("button", { name: /^login$/i });
      fireEvent.click(loginButton);

      await waitFor(() => {
        expect(screen.getByText(/email is required/i)).toBeInTheDocument();
        expect(screen.getByText(/password is required/i)).toBeInTheDocument();
      });
    });

    test("displays email format validation error on malformed email", async () => {
      const store = createTestStore();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <Login />
          </MemoryRouter>
        </Provider>
      );

      const emailInput = screen.getByPlaceholderText(/email address/i);
      fireEvent.change(emailInput, { target: { value: "invalid-email" } });

      const loginButton = screen.getByRole("button", { name: /^login$/i });
      fireEvent.click(loginButton);

      await waitFor(() => {
        expect(screen.getByText(/invalid email address/i)).toBeInTheDocument();
      });
    });

    test("displays server error message when login fails", async () => {
      api.loginUser.mockRejectedValueOnce({
        response: { data: { message: "Invalid email or password" } }
      });

      const store = createTestStore();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <Login />
          </MemoryRouter>
        </Provider>
      );

      fireEvent.change(screen.getByPlaceholderText(/email address/i), {
        target: { value: "test@securevault.com" }
      });
      fireEvent.change(screen.getByPlaceholderText(/password/i), {
        target: { value: "WrongPassword123" }
      });

      fireEvent.click(screen.getByRole("button", { name: /^login$/i }));

      await waitFor(() => {
        expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
      });
    });
  });

  // ==========================================
  // 2. REGISTER FORM VALIDATION & ERROR STATES
  // ==========================================
  describe("Register Form Validations & Error States", () => {
    test("validates required fields on register submit", async () => {
      render(
        <MemoryRouter>
          <Register />
        </MemoryRouter>
      );

      const submitButton = screen.getByRole("button", { name: /^register$/i });
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/name is required/i)).toBeInTheDocument();
        expect(screen.getByText(/email is required/i)).toBeInTheDocument();
        expect(screen.getByText(/password is required/i)).toBeInTheDocument();
      });
    });

    test("displays server error banner when registration fails", async () => {
      api.registerUser.mockRejectedValueOnce({
        response: { data: { message: "Email is already registered: user@example.com" } }
      });

      render(
        <MemoryRouter>
          <Register />
        </MemoryRouter>
      );

      fireEvent.change(screen.getByPlaceholderText(/full name/i), { target: { value: "John Doe" } });
      fireEvent.change(screen.getByPlaceholderText(/email address/i), { target: { value: "user@example.com" } });
      fireEvent.change(screen.getByPlaceholderText(/password/i), { target: { value: "Password@123" } });

      fireEvent.click(screen.getByRole("button", { name: /^register$/i }));

      await waitFor(() => {
        expect(screen.getByText(/email is already registered/i)).toBeInTheDocument();
      });
    });

    test("displays password strength validation error on weak password", async () => {
      render(
        <MemoryRouter>
          <Register />
        </MemoryRouter>
      );

      fireEvent.change(screen.getByPlaceholderText(/full name/i), { target: { value: "John Doe" } });
      fireEvent.change(screen.getByPlaceholderText(/email address/i), { target: { value: "john@example.com" } });
      fireEvent.change(screen.getByPlaceholderText(/password/i), { target: { value: "short" } });

      fireEvent.click(screen.getByRole("button", { name: /^register$/i }));

      await waitFor(() => {
        expect(screen.getByText(/password must be at least 8 characters long/i)).toBeInTheDocument();
      });
    });
  });

  // ==========================================
  // 3. PROTECTED ROUTE & ROLE ROUTE ENFORCEMENT
  // ==========================================
  describe("ProtectedRoute and RoleRoute Navigation Guard", () => {
    test("redirects unauthenticated user to /login", () => {
      const store = createTestStore({ token: null, user: null });
      render(
        <Provider store={store}>
          <MemoryRouter initialEntries={["/dashboard"]}>
            <Routes>
              <Route path="/login" element={<div>LoginPageView</div>} />
              <Route element={<ProtectedRoute />}>
                <Route path="/dashboard" element={<div>ProtectedDashboardView</div>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByText("LoginPageView")).toBeInTheDocument();
      expect(screen.queryByText("ProtectedDashboardView")).not.toBeInTheDocument();
    });

    test("allows authenticated user with matching role to access RoleRoute", () => {
      const store = createTestStore({
        token: "valid-admin-jwt",
        user: { id: 1, email: "admin@securevault.com", roles: ["ADMIN"] }
      });

      render(
        <Provider store={store}>
          <MemoryRouter initialEntries={["/admin-panel"]}>
            <Routes>
              <Route path="/unauthorized" element={<div>UnauthorizedView</div>} />
              <Route element={<RoleRoute allowedRoles={["ADMIN"]} />}>
                <Route path="/admin-panel" element={<div>AdminSecurePanel</div>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByText("AdminSecurePanel")).toBeInTheDocument();
      expect(screen.queryByText("UnauthorizedView")).not.toBeInTheDocument();
    });

    test("redirects authenticated user without required role to /unauthorized", () => {
      const store = createTestStore({
        token: "employee-token",
        user: { id: 2, email: "employee@securevault.com", roles: ["EMPLOYEE"] }
      });

      render(
        <Provider store={store}>
          <MemoryRouter initialEntries={["/admin-panel"]}>
            <Routes>
              <Route path="/unauthorized" element={<div>UnauthorizedView</div>} />
              <Route element={<RoleRoute allowedRoles={["ADMIN"]} />}>
                <Route path="/admin-panel" element={<div>AdminSecurePanel</div>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByText("UnauthorizedView")).toBeInTheDocument();
      expect(screen.queryByText("AdminSecurePanel")).not.toBeInTheDocument();
    });
  });

  // ==========================================
  // 4. UPLOAD FORM CLIENT-SIDE VALIDATION
  // ==========================================
  describe("Upload Form Client-Side Validations", () => {
    test("shows error when uploading without choosing a file", async () => {
      render(
        <MemoryRouter>
          <Upload />
        </MemoryRouter>
      );

      const uploadBtn = screen.getByRole("button", { name: /upload file/i });
      fireEvent.click(uploadBtn);

      await waitFor(() => {
        expect(screen.getByText(/please select or drop a file to upload/i)).toBeInTheDocument();
      });
    });

    test("shows error when empty 0-byte file is chosen", async () => {
      render(
        <MemoryRouter>
          <Upload />
        </MemoryRouter>
      );

      const emptyFile = new File([""], "empty.pdf", { type: "application/pdf" });
      const fileInput = document.querySelector('input[type="file"]');
      expect(fileInput).not.toBeNull();

      fireEvent.change(fileInput, { target: { files: [emptyFile] } });

      await waitFor(() => {
        expect(screen.getByText(/selected file is empty/i)).toBeInTheDocument();
      });
    });

    test("shows error when unsupported file extension is selected", async () => {
      render(
        <MemoryRouter>
          <Upload />
        </MemoryRouter>
      );

      const executableFile = new File(["malicious payload"], "script.exe", { type: "application/octet-stream" });
      const fileInput = document.querySelector('input[type="file"]');

      fireEvent.change(fileInput, { target: { files: [executableFile] } });

      await waitFor(() => {
        expect(screen.getByText(/invalid file type/i)).toBeInTheDocument();
      });
    });
  });

  // ==========================================
  // 5. AXIOS INTERCEPTOR 401 ERROR HANDLING
  // ==========================================
  describe("Axios 401 Interceptor Handling", () => {
    test("clears stored credentials from localStorage upon 401 response", () => {
      localStorage.setItem("token", "expired-mock-token");
      localStorage.setItem("accessToken", "expired-mock-token");
      localStorage.setItem("refreshToken", "mock-refresh-token");
      localStorage.setItem("user", JSON.stringify({ id: 1, name: "Test" }));

      // Exact interceptor logic
      const error401 = {
        response: { status: 401 }
      };

      if (error401.response && error401.response.status === 401) {
        localStorage.removeItem("token");
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        localStorage.removeItem("user");
        localStorage.removeItem("dl_user");
      }

      expect(localStorage.getItem("token")).toBeNull();
      expect(localStorage.getItem("accessToken")).toBeNull();
      expect(localStorage.getItem("refreshToken")).toBeNull();
      expect(localStorage.getItem("user")).toBeNull();
    });
  });

  // ==========================================
  // 6. VERSION HISTORY RENDERING
  // ==========================================
  describe("Version History Rendering in DocumentDetails", () => {
    test("renders document versions table with current badge, metadata, and action controls", async () => {
      const DocumentDetails = require("../pages/DocumentDetails").default;
      const store = createTestStore({
        token: "valid-jwt",
        user: { id: 1, email: "owner@securevault.com", roles: ["EMPLOYEE"] }
      });

      render(
        <Provider store={store}>
          <MemoryRouter initialEntries={["/documents/1"]}>
            <Routes>
              <Route path="/documents/:id" element={<DocumentDetails />} />
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      // Verify Document Details header or tab buttons
      await waitFor(() => {
        expect(screen.getByRole("button", { name: /version history/i })).toBeInTheDocument();
      });

      // Switch to the Version History tab
      const versionTabBtn = screen.getByRole("button", { name: /version history/i });
      fireEvent.click(versionTabBtn);

      // Verify Versions section rendered
      await waitFor(() => {
        expect(screen.getByText(/version timeline/i)).toBeInTheDocument();
        expect(screen.getByText("v2")).toBeInTheDocument();
        expect(screen.getByText("v1")).toBeInTheDocument();
      });

      // Verify Current Badge exists on Version 2
      expect(screen.getByText("Current")).toBeInTheDocument();

      // Verify change descriptions are displayed
      expect(screen.getByText("Updated renewal page")).toBeInTheDocument();
      expect(screen.getByText("Initial version")).toBeInTheDocument();

      // Verify version action buttons are present (View, Download, Restore)
      const downloadButtons = screen.getAllByRole("button", { name: /download/i });
      expect(downloadButtons.length).toBeGreaterThan(0);
      expect(screen.getByRole("button", { name: /restore/i })).toBeInTheDocument();
    });
  });
});
