// // import React from "react";
// // import { useNavigate } from "react-router-dom";
// // import "./PageStyles.css";

// // export default function Services() {
// //   const navigate = useNavigate();

// //   return (
// //     <div className="page-container">
// //       <h1>Our Services</h1>
// //       <ul className="services-list">
// //         <li>🔒 Secure Cloud Storage</li>
// //         <li>📂 Easy Document Uploads</li>
// //         <li>📑 Instant Access Anytime</li>
// //         <li>🔐 Strong Encryption</li>
// //       </ul>
// //       <button onClick={() => navigate("/contact")} className="btn">
// //         📞 Contact Us
// //       </button>
// //     </div>
// //   );
// // }
// import React from "react";
// import { ShieldCheck, Share2, Clock, PenTool, Folder } from "lucide-react";

// export default function Services() {
//   const services = [
//     {
//       icon: <ShieldCheck className="w-10 h-10 text-indigo-600" />,
//       title: "Secure Storage",
//       description: "Safely store your important documents in an encrypted digital locker.",
//     },
//     {
//       icon: <Share2 className="w-10 h-10 text-indigo-600" />,
//       title: "Quick Sharing",
//       description: "Share files instantly with institutions or individuals with complete control.",
//     },
//     {
//       icon: <Clock className="w-10 h-10 text-indigo-600" />,
//       title: "24/7 Access",
//       description: "Access your locker anytime, anywhere, and on any device.",
//     },
//     {
//       icon: <PenTool className="w-10 h-10 text-indigo-600" />,
//       title: "E-Signature Ready",
//       description: "Digitally sign your documents for a paperless experience.",
//     },
//     {
//       icon: <Folder className="w-10 h-10 text-indigo-600" />,
//       title: "Smart Organization",
//       description: "Easily search, sort, and manage your files with categorized folders.",
//     },
//   ];

//   return (
//     <section className="py-16 bg-gradient-to-b from-white to-indigo-50">
//       <div className="max-w-6xl mx-auto px-6">
//         <h2 className="text-3xl font-bold text-center text-gray-800 mb-12">
//           Our Services
//         </h2>
//         <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
//           {services.map((service, index) => (
//             <div
//               key={index}
//               className="bg-white rounded-2xl shadow-md p-6 hover:shadow-lg transition-transform transform hover:-translate-y-2"
//             >
//               <div className="flex justify-center mb-4">{service.icon}</div>
//               <h3 className="text-xl font-semibold text-gray-800 text-center">
//                 {service.title}
//               </h3>
//               <p className="text-gray-600 text-center mt-2">
//                 {service.description}
//               </p>
//             </div>
//           ))}
//         </div>
//       </div>
//     </section>
//   );
// }
// import React from "react";

// export default function Services() {
//   return (
//     <div className="relative min-h-screen flex flex-col items-center justify-center text-white bg-gradient-to-b from-purple-600 via-purple-500 to-blue-500 overflow-hidden">
      
//       {/* Top Waves */}
//       <div className="absolute top-0 left-0 w-full overflow-hidden leading-none">
//         <svg
//           className="relative block w-full h-32"
//           xmlns="http://www.w3.org/2000/svg"
//           preserveAspectRatio="none"
//           viewBox="0 0 1200 120"
//         >
//           <path
//             d="M0,0V46.29c47.5,22,103.5,29.5,158,17.39,70-15.51,136-57.51,206-61.9C438.5-2,512.5,33,583,53.57c69.5,20.36,138.5,27.36,209,7.14,70-20.71,136-66.71,206-72.1,55-4.39,111,12.9,158,30.9V0Z"
//             opacity=".25"
//             className="fill-purple-300"
//           ></path>
//           <path
//             d="M0,0V15.81c47.5,22,103.5,39.5,158,27.39,70-15.51,136-57.51,206-61.9C438.5-2,512.5,27,583,47.57c69.5,20.36,138.5,27.36,209,7.14,70-20.71,136-66.71,206-72.1,55-4.39,111,12.9,158,30.9V0Z"
//             opacity=".5"
//             className="fill-purple-400"
//           ></path>
//           <path
//             d="M0,0V5.81C47.5,27.81,103.5,47.5,158,35.39c70-15.51,136-57.51,206-61.9C438.5-2,512.5,17,583,37.57c69.5,20.36,138.5,27.36,209,7.14,70-20.71,136-66.71,206-72.1,55-4.39,111,12.9,158,30.9V0Z"
//             className="fill-blue-500"
//           ></path>
//         </svg>
//       </div>

//       {/* Content */}
//       <div className="z-10 max-w-6xl px-6 py-20 text-center">
//         <h1 className="text-5xl font-extrabold mb-6">Our Services</h1>
//         <p className="text-lg mb-12 max-w-2xl mx-auto">
//           Discover how our <span className="font-bold">Digital Locker System</span> simplifies document storage, 
//           sharing, and security with cutting-edge technology.
//         </p>

//         {/* Services Grid */}
//         <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
//           {/* Service 1 */}
//           <div className="bg-white text-purple-700 p-6 rounded-2xl shadow-lg hover:scale-105 transition">
//             <h2 className="text-2xl font-bold mb-3">🔐 Secure Storage</h2>
//             <p>Store all your important documents in one safe and encrypted location.</p>
//           </div>

//           {/* Service 2 */}
//           <div className="bg-white text-purple-700 p-6 rounded-2xl shadow-lg hover:scale-105 transition">
//             <h2 className="text-2xl font-bold mb-3">🌍 Easy Access</h2>
//             <p>Access your documents anytime, anywhere, with just one click.</p>
//           </div>

//           {/* Service 3 */}
//           <div className="bg-white text-purple-700 p-6 rounded-2xl shadow-lg hover:scale-105 transition">
//             <h2 className="text-2xl font-bold mb-3">🤝 Share Securely</h2>
//             <p>Share documents with others securely without compromising privacy.</p>
//           </div>
//         </div>

//         {/* Button */}
//         <button className="mt-12 px-8 py-3 bg-gradient-to-r from-purple-500 to-blue-500 rounded-full text-lg font-semibold shadow-lg hover:scale-110 transition">
//           Get Started 🚀
//         </button>
//       </div>

//       {/* Bottom Waves */}
//       <div className="absolute bottom-0 left-0 w-full overflow-hidden leading-none">
//         <svg
//           className="relative block w-full h-32 rotate-180"
//           xmlns="http://www.w3.org/2000/svg"
//           preserveAspectRatio="none"
//           viewBox="0 0 1200 120"
//         >
//           <path
//             d="M0,0V46.29c47.5,22,103.5,29.5,158,17.39,70-15.51,136-57.51,206-61.9C438.5-2,512.5,33,583,53.57c69.5,20.36,138.5,27.36,209,7.14,70-20.71,136-66.71,206-72.1,55-4.39,111,12.9,158,30.9V0Z"
//             opacity=".25"
//             className="fill-purple-300"
//           ></path>
//           <path
//             d="M0,0V15.81c47.5,22,103.5,39.5,158,27.39,70-15.51,136-57.51,206-61.9C438.5-2,512.5,27,583,47.57c69.5,20.36,138.5,27.36,209,7.14,70-20.71,136-66.71,206-72.1,55-4.39,111,12.9,158,30.9V0Z"
//             opacity=".5"
//             className="fill-purple-400"
//           ></path>
//           <path
//             d="M0,0V5.81C47.5,27.81,103.5,47.5,158,35.39c70-15.51,136-57.51,206-61.9C438.5-2,512.5,17,583,37.57c69.5,20.36,138.5,27.36,209,7.14,70-20.71,136-66.71,206-72.1,55-4.39,111,12.9,158,30.9V0Z"
//             className="fill-blue-500"
//           ></path>
//         </svg>
//       </div>
//     </div>
//   );
// }
import React from "react";
import { ShieldCheck, Share2, Clock, PenTool, Folder } from "lucide-react";

export default function Services() {
  // const services = [
  //   {
  //     icon: <ShieldCheck className="w-12 h-12 mb-4 text-white" />,
  //     title: "Secure Storage",
  //     description: "Safely store your important documents in an encrypted digital locker.",
  //     gradient: "from-purple-500 to-indigo-500",
  //   },
  //   {
  //     icon: <Share2 className="w-12 h-12 mb-4 text-white" />,
  //     title: "Quick Sharing",
  //     description: "Instantly share files with institutions or individuals with complete control.",
  //     gradient: "from-indigo-600 to-blue-500",
  //   },
  //   {
  //     icon: <Clock className="w-12 h-12 mb-4 text-white" />,
  //     title: "24/7 Access",
  //     description: "Access your locker anytime, anywhere, and on any device.",
  //     gradient: "from-blue-500 to-purple-600",
  //   },
  //   {
  //     icon: <PenTool className="w-12 h-12 mb-4 text-white" />,
  //     title: "E-Signature Ready",
  //     description: "Digitally sign your documents and go completely paperless.",
  //     gradient: "from-purple-600 to-indigo-600",
  //   },
  //   {
  //     icon: <Folder className="w-12 h-12 mb-4 text-white" />,
  //     title: "Smart Organization",
  //     description: "Easily search, sort, and manage your files with categorized folders.",
  //     gradient: "from-indigo-500 to-purple-700",
  //   },
  // ];
const services = [
  {
    icon: <ShieldCheck className="w-12 h-12 mb-4 text-white" />,
    title: "Secure Storage",
    description: "Safely store your important documents in an encrypted digital locker.",
    gradient: "from-purple-500 to-indigo-500",
  },
  {
    icon: <Share2 className="w-12 h-12 mb-4 text-white" />,
    title: "Quick Sharing",
    description: "Instantly share files with institutions or individuals with complete control.",
    gradient: "from-indigo-600 to-blue-500",
  },
  {
    icon: <Clock className="w-12 h-12 mb-4 text-white" />,
    title: "24/7 Access",
    description: "Access your locker anytime, anywhere, and on any device.",
    gradient: "from-blue-500 to-purple-600",
  },
  {
    icon: <PenTool className="w-12 h-12 mb-4 text-white" />,
    title: "E-Signature Ready",
    description: "Digitally sign your documents and go completely paperless.",
    gradient: "from-purple-600 to-indigo-600",
  },
  {
    icon: <Folder className="w-12 h-12 mb-4 text-white" />,
    title: "Smart Organization",
    description: "Easily search, sort, and manage your files with categorized folders.",
    gradient: "from-indigo-500 to-purple-700",
  },
  {
    icon: <Clock className="w-12 h-12 mb-4 text-white" />, // you can swap this for Bell if using lucide-react
    title: "Real-Time Notifications",
    description: "Get instant alerts when documents are uploaded, shared, or accessed.",
    gradient: "from-purple-500 to-pink-500",
  },
];

  return (
    <div className="relative flex flex-col items-center justify-center min-h-[calc(100vh-160px)] bg-gradient-to-br from-purple-50 via-white to-purple-100 text-gray-800 px-6 overflow-hidden">
      
      {/* Top Wave */}
      <div className="absolute top-0 left-0 w-full overflow-hidden leading-none">
        <svg
          className="relative block w-full h-28"
          xmlns="http://www.w3.org/2000/svg"
          preserveAspectRatio="none"
          viewBox="0 0 1440 320"
        >
          <defs>
            <linearGradient id="topGradientServices" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#7C3AED" />
              <stop offset="50%" stopColor="#6366F1" />
              <stop offset="100%" stopColor="#3B82F6" />
            </linearGradient>
          </defs>
          {/* <path
            fill="url(#topGradientServices)"
            d="M0,64L40,85.3C80,107,160,149,240,160C320,171,400,149,480,128C560,107,640,85,720,101.3C800,117,880,171,960,181.3C1040,192,1120,160,1200,144C1280,128,1360,128,1400,128L1440,128L1440,0L0,0Z"
          ></path> */}
          {/* <path
  fill="url(#topGradientServices)"
  d="M0,160L40,176C80,192,160,224,240,240C320,256,400,256,480,229.3C560,203,640,149,720,117.3C800,85,880,75,960,101.3C1040,128,1120,192,1200,213.3C1280,235,1360,213,1400,202.7L1440,192L1440,0L0,0Z"
/> */}
{/* <path
  fill="url(#topGradientServices)"
  d="M0,0L1440,160L1440,0L0,0Z"
/> */}
<path
  fill="url(#topGradientServices)"
  d="M0,128L120,112L240,144L360,96L480,128L600,80L720,128L840,96L960,128L1080,112L1200,144L1320,96L1440,128L1440,0L0,0Z"
/>



        </svg>
      </div>

      {/* Content */}
      <div className="relative max-w-6xl text-center z-10">
        <h2 className="text-6xl font-extrabold mb-10 text-black drop-shadow-md">
          Our Services
        </h2>

        {/* Service Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 mb-12">
          {services.map((service, index) => (
            <div
              key={index}
              className={`bg-gradient-to-br ${service.gradient} text-white rounded-xl p-8 shadow-lg hover:scale-105 transition duration-300`}
            >
              <div className="flex justify-center">{service.icon}</div>
              <h3 className="text-2xl font-bold mb-3">{service.title}</h3>
              <p className="text-base">{service.description}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Bottom Wave */}
      <div className="absolute bottom-0 left-0 w-full overflow-hidden leading-none rotate-180">
        <svg
          className="relative block w-full h-28"
          xmlns="http://www.w3.org/2000/svg"
          preserveAspectRatio="none"
          viewBox="0 0 1440 320"
        >
          <defs>
            <linearGradient id="bottomGradientServices" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#3B82F6" />
              <stop offset="50%" stopColor="#6366F1" />
              <stop offset="100%" stopColor="#7C3AED" />
            </linearGradient>
          </defs>
          {/* <path
            fill="url(#bottomGradientServices)"
            d="M0,64L40,85.3C80,107,160,149,240,160C320,171,400,149,480,128C560,107,640,85,720,101.3C800,117,880,171,960,181.3C1040,192,1120,160,1200,144C1280,128,1360,128,1400,128L1440,128L1440,0L0,0Z"
          ></path> */}
    <path
  fill="url(#topGradientServices)"
  d="M0,128L120,112L240,144L360,96L480,128L600,80L720,128L840,96L960,128L1080,112L1200,144L1320,96L1440,128L1440,0L0,0Z"
/>

        </svg>
      </div>
    </div>
  );
}
