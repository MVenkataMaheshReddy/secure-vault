import React from "react";
import { useNavigate } from "react-router-dom";
import "./PageStyles.css";

export default function ContactUs() {
  const navigate = useNavigate();

  return (
    <div className="page-container">
      <h1>Contact Us</h1>
      <p>
        Have questions? Reach out to us anytime!
      </p>
      <form className="contact-form">
        <input type="text" placeholder="Your Name" required />
        <input type="email" placeholder="Your Email" required />
        <textarea placeholder="Your Message"></textarea>
        <button type="submit" className="btn">Send</button>
      </form>
      <button onClick={() => navigate("/upload")} className="btn secondary">
        📤 Go to Upload Page
      </button>
    </div>
  );
}
