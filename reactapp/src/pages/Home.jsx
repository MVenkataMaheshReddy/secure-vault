
import React from "react";
import { Link } from "react-router-dom";
import { ShieldCheck, UploadCloud, FileText, Users, Lock } from "lucide-react";
import "./Home.css";

export default function Home() {
  return (
    <div className="home">

      <div className="waves">
        <svg viewBox="0 0 500 150" preserveAspectRatio="none">
          <path d="M0,50 C150,150 350,-50 500,50 L500,0 L0,0 Z"></path>
        </svg>
      </div>


      <header className="hero">
        <h1>
          Welcome to <span>Secure Vault</span>
        </h1>
        <p className="subtitle">
          Secure. Reliable. Always accessible.
          <br /> Your digital documents, organized with trust.
        </p>

        <div className="buttons">
          <Link to="/login" className="btn primary">
            🚀 Get Started
          </Link>
          <Link to="/register" className="btn secondary">
            ✨ Create Account
          </Link>
        </div>
      </header>


      <section className="features">
        <h2 className="section-title">Why Choose Us?</h2>
        <div className="feature-grid">
          <div className="feature-card">
            <ShieldCheck className="icon" />
            <h3>Secure</h3>
            <p>End-to-end encryption ensures your documents remain private.</p>
          </div>
          <div className="feature-card">
            <UploadCloud className="icon" />
            <h3>Easy Upload</h3>
            <p>Upload files instantly with drag-and-drop or one-click upload.</p>
          </div>
          <div className="feature-card">
            <FileText className="icon" />
            <h3>Organized</h3>
            <p>Manage documents in folders and access them anytime, anywhere.</p>
          </div>
          <div className="feature-card">
            <Users className="icon" />
            <h3>Share with Anyone</h3>
            <p>Securely share documents with family, friends, or colleagues.</p>
          </div>
          <div className="feature-card">
            <Lock className="icon" />
            <h3>Trusted Security</h3>
            <p>Multi-layered protection with 2FA and data backups.</p>
          </div>
        </div>
      </section>


      <section className="cta">
        <h2>Ready to secure your digital world?</h2>
        <p>Sign up today and enjoy seamless document management.</p>
        <Link to="/register" className="btn glow">
          Create Your Free Account
        </Link>
      </section>


    </div>
  );
}
