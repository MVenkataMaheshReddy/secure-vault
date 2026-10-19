import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { loginUserThunk, clearError } from "../redux/authSlice";
import loginImage from "../images/login.png";
import "./Login.css";

export default function Login() {
  const [serverError, setServerError] = useState("");
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const token = useSelector((state) => state.auth?.token) || localStorage.getItem("token") || localStorage.getItem("accessToken");
  const authUser = useSelector((state) => state.auth?.user);
  const authStatus = useSelector((state) => state.auth?.status);

  // Redirect authenticated users away from /login
  useEffect(() => {
    if (token) {
      const activeRole = (authUser?.role || authUser?.roles?.[0] || "").toUpperCase().replace("ROLE_", "");
      if (activeRole === "ADMIN") {
        navigate("/admin/dashboard", { replace: true });
      } else {
        navigate("/documents", { replace: true });
      }
    }
  }, [token, authUser, navigate]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    defaultValues: {
      email: "",
      password: "",
      role: "EMPLOYEE",
    },
  });

  const onSubmit = async (data) => {
    setServerError("");
    dispatch(clearError());

    try {
      const selectedRole = data.role || "EMPLOYEE";
      const resultAction = await dispatch(
        loginUserThunk({
          email: data.email.trim(),
          password: data.password,
          role: selectedRole,
        })
      );

      if (loginUserThunk.fulfilled.match(resultAction)) {
        const loggedUser = resultAction.payload?.user;
        const activeRole = (loggedUser?.role || loggedUser?.roles?.[0] || selectedRole).toUpperCase().replace("ROLE_", "");
        if (activeRole === "ADMIN") {
          navigate("/admin/dashboard");
        } else {
          navigate("/documents");
        }
      } else {
        setServerError(resultAction.payload || "Invalid email or password");
      }
    } catch (err) {
      setServerError("Invalid email or password");
    }
  };

  return (
    <div className="login-container">
      {/* Left side illustration */}
      <div className="login-image">
        <img src={loginImage} alt="Login Illustration" />
      </div>

      {/* Right side form */}
      <div className="login-form">
        <div className="form-box">
          <h2>Welcome Back</h2>
          <p className="subtitle">Securely access your Digital Locker</p>

          {serverError && <div className="error-box">{serverError}</div>}

          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <div>
              <input
                type="email"
                placeholder="Email address"
                aria-label="Email address"
                {...register("email", {
                  required: "Email is required",
                  pattern: {
                    value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                    message: "Invalid email address",
                  },
                })}
              />
              {errors.email && (
                <span className="field-error" style={{ color: "#d8000c", fontSize: "14px", display: "block", marginBottom: "10px" }}>
                  {errors.email.message}
                </span>
              )}
            </div>

            <div>
              <input
                type="password"
                placeholder="Password"
                aria-label="Password"
                {...register("password", {
                  required: "Password is required",
                })}
              />
              {errors.password && (
                <span className="field-error" style={{ color: "#d8000c", fontSize: "14px", display: "block", marginBottom: "10px" }}>
                  {errors.password.message}
                </span>
              )}
            </div>

            <div style={{ marginBottom: "15px" }}>
              <label htmlFor="login-role-select" style={{ display: "block", fontSize: "14px", fontWeight: "600", marginBottom: "6px", color: "#374151" }}>
                Login As Role
              </label>
              <select
                id="login-role-select"
                aria-label="Login As Role"
                {...register("role", { required: "Role is required" })}
                style={{
                  width: "100%",
                  padding: "10px 12px",
                  borderRadius: "6px",
                  border: "1px solid #d1d5db",
                  backgroundColor: "#fff",
                  fontSize: "14px",
                  color: "#1f2937",
                  boxSizing: "border-box",
                }}
              >
                <option value="EMPLOYEE">Employee</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>

            <button type="submit" disabled={isSubmitting || authStatus === "loading"}>
              {isSubmitting || authStatus === "loading" ? "Logging in..." : "Login"}
            </button>
          </form>

          <div className="login-footer">
            <p>
              Don’t have an account? <Link to="/register">Register</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
