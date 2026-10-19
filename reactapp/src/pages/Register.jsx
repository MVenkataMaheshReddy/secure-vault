import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { registerUser } from "../services/api";
import regimage from "../images/register.png";
import "./Register.css";

export default function Register() {
  const [serverError, setServerError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm({
    defaultValues: {
      name: "",
      email: "",
      password: "",
      role: "EMPLOYEE",
      adminCode: "",
    },
  });

  const selectedRole = watch("role", "EMPLOYEE");

  const onSubmit = async (data) => {
    setServerError("");
    setSuccessMessage("");

    try {
      const payload = {
        name: data.name.trim(),
        email: data.email.trim(),
        password: data.password,
        role: data.role || "EMPLOYEE",
      };
      if (data.role === "ADMIN") {
        payload.adminCode = data.adminCode ? data.adminCode.trim() : "";
      }

      await registerUser(payload);

      setSuccessMessage("Registration successful! Redirecting to login...");
      setTimeout(() => {
        navigate("/login");
      }, 1200);
    } catch (err) {
      const message =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Error creating user. Please check your information.";
      setServerError(message);
    }
  };

  return (
    <div className="register-container">
      {/* Left side illustration */}
      <div className="register-image">
        <img src={regimage} alt="Register Illustration" />
      </div>

      {/* Right side form */}
      <div className="register-form">
        <div className="form-box">
          <h2>Register</h2>
          <p className="subtitle" style={{ textAlign: "center", color: "#555", marginBottom: "20px" }}>
            Create an account to securely manage documents
          </p>

          {serverError && <div className="error-box">{serverError}</div>}
          {successMessage && (
            <div
              className="success-box"
              style={{
                background: "#e6fffa",
                color: "#047857",
                padding: "10px",
                marginBottom: "15px",
                borderRadius: "5px",
                fontSize: "14px",
              }}
            >
              {successMessage}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <div>
              <input
                type="text"
                placeholder="Full Name"
                aria-label="Full Name"
                {...register("name", {
                  required: "Name is required",
                  minLength: {
                    value: 2,
                    message: "Name must be at least 2 characters",
                  },
                })}
              />
              {errors.name && (
                <span className="field-error" style={{ color: "#d8000c", fontSize: "14px", display: "block", marginBottom: "10px" }}>
                  {errors.name.message}
                </span>
              )}
            </div>

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
                placeholder="Password (min 8 chars, letter + number)"
                aria-label="Password"
                {...register("password", {
                  required: "Password is required",
                  minLength: {
                    value: 8,
                    message: "Password must be at least 8 characters long",
                  },
                  pattern: {
                    value: /^(?=.*[A-Za-z])(?=.*\d).{8,}$/,
                    message: "Password must contain at least one letter and one number",
                  },
                })}
              />
              {errors.password && (
                <span className="field-error" style={{ color: "#d8000c", fontSize: "14px", display: "block", marginBottom: "10px" }}>
                  {errors.password.message}
                </span>
              )}
            </div>

            <div style={{ marginBottom: "15px" }}>
              <label htmlFor="register-role-select" style={{ display: "block", fontSize: "14px", fontWeight: "600", marginBottom: "6px", color: "#374151" }}>
                Select Role
              </label>
              <select
                id="register-role-select"
                aria-label="Select Role"
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

            {selectedRole === "ADMIN" && (
              <div style={{ marginBottom: "15px" }}>
                <label htmlFor="register-admin-code" style={{ display: "block", fontSize: "14px", fontWeight: "600", marginBottom: "6px", color: "#b91c1c" }}>
                  Admin Access Code *
                </label>
                <input
                  id="register-admin-code"
                  type="password"
                  placeholder="Enter admin access code"
                  aria-label="Admin Access Code"
                  {...register("adminCode", {
                    validate: (value) =>
                      selectedRole !== "ADMIN" || (value && value.trim().length > 0) || "Admin access code is required",
                  })}
                  style={{
                    borderColor: "#ef4444",
                  }}
                />
                {errors.adminCode && (
                  <span className="field-error" style={{ color: "#d8000c", fontSize: "14px", display: "block", marginBottom: "10px" }}>
                    {errors.adminCode.message}
                  </span>
                )}
              </div>
            )}

            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Registering..." : "Register"}
            </button>
          </form>

          <div className="register-footer" style={{ textAlign: "center", marginTop: "15px" }}>
            <p>
              Already have an account? <Link to="/login">Login</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
