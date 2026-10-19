
import React from "react";
import nf from "../images/notfound.png";

export default function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-50 px-4">
      
      <h2 className="text-5xl font-extrabold text-purple-600 mb-4 flex items-center gap-2 animate-bounce">
        🤯 Oh my God! Page Not Found
      </h2>

    
      <p className="text-lg text-gray-600 mb-10">
        The page you are looking for doesn't exist.
      </p>

     
      <img
        src={nf}
        alt="Not Found"
        className="w-[400px] md:w-[600px] lg:w-[700px] max-w-full drop-shadow-2xl animate-pulse"
      />

      
      <a
        href="/"
        className="mt-10 px-6 py-3 bg-purple-600 text-white rounded-xl shadow-lg hover:bg-purple-700 transition"
      >
        Go Back Home
      </a>
    </div>
  );
}
