import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";

import Home from "./pages/Home";
import Login from "./pages/Login";
import AboutUs from "./pages/AboutUs";
import Admin from "./pages/Admin";
import ContactUs from "./pages/ContactUs";
import Register from "./pages/Register";
import Documents from "./pages/Documents";
import Upload from "./pages/Upload";
import DocumentDetails from "./pages/DocumentDetails";
import Search from "./pages/Search";
import Trash from "./pages/Trash";
import AdminAuditLogs from "./pages/AdminAuditLogs";
import SharedWithMe from "./pages/SharedWithMe";
import Profile from "./pages/Profile";
import PublicVerify from "./pages/PublicVerify";
import VerifierDashboard from "./pages/VerifierDashboard";
import ReviewerDashboard from "./pages/ReviewerDashboard";
import AnalyticsDashboard from "./pages/AnalyticsDashboard";
import AdminApprovals from "./pages/AdminApprovals";
import NotFound from "./pages/NotFound";
import Forbidden from "./pages/Forbidden";
import ProtectedRoute from "./components/ProtectedRoute";

export default function App() {
  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="flex-1 container mx-auto px-4 py-8">
        <Routes>
          {/* Public Routes */}
          <Route path="/" element={<Home />} />
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<Login />} />
          <Route path="/about" element={<AboutUs />} />
          <Route path="/about-us" element={<Navigate to="/about" replace />} />
          <Route path="/services" element={<Navigate to="/about" replace />} />
          <Route path="/contact" element={<ContactUs />} />
          <Route path="/unauthorized" element={<Forbidden />} />
          <Route path="/403" element={<Forbidden />} />

          {/* Phase 7: Public Verification Routes (no login required) */}
          <Route path="/verify/:verificationId" element={<PublicVerify />} />
          <Route path="/verify" element={<PublicVerify />} />

          {/* Authenticated routes: all roles */}
          <Route element={<ProtectedRoute />}>
            <Route path="/documents" element={<Documents />} />
            <Route path="/shared" element={<SharedWithMe />} />
            <Route path="/search" element={<Search />} />
            <Route path="/documentsfetch/:id" element={<DocumentDetails />} />
            <Route path="/profile" element={<Profile />} />
          </Route>

          {/* Phase 10: Analytics Dashboard for ADMIN, EMPLOYEE, REVIEWER (VERIFIER gets 403) */}
          <Route element={<ProtectedRoute allowedRoles={["ADMIN", "EMPLOYEE", "REVIEWER"]} />}>
            <Route path="/dashboard" element={<AnalyticsDashboard />} />
            <Route path="/analytics" element={<AnalyticsDashboard />} />
          </Route>

          {/* Upload and Trash routes: ADMIN and EMPLOYEE only */}
          <Route element={<ProtectedRoute allowedRoles={["ADMIN", "EMPLOYEE"]} />}>
            <Route path="/upload" element={<Upload />} />
            <Route path="/trash" element={<Trash />} />
          </Route>

          {/* Admin routes: ADMIN only */}
          <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
            <Route path="/admin" element={<Admin />} />
            <Route path="/admin/dashboard" element={<AnalyticsDashboard />} />
            <Route path="/admin/approvals" element={<AdminApprovals />} />
            <Route path="/admin/audit-logs" element={<AdminAuditLogs />} />
            <Route path="/activity-logs" element={<AdminAuditLogs />} />
          </Route>

          {/* Verifier Console: ADMIN and VERIFIER */}
          <Route element={<ProtectedRoute allowedRoles={["ADMIN", "VERIFIER"]} />}>
            <Route path="/verifier" element={<VerifierDashboard />} />
          </Route>

          {/* Reviewer Console: ADMIN and REVIEWER (Phase 8) */}
          <Route element={<ProtectedRoute allowedRoles={["ADMIN", "REVIEWER"]} />}>
            <Route path="/reviewer" element={<ReviewerDashboard />} />
          </Route>

          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}
