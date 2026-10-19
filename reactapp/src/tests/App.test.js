import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import store from "../redux/store";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import Home from "../pages/Home";
import Documents from "../pages/Documents";
import Upload from "../pages/Upload";
import Login from "../pages/Login";
import Register from "../pages/Register";

jest.mock("../services/api", () => ({
  __esModule: true,
  fetchFolders: jest.fn(() => Promise.resolve({ data: [] })),
  fetchDocumentsMetadata: jest.fn(() => Promise.resolve({ data: [] })),
  fetchDocumentsPaginated: jest.fn(() => Promise.resolve({ data: { content: [], totalPages: 1, totalElements: 0 } })),
  fetchDocumentById: jest.fn(() => Promise.resolve({
    data: {
      id: 1,
      title: "Passport",
      originalFileName: "passport.pdf",
      currentVersion: 1,
      verificationId: "SV-A1B2C3D4",
      verificationStatus: "VALID",
      checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      ownerEmail: "owner@securevault.com"
    }
  })),
  fetchDocumentVersions: jest.fn(() => Promise.resolve({ data: [{ id: 10, versionNumber: 1, isCurrent: true, fileSize: 1024, changeDescription: "Initial" }] })),
  fetchTrashDocuments: jest.fn(() => Promise.resolve({ data: [] })),
  restoreDocument: jest.fn(() => Promise.resolve({ data: {} })),
  createFolder: jest.fn(() => Promise.resolve({ data: { fol_id: 1, fol_name: "Test" } })),
  uploadDocument: jest.fn(() => Promise.resolve({ data: {} })),
  loginUser: jest.fn(() => Promise.resolve({ data: { token: "fake-token", user: { id: 1, name: "Alice", email: "alice@example.com", roles: ["EMPLOYEE"] } } })),
  registerUser: jest.fn(() => Promise.resolve({ data: { id: 1, name: "Alice", email: "alice@example.com", roles: ["EMPLOYEE"] } })),
  getCurrentUser: jest.fn(() => Promise.resolve({ data: { id: 1, name: "Alice", email: "alice@example.com", roles: ["EMPLOYEE"] } })),
  searchDocuments: jest.fn(() => Promise.resolve({ data: { content: [], totalPages: 1, totalElements: 0 } })),
  retryOcr: jest.fn(() => Promise.resolve({ data: { message: "OCR initiated" } })),
  downloadDocument: jest.fn(() => Promise.resolve({ data: new Blob() })),
  getPublicVerification: jest.fn(() =>
    Promise.resolve({
      data: {
        verificationId: "SV-A1B2C3D4",
        documentTitle: "Degree Certificate",
        category: "ACADEMIC",
        ownerDisplayName: "John Doe",
        status: "VALID",
        issuedDate: "2026-10-04T10:00:00",
        currentVersionNumber: 1,
        checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      },
    })
  ),
  checkFileAuthenticity: jest.fn(() =>
    Promise.resolve({
      data: {
        verificationId: "SV-A1B2C3D4",
        matches: true,
        matchedVersion: 1,
        uploadedFileChecksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        currentVersionChecksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        status: "VALID",
        message: "Document copy is authentic!",
      },
    })
  ),
  generateVerification: jest.fn(() =>
    Promise.resolve({ data: { verificationId: "SV-A1B2C3D4", status: "VALID" } })
  ),
  revokeVerification: jest.fn(() =>
    Promise.resolve({ data: { verificationId: "SV-A1B2C3D4", status: "REVOKED" } })
  ),
  fetchVerificationRecords: jest.fn(() =>
    Promise.resolve({
      data: [
        {
          id: 1,
          documentId: 100,
          verificationId: "SV-A1B2C3D4",
          documentTitle: "Degree Certificate",
          category: "ACADEMIC",
          status: "VALID",
          currentVersion: 1,
          ownerName: "John Doe",
          createdAt: "2026-10-04T10:00:00",
        },
      ],
    })
  ),
  getDocumentQrCodeBlob: jest.fn(() => Promise.resolve({ data: new Blob() })),
  getDocumentQrCodeUrl: jest.fn((id) => `/api/documents/${id}/qr`),
  fetchApprovals: jest.fn(() =>
    Promise.resolve({
      data: {
        content: [
          {
            id: 1,
            documentId: 100,
            documentTitle: "Degree Certificate",
            category: "ACADEMIC",
            ownerName: "John Doe",
            ownerEmail: "john@example.com",
            version: 1,
            status: "PENDING",
            reviewerName: null,
            reviewerEmail: null,
            comments: "Initial submission",
            createdAt: "2026-10-04T10:00:00",
            updatedAt: "2026-10-04T10:00:00",
          },
        ],
        totalPages: 1,
        totalElements: 1,
      },
    })
  ),
  fetchApprovalById: jest.fn(() =>
    Promise.resolve({
      data: {
        id: 1,
        documentId: 100,
        documentTitle: "Degree Certificate",
        category: "ACADEMIC",
        ownerName: "John Doe",
        ownerEmail: "john@example.com",
        version: 1,
        status: "PENDING",
      },
    })
  ),
  approveApproval: jest.fn(() =>
    Promise.resolve({
      data: { id: 1, status: "APPROVED" },
    })
  ),
  rejectApproval: jest.fn(() =>
    Promise.resolve({
      data: { id: 1, status: "REJECTED" },
    })
  ),
  fetchDocumentApprovals: jest.fn(() =>
    Promise.resolve({
      data: [
        {
          id: 1,
          documentId: 100,
          status: "PENDING",
          reviewerName: null,
          comments: "Submitted for review",
          createdAt: "2026-10-04T10:00:00",
          version: 1,
        },
      ],
    })
  ),
  resubmitDocument: jest.fn(() =>
    Promise.resolve({
      data: { id: 100, status: "PENDING" },
    })
  ),
  shareDocument: jest.fn(() =>
    Promise.resolve({
      data: { id: 1, sharedWithEmail: "colleague@example.com", permission: "VIEW" },
    })
  ),
  fetchDocumentShares: jest.fn(() =>
    Promise.resolve({
      data: [
        {
          id: 1,
          documentId: 1,
          sharedWithEmail: "colleague@example.com",
          permission: "VIEW",
          expiresAt: null,
          createdAt: "2026-10-04T10:00:00",
        },
      ],
    })
  ),
  revokeDocumentShare: jest.fn(() => Promise.resolve({ data: {} })),
  fetchSharedWithMe: jest.fn(() =>
    Promise.resolve({
      data: [
        {
          shareId: 1,
          documentId: 10,
          title: "Shared Strategy Document",
          category: "LEGAL",
          description: "Confidential roadmap",
          ownerName: "Alice Owner",
          ownerEmail: "alice@example.com",
          permission: "VIEW",
          expiresAt: null,
          sharedAt: "2026-10-04T10:00:00",
          currentVersion: 2,
          fileSize: 2048,
          contentType: "application/pdf",
        },
        {
          shareId: 2,
          documentId: 11,
          title: "Financial Report",
          category: "FINANCIAL",
          description: "Q3 report",
          ownerName: "Bob Finance",
          ownerEmail: "bob@example.com",
          permission: "DOWNLOAD",
          expiresAt: "2026-12-31T23:59:59",
          sharedAt: "2026-10-04T11:00:00",
          currentVersion: 1,
          fileSize: 4096,
          contentType: "application/pdf",
        },
      ],
    })
  ),
  fetchAdminAuditLogs: jest.fn(() =>
    Promise.resolve({
      data: {
        content: [
          {
            id: 1,
            action: "UPLOAD",
            performedByEmail: "alice@example.com",
            entityType: "DOCUMENT",
            entityId: "100",
            ipAddress: "127.0.0.1",
            details: "Uploaded passport.pdf",
            timestamp: "2026-10-04T10:00:00",
          },
          {
            id: 2,
            action: "SHARE_CREATE",
            performedByEmail: "alice@example.com",
            entityType: "DOCUMENT",
            entityId: "100",
            ipAddress: "127.0.0.1",
            details: "Shared with colleague@example.com (permission: VIEW)",
            timestamp: "2026-10-04T10:05:00",
          },
        ],
        totalPages: 1,
        totalElements: 2,
        number: 0,
      },
    })
  ),
  exportAdminAuditLogs: jest.fn(() =>
    Promise.resolve({ data: new Blob(["timestamp,action\n2026-10-04,UPLOAD"]) })
  ),
  fetchAnalytics: jest.fn(() =>
    Promise.resolve({
      data: {
        scope: "SYSTEM_WIDE",
        summary: {
          totalDocuments: 150,
          pendingApprovals: 12,
          approvedDocuments: 110,
          rejectedDocuments: 15,
          verifiedDocuments: 85,
          activeUsers: 42,
          storageUsedBytes: 524288000,
        },
      },
    })
  ),
  fetchAnalyticsSummary: jest.fn(() =>
    Promise.resolve({
      data: {
        totalDocuments: 150,
        pendingApprovals: 12,
        approvedDocuments: 110,
        rejectedDocuments: 15,
        verifiedDocuments: 85,
        activeUsers: 42,
        storageUsedBytes: 524288000,
      },
    })
  ),
  fetchUploadsByMonth: jest.fn(() =>
    Promise.resolve({
      data: [
        { month: "2026-01", label: "Jan 2026", count: 20 },
        { month: "2026-02", label: "Feb 2026", count: 35 },
        { month: "2026-03", label: "Mar 2026", count: 48 },
      ],
    })
  ),
  fetchApprovalsByMonth: jest.fn(() =>
    Promise.resolve({
      data: [
        { month: "2026-01", label: "Jan 2026", approved: 18, rejected: 2 },
        { month: "2026-02", label: "Feb 2026", approved: 30, rejected: 5 },
        { month: "2026-03", label: "Mar 2026", approved: 42, rejected: 6 },
      ],
    })
  ),
  fetchDocumentsByCategory: jest.fn(() =>
    Promise.resolve({
      data: [
        { category: "FINANCIAL", count: 50 },
        { category: "LEGAL", count: 40 },
        { category: "ACADEMIC", count: 30 },
      ],
    })
  ),
  fetchRecentActivity: jest.fn(() =>
    Promise.resolve({
      data: [
        {
          id: 1,
          userId: 1,
          userName: "Alice Smith",
          userEmail: "alice@example.com",
          action: "UPLOAD",
          entityType: "DOCUMENT",
          entityId: 100,
          ipAddress: "192.168.1.10",
          details: "Uploaded contract.pdf",
          timestamp: "2026-10-04T12:00:00",
        },
        {
          id: 2,
          userId: 2,
          userName: "Bob Reviewer",
          userEmail: "bob@example.com",
          action: "APPROVE",
          entityType: "APPROVAL",
          entityId: 10,
          ipAddress: "192.168.1.11",
          details: "Approved degree certificate",
          timestamp: "2026-10-04T12:30:00",
        },
      ],
    })
  ),
  default: {},
}));

import * as api from "../services/api";

const renderWithProviders = (ui) => {
  return render(
    <Provider store={store}>
      <MemoryRouter>{ui}</MemoryRouter>
    </Provider>
  );
};

describe("SecureVaultFrontendTests", () => {
  beforeEach(() => {
    localStorage.clear();
    api.fetchFolders.mockResolvedValue({ data: [] });
    api.fetchDocumentsMetadata.mockResolvedValue({ data: [] });
    api.fetchDocumentsPaginated.mockResolvedValue({ data: { content: [], totalPages: 1, totalElements: 0 } });
    api.fetchDocumentById.mockResolvedValue({
      data: {
        id: 1,
        title: "Passport",
        originalFileName: "passport.pdf",
        currentVersion: 1,
        verificationId: "SV-A1B2C3D4",
        verificationStatus: "VALID",
        checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        ownerEmail: "owner@securevault.com"
      }
    });
    api.fetchDocumentVersions.mockResolvedValue({ data: [{ id: 10, versionNumber: 1, isCurrent: true, fileSize: 1024, changeDescription: "Initial" }] });
    api.fetchTrashDocuments.mockResolvedValue({ data: [] });
    api.restoreDocument.mockResolvedValue({ data: {} });
    api.createFolder.mockResolvedValue({ data: { fol_id: 1, fol_name: "Test" } });
    api.uploadDocument.mockResolvedValue({ data: {} });
    if (api.searchDocuments) {
      api.searchDocuments.mockResolvedValue({ data: { content: [], totalPages: 1, totalElements: 0 } });
    }
    if (api.retryOcr) {
      api.retryOcr.mockResolvedValue({ data: { message: "OCR initiated" } });
    }
    if (api.fetchVerificationRecords) {
      api.fetchVerificationRecords.mockResolvedValue({
        data: [
          {
            id: 1,
            documentId: 100,
            verificationId: "SV-A1B2C3D4",
            documentTitle: "Degree Certificate",
            category: "ACADEMIC",
            status: "VALID",
            currentVersion: 1,
            ownerName: "John Doe",
            createdAt: "2026-10-04T10:00:00",
          },
        ],
      });
    }
    if (api.getPublicVerification) {
      api.getPublicVerification.mockResolvedValue({
        data: {
          verificationId: "SV-A1B2C3D4",
          documentTitle: "Degree Certificate",
          category: "ACADEMIC",
          ownerDisplayName: "John Doe",
          status: "VALID",
          issuedDate: "2026-10-04T10:00:00",
          currentVersionNumber: 1,
          checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        },
      });
    }
    if (api.fetchApprovals) {
      api.fetchApprovals.mockResolvedValue({
        data: {
          content: [
            {
              id: 1,
              documentId: 100,
              documentTitle: "Degree Certificate",
              category: "ACADEMIC",
              ownerName: "John Doe",
              ownerEmail: "john@example.com",
              version: 1,
              status: "PENDING",
              reviewerName: null,
              reviewerEmail: null,
              comments: "Initial submission",
              createdAt: "2026-10-04T10:00:00",
              updatedAt: "2026-10-04T10:00:00",
            },
          ],
          totalPages: 1,
          totalElements: 1,
        },
      });
    }
    if (api.fetchApprovalById) {
      api.fetchApprovalById.mockResolvedValue({
        data: {
          id: 1,
          documentId: 100,
          documentTitle: "Degree Certificate",
          category: "ACADEMIC",
          ownerName: "John Doe",
          ownerEmail: "john@example.com",
          version: 1,
          status: "PENDING",
        },
      });
    }
    if (api.approveApproval) {
      api.approveApproval.mockResolvedValue({ data: { id: 1, status: "APPROVED" } });
    }
    if (api.rejectApproval) {
      api.rejectApproval.mockResolvedValue({ data: { id: 1, status: "REJECTED" } });
    }
    if (api.fetchDocumentApprovals) {
      api.fetchDocumentApprovals.mockResolvedValue({
        data: [
          {
            id: 1,
            documentId: 100,
            status: "PENDING",
            reviewerName: null,
            comments: "Submitted for review",
            createdAt: "2026-10-04T10:00:00",
            version: 1,
          },
        ],
      });
    }
    if (api.resubmitDocument) {
      api.resubmitDocument.mockResolvedValue({ data: { id: 100, status: "PENDING" } });
    }
    if (api.fetchDocumentShares) {
      api.fetchDocumentShares.mockResolvedValue({
        data: [
          {
            id: 1,
            documentId: 1,
            sharedWithEmail: "colleague@example.com",
            permission: "VIEW",
            expiresAt: null,
            createdAt: "2026-10-04T10:00:00",
          },
        ],
      });
    }
    if (api.shareDocument) {
      api.shareDocument.mockResolvedValue({
        data: { id: 1, sharedWithEmail: "colleague@example.com", permission: "VIEW" },
      });
    }
    if (api.revokeDocumentShare) {
      api.revokeDocumentShare.mockResolvedValue({ data: {} });
    }
    if (api.fetchSharedWithMe) {
      api.fetchSharedWithMe.mockResolvedValue({
        data: [
          {
            shareId: 1,
            documentId: 10,
            title: "Shared Strategy Document",
            category: "LEGAL",
            description: "Confidential roadmap",
            ownerName: "Alice Owner",
            ownerEmail: "alice@example.com",
            permission: "VIEW",
            expiresAt: null,
            sharedAt: "2026-10-04T10:00:00",
            currentVersion: 2,
            fileSize: 2048,
            contentType: "application/pdf",
          },
          {
            shareId: 2,
            documentId: 11,
            title: "Financial Report",
            category: "FINANCIAL",
            description: "Q3 report",
            ownerName: "Bob Finance",
            ownerEmail: "bob@example.com",
            permission: "DOWNLOAD",
            expiresAt: "2026-12-31T23:59:59",
            sharedAt: "2026-10-04T11:00:00",
            currentVersion: 1,
            fileSize: 4096,
            contentType: "application/pdf",
          },
        ],
      });
    }
    if (api.fetchAdminAuditLogs) {
      api.fetchAdminAuditLogs.mockResolvedValue({
        data: {
          content: [
            {
              id: 1,
              action: "UPLOAD",
              performedByEmail: "alice@example.com",
              entityType: "DOCUMENT",
              entityId: "100",
              ipAddress: "127.0.0.1",
              details: "Uploaded passport.pdf",
              timestamp: "2026-10-04T10:00:00",
            },
            {
              id: 2,
              action: "SHARE_CREATE",
              performedByEmail: "alice@example.com",
              entityType: "DOCUMENT",
              entityId: "100",
              ipAddress: "127.0.0.1",
              details: "Shared with colleague@example.com (permission: VIEW)",
              timestamp: "2026-10-04T10:05:00",
            },
          ],
          totalPages: 1,
          totalElements: 2,
          number: 0,
        },
      });
    }
    if (api.exportAdminAuditLogs) {
      api.exportAdminAuditLogs.mockResolvedValue({
        data: new Blob(["timestamp,action\n2026-10-04,UPLOAD"]),
      });
    }
    if (api.fetchAnalyticsSummary) {
      api.fetchAnalyticsSummary.mockResolvedValue({
        data: {
          totalDocuments: 150,
          pendingApprovals: 12,
          approvedDocuments: 110,
          rejectedDocuments: 15,
          verifiedDocuments: 85,
          activeUsers: 42,
          storageUsedBytes: 524288000,
        },
      });
    }
    if (api.fetchUploadsByMonth) {
      api.fetchUploadsByMonth.mockResolvedValue({
        data: [
          { month: "2026-01", label: "Jan 2026", count: 20 },
          { month: "2026-02", label: "Feb 2026", count: 35 },
          { month: "2026-03", label: "Mar 2026", count: 48 },
        ],
      });
    }
    if (api.fetchApprovalsByMonth) {
      api.fetchApprovalsByMonth.mockResolvedValue({
        data: [
          { month: "2026-01", label: "Jan 2026", approved: 18, rejected: 2 },
          { month: "2026-02", label: "Feb 2026", approved: 30, rejected: 5 },
          { month: "2026-03", label: "Mar 2026", approved: 42, rejected: 6 },
        ],
      });
    }
    if (api.fetchDocumentsByCategory) {
      api.fetchDocumentsByCategory.mockResolvedValue({
        data: [
          { category: "FINANCIAL", count: 50 },
          { category: "LEGAL", count: 40 },
          { category: "ACADEMIC", count: 30 },
        ],
      });
    }
    if (api.fetchRecentActivity) {
      api.fetchRecentActivity.mockResolvedValue({
        data: [
          {
            id: 1,
            userId: 1,
            userName: "Alice Smith",
            userEmail: "alice@example.com",
            action: "UPLOAD",
            entityType: "DOCUMENT",
            entityId: 100,
            ipAddress: "192.168.1.10",
            details: "Uploaded contract.pdf",
            timestamp: "2026-10-04T12:00:00",
          },
          {
            id: 2,
            userId: 2,
            userName: "Bob Reviewer",
            userEmail: "bob@example.com",
            action: "APPROVE",
            entityType: "APPROVAL",
            entityId: 10,
            ipAddress: "192.168.1.11",
            details: "Approved degree certificate",
            timestamp: "2026-10-04T12:30:00",
          },
        ],
      });
    }
  });

  test("rendersNavbarBrandAndPublicLinks", () => {
    renderWithProviders(<Navbar />);
    expect(screen.getByText(/SecureVault|Secure/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^About$/i })).toBeInTheDocument();
  });

  test("rendersFooterContent", () => {
    renderWithProviders(<Footer />);
    expect(screen.getByText(/Secure Vault|Digital Locker/i)).toBeInTheDocument();
  });

  test("rendersHomeHeroContent", () => {
    renderWithProviders(<Home />);
    expect(screen.getByRole("heading", { level: 1 })).toBeInTheDocument();
  });

  test("rendersDocumentsPageStructure", async () => {
    renderWithProviders(<Documents />);
    await waitFor(() => {
      expect(screen.getByText(/All Documents/i)).toBeInTheDocument();
    });
  });

  test("rendersUploadPageInputsAndButtons", () => {
    renderWithProviders(<Upload />);
    expect(screen.getByText(/Upload File/i)).toBeInTheDocument();
  });

  test("rendersLoginPageForm", () => {
    renderWithProviders(<Login />);
    expect(screen.getByLabelText(/Email Address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Password/i)).toBeInTheDocument();
  });

  test("rendersRegisterPageForm", () => {
    renderWithProviders(<Register />);
    expect(screen.getByLabelText(/Full Name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Email Address/i)).toBeInTheDocument();
  });

  test("rendersUploadDragAndDropZoneAndCategorySelect", () => {
    renderWithProviders(<Upload />);
    expect(screen.getByText(/^Category$/i)).toBeInTheDocument();
    expect(screen.getByText(/Drag and drop your file here/i)).toBeInTheDocument();
  });

  test("rendersTrashPageStructure", async () => {
    const Trash = require("../pages/Trash").default;
    renderWithProviders(<Trash />);
    await waitFor(() => {
      expect(screen.getByText(/Trash Bin/i)).toBeInTheDocument();
    });
  });

  test("rendersDocumentDetailsVersionHistoryAndCurrentBadge", async () => {
    const DocumentDetails = require("../pages/DocumentDetails").default;
    renderWithProviders(<DocumentDetails />);
    await waitFor(() => {
      expect(screen.getByText(/Version History/i)).toBeInTheDocument();
      expect(screen.getByText(/Upload New Version/i)).toBeInTheDocument();
    });
  });

  test("rendersNavbarSearchInputAndSearchLink", () => {
    renderWithProviders(<Navbar />);
    expect(screen.getByPlaceholderText(/Search docs or text.../i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^Search$/i })).toBeInTheDocument();
  });

  test("rendersSearchPageStructureAndFilters", async () => {
    const Search = require("../pages/Search").default;
    renderWithProviders(<Search />);
    await waitFor(() => {
      expect(screen.getByText(/Content & Document Search/i)).toBeInTheDocument();
      expect(
        screen.getByPlaceholderText(/Search by keywords, CGPA, numbers, title, text inside scanned docs.../i)
      ).toBeInTheDocument();
      expect(screen.getByText(/Reset Filters/i)).toBeInTheDocument();
    });
  });

  test("rendersSafeHighlightSnippetWithoutDangerouslySetInnerHTML", () => {
    const HighlightSnippet = require("../components/HighlightSnippet").default;
    const { container } = renderWithProviders(
      <HighlightSnippet snippet="Student record: CGPA: 9.06 distinction" query="9.06" />
    );

    const mark = container.querySelector("mark");
    expect(mark).toBeInTheDocument();
    expect(mark.textContent).toBe("9.06");
    expect(container.textContent).toContain("Student record: CGPA: 9.06 distinction");
  });

  // ==================== PHASE 7 TESTS ====================
  test("rendersPublicVerifyPageAndValidDocumentDetails", async () => {
    const PublicVerify = require("../pages/PublicVerify").default;
    renderWithProviders(<PublicVerify />);
    expect(screen.getByText(/SecureVault Document Verification/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/SV-XXXXXXXX/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Verify Document/i })).toBeInTheDocument();
  });

  test("rendersVerifierDashboardAndRecordsTable", async () => {
    const VerifierDashboard = require("../pages/VerifierDashboard").default;
    renderWithProviders(<VerifierDashboard />);
    await waitFor(() => {
      expect(screen.getByText(/Official Document Verification Console/i)).toBeInTheDocument();
      expect(screen.getByText(/Fast Verification Lookup by ID/i)).toBeInTheDocument();
      expect(screen.getByText(/Degree Certificate/i)).toBeInTheDocument();
      expect(screen.getByText(/SV-A1B2C3D4/i)).toBeInTheDocument();
    });
  });

  test("rendersDocumentDetailsQrVerificationSectionAndActions", async () => {
    const DocumentDetails = require("../pages/DocumentDetails").default;
    renderWithProviders(<DocumentDetails />);
    await waitFor(() => {
      expect(screen.getByTestId("qr-verification-section")).toBeInTheDocument();
      expect(screen.getByText(/Download QR/i)).toBeInTheDocument();
      expect(screen.getByText(/Print/i)).toBeInTheDocument();
    });
  });

  test("rendersReviewerDashboardQueueAndActions", async () => {
    const ReviewerDashboard = require("../pages/ReviewerDashboard").default;
    renderWithProviders(<ReviewerDashboard />);
    await waitFor(() => {
      expect(screen.getByText(/Document Approval Workflow/i)).toBeInTheDocument();
      expect(screen.getByText(/Pending Queue/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^✅ Approved$/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^❌ Rejected$/i })).toBeInTheDocument();
      expect(screen.getByText(/Degree Certificate/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^✅ Approve$/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^❌ Reject$/i })).toBeInTheDocument();
    });
  });

  test("rendersDocumentDetailsApprovalHistoryTabAndTimeline", async () => {
    const DocumentDetails = require("../pages/DocumentDetails").default;
    renderWithProviders(<DocumentDetails />);
    await waitFor(() => {
      expect(screen.getByText(/Approval History/i)).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText(/Approval History/i));
    await waitFor(() => {
      expect(screen.getByText(/Document Approval History/i)).toBeInTheDocument();
      expect(screen.getByText(/Submitted for review/i)).toBeInTheDocument();
    });
  });

  test("rendersDocumentDetailsRejectionAlertAndResubmitButton", async () => {
    api.fetchDocumentById.mockReturnValueOnce(
      Promise.resolve({
        data: {
          id: 100,
          title: "Quarterly Audit",
          originalFileName: "audit.pdf",
          status: "REJECTED",
          category: "FINANCIAL",
          currentVersion: 1,
          ownerEmail: "owner@securevault.com",
          fileSize: 1024,
          contentType: "application/pdf",
          checksum: "hash123",
          createdAt: "2026-10-04T10:00:00",
        },
      })
    );
    api.fetchDocumentApprovals.mockReturnValueOnce(
      Promise.resolve({
        data: [
          {
            id: 1,
            documentId: 100,
            status: "REJECTED",
            reviewerName: "Bob Reviewer",
            comments: "Missing supporting receipts on page 4",
            createdAt: "2026-10-04T11:00:00",
            version: 1,
          },
        ],
      })
    );

    localStorage.setItem(
      "user",
      JSON.stringify({ id: 1, email: "owner@securevault.com", roles: ["EMPLOYEE"] })
    );

    const DocumentDetails = require("../pages/DocumentDetails").default;
    renderWithProviders(<DocumentDetails />);
    await waitFor(() => {
      expect(screen.getByText(/Document Submission Rejected/i)).toBeInTheDocument();
      expect(screen.getByText(/Missing supporting receipts on page 4/i)).toBeInTheDocument();
      expect(screen.getByText(/Resubmit for Approval/i)).toBeInTheDocument();
    });
  });

  // ==================== PHASE 9 TESTS ====================
  test("rendersDocumentDetailsShareModalAndListsShares", async () => {
    localStorage.setItem(
      "user",
      JSON.stringify({ id: 1, email: "owner@securevault.com", roles: ["EMPLOYEE"] })
    );

    const DocumentDetails = require("../pages/DocumentDetails").default;
    renderWithProviders(<DocumentDetails />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /🔗 Share/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /🔗 Share/i }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/colleague@securevault.com/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Grant Access/i })).toBeInTheDocument();
      expect(screen.getAllByText(/colleague@example.com/i).length).toBeGreaterThan(0);
      expect(screen.getByRole("button", { name: /Revoke/i })).toBeInTheDocument();
    });
  });

  test("rendersSharedWithMePageWithPermissionBadgesAndDownloadControl", async () => {
    localStorage.setItem(
      "user",
      JSON.stringify({ id: 2, email: "colleague@example.com", roles: ["EMPLOYEE"] })
    );

    const SharedWithMe = require("../pages/SharedWithMe").default;
    renderWithProviders(<SharedWithMe />);

    await waitFor(() => {
      expect(screen.getByText(/Documents Shared with Me/i)).toBeInTheDocument();
      expect(screen.getByText(/Shared Strategy Document/i)).toBeInTheDocument();
      expect(screen.getByText(/Financial Report/i)).toBeInTheDocument();
      expect(screen.getByText(/VIEW ONLY/i)).toBeInTheDocument();
      expect(screen.getAllByText(/DOWNLOAD/i).length).toBeGreaterThan(0);
      expect(screen.getByText(/Alice Owner/i)).toBeInTheDocument();
      expect(screen.getByText(/Bob Finance/i)).toBeInTheDocument();
    });

    // Check download button states
    const downloadButtons = screen.getAllByRole("button", { name: /Download/i });
    expect(downloadButtons[0]).toBeDisabled();
    expect(downloadButtons[1]).not.toBeDisabled();
  });

  test("rendersAdminAuditLogsWithFilterToolbarAndExport", async () => {
    localStorage.setItem(
      "user",
      JSON.stringify({ id: 99, email: "admin@securevault.com", roles: ["ADMIN"] })
    );

    const AdminAuditLogs = require("../pages/AdminAuditLogs").default;
    renderWithProviders(<AdminAuditLogs />);

    await waitFor(() => {
      expect(screen.getByText(/System Activity & Audit/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/Search email/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Export CSV/i })).toBeInTheDocument();
      expect(screen.getAllByText(/alice@example.com/i).length).toBeGreaterThan(0);
      expect(screen.getAllByText(/127.0.0.1/i).length).toBeGreaterThan(0);
      expect(screen.getByText(/Uploaded passport.pdf/i)).toBeInTheDocument();
      expect(screen.getByText(/Shared with colleague@example.com/i)).toBeInTheDocument();
    });
  });

  // =========================================================================
  // PHASE 10: ANALYTICS DASHBOARD TESTS
  // =========================================================================
  test("rendersAnalyticsDashboardForAdminWithAllWidgetsAndCharts", async () => {
    localStorage.setItem(
      "user",
      JSON.stringify({ id: 1, email: "admin@securevault.com", roles: ["ADMIN"] })
    );

    const AnalyticsDashboard = require("../pages/AnalyticsDashboard").default;
    renderWithProviders(<AnalyticsDashboard />);

    await waitFor(() => {
      expect(screen.getByTestId("analytics-dashboard")).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: /Analytics Dashboard/i })).toBeInTheDocument();
      expect(screen.getByText(/ADMIN Scope/i)).toBeInTheDocument();
      expect(screen.getByText(/System-Wide View:/i)).toBeInTheDocument();

      // Stat Cards
      expect(screen.getByTestId("card-total-docs")).toBeInTheDocument();
      expect(screen.getByTestId("card-pending")).toBeInTheDocument();
      expect(screen.getByTestId("card-approved")).toBeInTheDocument();
      expect(screen.getByTestId("card-rejected")).toBeInTheDocument();
      expect(screen.getByTestId("card-verified")).toBeInTheDocument();
      expect(screen.getByTestId("card-active-users")).toBeInTheDocument();
      expect(screen.getByTestId("card-storage")).toBeInTheDocument();

      // Numbers match mock aggregate counts
      expect(screen.getByText("150")).toBeInTheDocument(); // total docs
      expect(screen.getByText("12")).toBeInTheDocument(); // pending
      expect(screen.getByText("110")).toBeInTheDocument(); // approved
      expect(screen.getByText("15")).toBeInTheDocument(); // rejected
      expect(screen.getByText("85")).toBeInTheDocument(); // verified
      expect(screen.getByText("42")).toBeInTheDocument(); // active users
      expect(screen.getByText(/500 MB/i)).toBeInTheDocument(); // formatted storage

      // Charts
      expect(screen.getByTestId("chart-uploads")).toBeInTheDocument();
      expect(screen.getByTestId("chart-categories")).toBeInTheDocument();
      expect(screen.getByTestId("chart-approvals")).toBeInTheDocument();

      // Category breakdown
      expect(screen.getByText("FINANCIAL")).toBeInTheDocument();
      expect(screen.getByText("LEGAL")).toBeInTheDocument();
      expect(screen.getByText("ACADEMIC")).toBeInTheDocument();

      // Recent Activity
      expect(screen.getByTestId("activity-feed")).toBeInTheDocument();
      expect(screen.getByText(/Alice Smith/i)).toBeInTheDocument();
      expect(screen.getByText(/Bob Reviewer/i)).toBeInTheDocument();
      expect(screen.getByText(/Uploaded contract.pdf/i)).toBeInTheDocument();
    });
  });

  test("rendersAnalyticsDashboardScopedForEmployee", async () => {
    localStorage.setItem(
      "user",
      JSON.stringify({ id: 5, email: "employee@securevault.com", roles: ["EMPLOYEE"] })
    );

    // Mock employee scoped values
    api.fetchAnalyticsSummary.mockResolvedValueOnce({
      data: {
        totalDocuments: 10,
        pendingApprovals: 2,
        approvedDocuments: 7,
        rejectedDocuments: 1,
        verifiedDocuments: 5,
        activeUsers: 1,
        storageUsedBytes: 10485760, // 10 MB
      },
    });

    const AnalyticsDashboard = require("../pages/AnalyticsDashboard").default;
    renderWithProviders(<AnalyticsDashboard />);

    await waitFor(() => {
      expect(screen.getByTestId("analytics-dashboard")).toBeInTheDocument();
      expect(screen.getByText(/EMPLOYEE Scope/i)).toBeInTheDocument();
      expect(screen.getByText(/Personal View:/i)).toBeInTheDocument();

      // Scoped numbers
      expect(screen.getByText("10")).toBeInTheDocument();
      expect(screen.getByText("2")).toBeInTheDocument();
      expect(screen.getByText("7")).toBeInTheDocument();
      expect(screen.getByText("1")).toBeInTheDocument();
      expect(screen.getByText("5")).toBeInTheDocument();
      expect(screen.getByText(/10 MB/i)).toBeInTheDocument();

      // Global active users card must NOT be present for EMPLOYEE
      expect(screen.queryByTestId("card-active-users")).not.toBeInTheDocument();
    });
  });

  test("rendersAnalyticsDashboardRestrictedForVerifier", async () => {
    localStorage.setItem(
      "user",
      JSON.stringify({ id: 8, email: "verifier@securevault.com", roles: ["VERIFIER"] })
    );

    const AnalyticsDashboard = require("../pages/AnalyticsDashboard").default;
    renderWithProviders(<AnalyticsDashboard />);

    await waitFor(() => {
      expect(screen.getByText(/Access Restricted/i)).toBeInTheDocument();
      expect(screen.getByText(/restricted to Administrators, Reviewers, and Document Owners/i)).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Go to Verifier Console/i })).toBeInTheDocument();
      expect(screen.queryByTestId("stat-cards-grid")).not.toBeInTheDocument();
    });
  });

  test("rendersNavbarAnalyticsLinkForAuthorizedRoles", async () => {
    localStorage.setItem(
      "user",
      JSON.stringify({ id: 5, email: "employee@securevault.com", roles: ["EMPLOYEE"] })
    );

    renderWithProviders(<Navbar />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: /Analytics/i })).toBeInTheDocument();
    });
  });
});

