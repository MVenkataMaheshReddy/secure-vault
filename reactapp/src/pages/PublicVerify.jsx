import React, { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { getPublicVerification, checkFileAuthenticity } from "../services/api";
import "./PublicVerify.css";

export default function PublicVerify() {
  const { verificationId: routeParamId } = useParams();
  const navigate = useNavigate();

  const [searchId, setSearchId] = useState(routeParamId || "");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [verificationData, setVerificationData] = useState(null);
  const [copiedField, setCopiedField] = useState(null);

  // File check states
  const [checkFile, setCheckFile] = useState(null);
  const [checkingFile, setCheckingFile] = useState(false);
  const [fileCheckResult, setFileCheckResult] = useState(null);
  const [fileCheckError, setFileCheckError] = useState(null);

  const fetchVerification = useCallback(async (idToVerify) => {
    if (!idToVerify || !idToVerify.trim()) return;
    const cleanId = idToVerify.trim().toUpperCase();

    try {
      setLoading(true);
      setError(null);
      setVerificationData(null);
      setFileCheckResult(null);
      setFileCheckError(null);
      setCheckFile(null);

      const res = await getPublicVerification(cleanId);
      setVerificationData(res.data);
    } catch (err) {
      console.error("Verification lookup failed:", err);
      if (err.response?.status === 429) {
        setError("Rate limit exceeded. Please wait a minute before verifying again.");
      } else if (err.response?.status === 404) {
        setError(`No official document found matching verification ID "${cleanId}".`);
      } else {
        setError(err.response?.data?.message || "Failed to verify document. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (routeParamId) {
      setSearchId(routeParamId);
      fetchVerification(routeParamId);
    }
  }, [routeParamId, fetchVerification]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (searchId.trim()) {
      const cleanId = searchId.trim().toUpperCase();
      navigate(`/verify/${cleanId}`);
      fetchVerification(cleanId);
    }
  };

  const handleCopy = (text, fieldName) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(null), 2500);
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      setCheckFile(file);
      setFileCheckResult(null);
      setFileCheckError(null);
    }
  };

  const handleCheckFileSubmit = async (e) => {
    e.preventDefault();
    if (!checkFile || !verificationData?.verificationId) return;

    try {
      setCheckingFile(true);
      setFileCheckResult(null);
      setFileCheckError(null);

      const formData = new FormData();
      formData.append("file", checkFile);

      const res = await checkFileAuthenticity(verificationData.verificationId, formData);
      setFileCheckResult(res.data);
    } catch (err) {
      console.error("File check failed:", err);
      setFileCheckError(
        err.response?.data?.message || "File authenticity check failed. Please try again."
      );
    } finally {
      setCheckingFile(false);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return "N/A";
    try {
      const d = new Date(dateString);
      return d.toLocaleDateString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return dateString;
    }
  };

  const isVerified = verificationData?.status === "VALID";
  const isRevoked = verificationData?.status === "REVOKED";

  return (
    <div className="verify-page-container">
      {/* Header Banner */}
      <div className="verify-header">
        <div className="verify-shield-icon">🛡️</div>
        <h1 className="verify-page-title">SecureVault Document Verification</h1>
        <p className="verify-page-subtitle">
          Public, tamper-evident cryptographic verification for official documents & credentials.
        </p>
      </div>

      {/* Verification ID Input Form */}
      <div className="verify-search-box">
        <form onSubmit={handleSearchSubmit} className="verify-search-form">
          <label htmlFor="verification-id-input" className="verify-search-label">
            Enter Verification ID (e.g., SV-A1B2C3D4)
          </label>
          <div className="verify-search-row">
            <input
              id="verification-id-input"
              type="text"
              value={searchId}
              onChange={(e) => setSearchId(e.target.value.toUpperCase())}
              placeholder="SV-XXXXXXXX"
              className="verify-input"
              maxLength={15}
              autoFocus={!routeParamId}
            />
            <button
              type="submit"
              disabled={loading || !searchId.trim()}
              className="btn-verify-submit"
            >
              {loading ? "Verifying..." : "Verify Document"}
            </button>
          </div>
        </form>
      </div>

      {/* Loading Indicator */}
      {loading && (
        <div className="verify-loading-card">
          <div className="verify-spinner"></div>
          <p>Verifying cryptographic record from SecureVault Ledger...</p>
        </div>
      )}

      {/* Error / Not Found Card */}
      {error && !loading && (
        <div className="verify-result-card invalid-card" role="alert">
          <div className="verify-status-banner banner-invalid">
            <div className="status-icon">✕</div>
            <div>
              <h2 className="status-heading">INVALID OR NOT FOUND</h2>
              <p className="status-subheading">
                This verification identifier cannot be authenticated.
              </p>
            </div>
          </div>
          <div className="verify-card-body">
            <p className="verify-error-msg">{error}</p>
            <div className="verify-warning-note">
              <strong>Caution:</strong> If you were provided a QR code or printed copy with this ID,
              it may be invalid, fraudulent, or the identifier was mistyped.
            </div>
          </div>
        </div>
      )}

      {/* Verification Result Card */}
      {verificationData && !loading && (
        <div
          className={`verify-result-card ${
            isVerified ? "verified-card" : isRevoked ? "revoked-card" : "invalid-card"
          }`}
          data-testid="verification-result-card"
        >
          {/* Status Header Banner */}
          {isVerified ? (
            <div className="verify-status-banner banner-verified">
              <div className="status-icon">✓</div>
              <div>
                <h2 className="status-heading">DOCUMENT VERIFIED</h2>
                <p className="status-subheading">
                  This document is genuine, legally registered, and currently VALID.
                </p>
              </div>
            </div>
          ) : (
            <div className="verify-status-banner banner-revoked">
              <div className="status-icon">⚠️</div>
              <div>
                <h2 className="status-heading">INVALID / REVOKED</h2>
                <p className="status-subheading">
                  The authenticity certificate for this document is REVOKED or NOT VALID.
                </p>
              </div>
            </div>
          )}

          {/* Document Safe Details Grid */}
          <div className="verify-card-body">
            <h3 className="section-title">Official Document Information</h3>

            <div className="verify-info-grid">
              <div className="info-cell">
                <span className="info-label">Verification ID</span>
                <span className="info-value id-badge">
                  {verificationData.verificationId}
                  <button
                    className="btn-tiny-copy"
                    onClick={() => handleCopy(verificationData.verificationId, "id")}
                    title="Copy Verification ID"
                  >
                    {copiedField === "id" ? "✓" : "📋"}
                  </button>
                </span>
              </div>

              <div className="info-cell">
                <span className="info-label">Verification Status</span>
                <span
                  className={`status-pill ${
                    isVerified ? "pill-valid" : "pill-revoked"
                  }`}
                >
                  {verificationData.status}
                </span>
              </div>

              <div className="info-cell full-width">
                <span className="info-label">Document Title</span>
                <span className="info-value text-bold highlight-title">
                  {verificationData.documentTitle}
                </span>
              </div>

              <div className="info-cell">
                <span className="info-label">Category</span>
                <span className="info-value">{verificationData.category || "General"}</span>
              </div>

              <div className="info-cell">
                <span className="info-label">Verified Owner</span>
                <span className="info-value">
                  👤 {verificationData.uploadedBy || verificationData.ownerDisplayName || "SecureVault User"}
                </span>
              </div>

              <div className="info-cell">
                <span className="info-label">Current Version</span>
                <span className="info-value version-pill">
                  v{verificationData.currentVersion || verificationData.currentVersionNumber || 1}
                </span>
              </div>

              <div className="info-cell">
                <span className="info-label">Approved Date</span>
                <span className="info-value">{formatDate(verificationData.approvedAt || verificationData.issuedDate)}</span>
              </div>

              <div className="info-cell full-width">
                <div className="checksum-row-label">
                  <span className="info-label">Current Version Cryptographic Checksum (SHA-256)</span>
                  <button
                    className="btn-tiny-copy"
                    onClick={() => handleCopy(verificationData.checksum, "checksum")}
                    title="Copy Checksum"
                  >
                    {copiedField === "checksum" ? "✓ Copied" : "📋 Copy"}
                  </button>
                </div>
                <div className="checksum-container">
                  <code className="checksum-code">{verificationData.checksum}</code>
                </div>
                <small className="checksum-hint">
                  Guarantees that the digital version stored on SecureVault has never been modified or tampered with.
                </small>
              </div>
            </div>

            {/* Check a File Copy Section */}
            <div className="file-check-section">
              <div className="file-check-header">
                <h3>🔍 Check a File Copy</h3>
                <span className="privacy-badge">🔒 In-Memory Only · Zero Storage</span>
              </div>
              <p className="file-check-desc">
                Have a local copy of this document? Upload it to mathematically verify whether it is an
                authentic, unmodified copy of any official version of this document. The file is never saved.
              </p>

              <form onSubmit={handleCheckFileSubmit} className="file-check-form">
                <div className="file-input-wrapper">
                  <input
                    type="file"
                    id="verify-file-input"
                    onChange={handleFileChange}
                    className="file-input-hidden"
                  />
                  <label htmlFor="verify-file-input" className="file-input-label">
                    {checkFile ? `📄 ${checkFile.name} (${(checkFile.size / 1024).toFixed(1)} KB)` : "📂 Select file copy to check..."}
                  </label>
                  <button
                    type="submit"
                    disabled={!checkFile || checkingFile}
                    className="btn-check-file"
                  >
                    {checkingFile ? "Recomputing Checksum..." : "Check File Copy"}
                  </button>
                </div>
              </form>

              {fileCheckError && (
                <div className="file-check-alert alert-error">
                  ⚠️ {fileCheckError}
                </div>
              )}

              {fileCheckResult && (
                <div
                  className={`file-check-result-box ${
                    fileCheckResult.matches ? "match-success" : "match-failure"
                  }`}
                  data-testid="file-check-result"
                >
                  <div className="match-header">
                    <span className="match-icon">
                      {fileCheckResult.matches ? "✓" : "✕"}
                    </span>
                    <span className="match-title">
                      {fileCheckResult.matches
                        ? `COPY AUTHENTIC — MATCHES VERSION ${fileCheckResult.matchedVersion}`
                        : "CHECKSUM MISMATCH — FILE HAS BEEN TAMPERED OR DOES NOT MATCH"}
                    </span>
                  </div>

                  <p className="match-message">{fileCheckResult.message}</p>

                  <div className="match-details-grid">
                    <div>
                      <span className="meta-sub">Uploaded File SHA-256:</span>
                      <code>{fileCheckResult.uploadedFileChecksum}</code>
                    </div>
                    <div>
                      <span className="meta-sub">Official Current SHA-256:</span>
                      <code>{fileCheckResult.currentVersionChecksum}</code>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Back to Home / Portal link */}
      <div className="verify-footer-nav">
        <Link to="/" className="back-link">
          ← Back to SecureVault Home
        </Link>
        <Link to="/documents" className="back-link">
          Go to Document Vault →
        </Link>
      </div>
    </div>
  );
}
