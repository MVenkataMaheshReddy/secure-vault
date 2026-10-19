import React from "react";
import { Link } from "react-router-dom";
import { ShieldCheck, Share2, Clock, PenTool, Folder, Bell } from "lucide-react";

export default function AboutUs() {
  const services = [
    {
      icon: <ShieldCheck className="w-10 h-10 text-amber-500 mb-3" />,
      title: "Secure Storage",
      description: "Safely store and preserve your confidential documents in an encrypted digital locker.",
    },
    {
      icon: <Share2 className="w-10 h-10 text-amber-500 mb-3" />,
      title: "Quick Sharing",
      description: "Instantly share files with institutions and colleagues with granular view/download permissions.",
    },
    {
      icon: <Clock className="w-10 h-10 text-amber-500 mb-3" />,
      title: "24/7 Access",
      description: "Access your digital locker anytime, anywhere, and across all your desktop and mobile devices.",
    },
    {
      icon: <PenTool className="w-10 h-10 text-amber-500 mb-3" />,
      title: "E-Signature Ready",
      description: "Digitally sign and verify document approvals for a completely paperless compliance workflow.",
    },
    {
      icon: <Folder className="w-10 h-10 text-amber-500 mb-3" />,
      title: "Smart Organization",
      description: "Easily categorize, search OCR text, tag, and organize files into structured digital folders.",
    },
    {
      icon: <Bell className="w-10 h-10 text-amber-500 mb-3" />,
      title: "Real-Time Notifications",
      description: "Receive instant updates and audit trails when documents are uploaded, approved, or shared.",
    },
  ];

  return (
    <div className="relative min-h-screen text-slate-800 pb-16">
      {/* SECTION 1: ABOUT US */}
      <section className="relative pt-6 pb-16 px-4 max-w-6xl mx-auto text-center">
        <div className="inline-block px-4 py-1.5 mb-4 text-xs font-semibold tracking-wider uppercase rounded-full bg-slate-200 text-slate-800 border border-slate-300">
          About The Platform
        </div>
        <h1 className="text-4xl sm:text-5xl font-extrabold text-[#0B1F3A] tracking-tight mb-6">
          About <span className="text-[#0B1F3A]">Secure</span><span className="text-[#F5A623]">Vault</span>
        </h1>
        <p className="max-w-3xl mx-auto text-lg sm:text-xl text-[#5B6B82] leading-relaxed mb-12">
          Welcome to <strong className="text-[#14213D]">SecureVault</strong> – your next-generation enterprise solution
          for <strong className="text-[#1D3B66]">secure cloud storage</strong>. Featuring bank-grade encryption,
          tamper-evident verification, and instant access, your confidential files remain safe, organized, and available
          whenever you need them.
        </p>

        {/* 3 Core Pillars */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-16 text-left">
          <div className="bg-white rounded-xl p-8 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="w-12 h-12 rounded-lg bg-[#E6ECF5] text-[#1D3B66] flex items-center justify-center text-2xl mb-4 font-bold">
              🔒
            </div>
            <h3 className="text-xl font-bold text-[#0B1F3A] mb-2">Security First</h3>
            <p className="text-[#5B6B82] text-sm leading-relaxed">
              Every document is encrypted with SHA-256 binary validation and protected by strict role-based access control.
            </p>
          </div>

          <div className="bg-white rounded-xl p-8 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="w-12 h-12 rounded-lg bg-[#E6ECF5] text-[#1D3B66] flex items-center justify-center text-2xl mb-4 font-bold">
              📂
            </div>
            <h3 className="text-xl font-bold text-[#0B1F3A] mb-2">Easy Management</h3>
            <p className="text-[#5B6B82] text-sm leading-relaxed">
              Upload, tag, version-control, and categorize files in seconds with automated background OCR extraction.
            </p>
          </div>

          <div className="bg-white rounded-xl p-8 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="w-12 h-12 rounded-lg bg-[#E6ECF5] text-[#1D3B66] flex items-center justify-center text-2xl mb-4 font-bold">
              🌍
            </div>
            <h3 className="text-xl font-bold text-[#0B1F3A] mb-2">Access Anywhere</h3>
            <p className="text-[#5B6B82] text-sm leading-relaxed">
              Retrieve documents securely from any device with tamper-proof QR code verification and instant audit logging.
            </p>
          </div>
        </div>
      </section>

      {/* SECTION 2: SERVICES */}
      <section className="relative pt-12 pb-16 px-4 max-w-6xl mx-auto border-t border-slate-200">
        <div className="text-center mb-12">
          <div className="inline-block px-4 py-1.5 mb-3 text-xs font-semibold tracking-wider uppercase rounded-full bg-[#E6ECF5] text-[#1D3B66]">
            Capabilities & Solutions
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-[#0B1F3A] mb-4">
            Our Services
          </h2>
          <p className="max-w-2xl mx-auto text-[#5B6B82] text-base sm:text-lg">
            Discover how SecureVault simplifies document lifecycle management, sharing, and compliance with cutting-edge cloud infrastructure.
          </p>
        </div>

        {/* 6 Services Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {services.map((service, index) => (
            <div
              key={index}
              className="bg-white rounded-xl p-7 border border-slate-200 shadow-sm hover:shadow-lg transition-all transform hover:-translate-y-1 flex flex-col items-center text-center"
            >
              <div className="p-3 rounded-full bg-[#E6ECF5] mb-4 flex items-center justify-center">
                {service.icon}
              </div>
              <h3 className="text-xl font-bold text-[#0B1F3A] mb-2">{service.title}</h3>
              <p className="text-[#5B6B82] text-sm leading-relaxed">{service.description}</p>
            </div>
          ))}
        </div>

        {/* CTA */}
        <div className="mt-16 text-center">
          <Link
            to="/documents"
            className="inline-flex items-center gap-2 px-8 py-3.5 bg-[#F5A623] hover:bg-[#D98E10] text-[#0B1F3A] font-bold text-base rounded-lg shadow-sm transition-all"
          >
            Explore My Documents →
          </Link>
        </div>
      </section>
    </div>
  );
}
