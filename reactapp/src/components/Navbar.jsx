import React, { useState, useEffect } from "react";
import { NavLink, Link, useNavigate, useLocation } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import { logout as logoutAction } from "../redux/authSlice";
import { logoutUser, fetchPendingApprovalsCount } from "../services/api";
import "./Navbar.css";

export default function Navbar() {
  const reduxUser = useSelector((state) => state.auth?.user);
  let user = reduxUser;

  if (!user) {
    try {
      user = JSON.parse(localStorage.getItem("user") || localStorage.getItem("dl_user") || "null");
    } catch {
      user = null;
    }
  }

  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [navSearch, setNavSearch] = useState("");
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [pendingApprovalsCount, setPendingApprovalsCount] = useState(0);

  // Close mobile drawer on route change
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  const handleLogout = async () => {
    try {
      await logoutUser();
    } catch {
      // Ignore network errors during logout
    } finally {
      dispatch(logoutAction());
      setMobileMenuOpen(false);
      navigate("/login");
    }
  };

  const handleNavSearch = (e) => {
    e.preventDefault();
    if (navSearch && navSearch.trim()) {
      navigate(`/search?q=${encodeURIComponent(navSearch.trim())}`);
    } else {
      navigate("/search");
    }
    setMobileMenuOpen(false);
  };

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
  const isReviewer = userRoles.includes("REVIEWER") || isAdmin;
  const isVerifier = userRoles.includes("VERIFIER") || isAdmin;
  const canUpload = userRoles.includes("ADMIN") || userRoles.includes("EMPLOYEE");
  const canViewAnalytics = Boolean(user && (isAdmin || userRoles.includes("EMPLOYEE") || userRoles.includes("REVIEWER")));
  const primaryRole = userRoles.length > 0 ? userRoles[0] : "EMPLOYEE";
  const displayName = user?.name || user?.username || (user?.email ? user.email.split("@")[0] : "User");

  useEffect(() => {
    if (isAdmin) {
      fetchPendingApprovalsCount()
        .then((res) => {
          setPendingApprovalsCount(res.data?.count ?? res.data?.pending ?? 0);
        })
        .catch(() => {});
    }
  }, [isAdmin]);

  return (
    <header className="navbar">
      <div className="navbar-container">
        {/* Left: Brand Logo */}
        <div className="navbar-left">
          <Link to="/" className="navbar-logo" aria-label="SecureVault">
            <span className="logo-secure">Secure</span>
            <span className="logo-vault">Vault</span>
          </Link>
        </div>

        {/* Middle: Desktop Nav Links */}
        <nav className="navbar-links" aria-label="Main Navigation">
          <NavLink
            to="/documents"
            className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
          >
            Documents
          </NavLink>

          {user && (
            <NavLink
              to="/shared"
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
            >
              Shared with Me
            </NavLink>
          )}

          {canViewAnalytics && (
            <NavLink
              to="/dashboard"
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
            >
              Analytics
            </NavLink>
          )}

          <NavLink
            to="/search"
            className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
          >
            Search
          </NavLink>

          <NavLink
            to="/verify"
            className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
          >
            Verify Doc
          </NavLink>

          {canUpload && (
            <NavLink
              to="/trash"
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
            >
              Trash
            </NavLink>
          )}

          <NavLink
            to="/about"
            className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
          >
            About
          </NavLink>

          {/* Role-based admin links */}
          {isAdmin && (
            <NavLink
              to="/admin/approvals"
              className={({ isActive }) => `nav-link nav-approvals-link ${isActive ? "active" : ""}`}
            >
              Approvals
              {pendingApprovalsCount > 0 && (
                <span className="nav-badge-pill">{pendingApprovalsCount}</span>
              )}
            </NavLink>
          )}

          {isAdmin && (
            <NavLink
              to="/admin"
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
            >
              User Management
            </NavLink>
          )}

          {isAdmin && (
            <NavLink
              to="/admin/audit-logs"
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
            >
              Activity Log
            </NavLink>
          )}

          {isReviewer && !isAdmin && (
            <NavLink
              to="/reviewer"
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
            >
              Review Queue
            </NavLink>
          )}

          {isVerifier && !isAdmin && (
            <NavLink
              to="/verifier"
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
            >
              Verifier
            </NavLink>
          )}
        </nav>

        {/* Right: Search + User + Actions (Desktop) */}
        <div className="navbar-right">
          {/* Search Box */}
          <form onSubmit={handleNavSearch} className="navbar-search-form" role="search">
            <input
              type="text"
              value={navSearch}
              onChange={(e) => setNavSearch(e.target.value)}
              placeholder="Search docs or text..."
              className="navbar-search-input"
              aria-label="Search"
            />
            <button type="submit" className="navbar-search-btn" title="Search">
              <span aria-hidden="true">🔍</span>
            </button>
          </form>

          {/* User Area & Action Buttons */}
          {user ? (
            <div className="navbar-actions-group">
              {/* User Highlighted Container */}
              <div className="navbar-user-container" title={`Signed in as ${displayName} (${primaryRole})`}>
                <span className="navbar-user-avatar" aria-hidden="true">👤</span>
                <span className="navbar-user-name">{displayName}</span>
                <span className="navbar-role-pill">{primaryRole}</span>
              </div>

              {/* Upload Button */}
              {canUpload && (
                <button
                  type="button"
                  onClick={() => navigate("/upload")}
                  className="navbar-btn-upload"
                  title="Upload New Document"
                >
                  Upload
                </button>
              )}

              {/* Logout Button */}
              <button
                type="button"
                onClick={handleLogout}
                className="navbar-btn-logout"
                title="Log out of SecureVault"
                aria-label="Logout"
              >
                <span className="logout-icon" aria-hidden="true">🚪</span>
                <span className="logout-text">Logout</span>
              </button>
            </div>
          ) : (
            <div className="navbar-auth-guest">
              <Link to="/login" className="navbar-link-login">
                Login
              </Link>
              <Link to="/register" className="navbar-btn-signup">
                Sign up
              </Link>
            </div>
          )}

          {/* Hamburger Menu Toggle Button (<1024px) */}
          <button
            type="button"
            className="navbar-hamburger-btn"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label={mobileMenuOpen ? "Close menu" : "Open menu"}
            aria-expanded={mobileMenuOpen}
          >
            {mobileMenuOpen ? "✕" : "☰"}
          </button>
        </div>
      </div>

      {/* Responsive Dropdown Drawer (<1024px) */}
      {mobileMenuOpen && (
        <div className="navbar-mobile-drawer" role="dialog" aria-modal="true">
          {/* Mobile User Box */}
          {user ? (
            <div className="mobile-user-section">
              <div className="navbar-user-container mobile-user-container">
                <span className="navbar-user-avatar">👤</span>
                <span className="navbar-user-name">{displayName}</span>
                <span className="navbar-role-pill">{primaryRole}</span>
              </div>
              {canUpload && (
                <button
                  type="button"
                  onClick={() => {
                    navigate("/upload");
                    setMobileMenuOpen(false);
                  }}
                  className="navbar-btn-upload mobile-upload-btn"
                >
                  Upload Document
                </button>
              )}
            </div>
          ) : (
            <div className="mobile-auth-guest">
              <Link to="/login" className="navbar-link-login" onClick={() => setMobileMenuOpen(false)}>
                Login
              </Link>
              <Link to="/register" className="navbar-btn-signup" onClick={() => setMobileMenuOpen(false)}>
                Sign up
              </Link>
            </div>
          )}

          {/* Mobile Search */}
          <form onSubmit={handleNavSearch} className="navbar-search-form mobile-search-form" role="search">
            <input
              type="text"
              value={navSearch}
              onChange={(e) => setNavSearch(e.target.value)}
              placeholder="Search docs or text..."
              className="navbar-search-input"
              aria-label="Search"
            />
            <button type="submit" className="navbar-search-btn" title="Search">
              <span>🔍</span>
            </button>
          </form>

          {/* Mobile Navigation Links */}
          <div className="mobile-nav-links">
            <NavLink
              to="/documents"
              className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
              onClick={() => setMobileMenuOpen(false)}
            >
              Documents
            </NavLink>

            {user && (
              <NavLink
                to="/shared"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Shared with Me
              </NavLink>
            )}

            {canViewAnalytics && (
              <NavLink
                to="/dashboard"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Analytics
              </NavLink>
            )}

            <NavLink
              to="/search"
              className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
              onClick={() => setMobileMenuOpen(false)}
            >
              Search
            </NavLink>

            <NavLink
              to="/verify"
              className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
              onClick={() => setMobileMenuOpen(false)}
            >
              Verify Doc
            </NavLink>

            {canUpload && (
              <NavLink
                to="/trash"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Trash
              </NavLink>
            )}

            <NavLink
              to="/about"
              className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
              onClick={() => setMobileMenuOpen(false)}
            >
              About
            </NavLink>

            {isAdmin && (
              <NavLink
                to="/admin/approvals"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Approvals {pendingApprovalsCount > 0 && `(${pendingApprovalsCount})`}
              </NavLink>
            )}

            {isAdmin && (
              <NavLink
                to="/admin"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                User Management
              </NavLink>
            )}

            {isAdmin && (
              <NavLink
                to="/admin/audit-logs"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Activity Log
              </NavLink>
            )}

            {isReviewer && !isAdmin && (
              <NavLink
                to="/reviewer"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Review Queue
              </NavLink>
            )}

            {isVerifier && !isAdmin && (
              <NavLink
                to="/verifier"
                className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Verifier
              </NavLink>
            )}

            {user && (
              <button
                type="button"
                onClick={handleLogout}
                className="navbar-btn-logout mobile-logout-btn"
              >
                <span className="logout-icon" aria-hidden="true">🚪</span>
                <span>Logout</span>
              </button>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
