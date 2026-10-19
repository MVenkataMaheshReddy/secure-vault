import React from "react";
import { Link } from "react-router-dom";

export default function Forbidden() {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "60vh",
        textAlign: "center",
        padding: "20px",
      }}
    >
      <h1 style={{ fontSize: "72px", fontWeight: "bold", color: "#dc2626", margin: "0" }}>
        403
      </h1>
      <h2 style={{ fontSize: "28px", marginTop: "10px", color: "#1f2937" }}>
        Access Denied / Not Authorized
      </h2>
      <p style={{ color: "#4b5563", maxWidth: "480px", margin: "15px auto 25px auto", lineHeight: "1.5" }}>
        You do not have the required permissions or role to view this page. If you believe this is a mistake, please contact your system administrator.
      </p>
      <Link
        to="/documents"
        style={{
          display: "inline-block",
          padding: "10px 24px",
          background: "var(--navy-700, #1D3B66)",
          color: "#fff",
          borderRadius: "6px",
          textDecoration: "none",
          fontWeight: "500",
        }}
      >
        Return to Documents
      </Link>
    </div>
  );
}
