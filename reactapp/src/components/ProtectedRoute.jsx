import React from "react";
import { Navigate, Outlet } from "react-router-dom";
import { useSelector } from "react-redux";

export default function ProtectedRoute({ allowedRoles = [] }) {
  const reduxToken = useSelector((state) => state.auth?.token);
  const reduxUser = useSelector((state) => state.auth?.user);

  const localToken = localStorage.getItem("token") || localStorage.getItem("accessToken");
  const isAuthenticated = Boolean(reduxToken || localToken);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && allowedRoles.length > 0) {
    let user = reduxUser;
    if (!user) {
      try {
        user = JSON.parse(localStorage.getItem("user") || localStorage.getItem("dl_user") || "null");
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

    const hasAllowedRole = allowedRoles.some((allowed) =>
      userRoles.includes(allowed.toUpperCase().replace("ROLE_", ""))
    );

    if (!hasAllowedRole) {
      return <Navigate to="/unauthorized" replace />;
    }
  }

  return <Outlet />;
}
