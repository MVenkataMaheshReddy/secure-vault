// import React, { useEffect, useState } from "react";
// import { fetchFolders, fetchDocumentsMetadata } from "../services/api";
// import { Link } from "react-router-dom";

// export default function Dashboard() {
//   const [folders, setFolders] = useState([]);
//   const [docs, setDocs] = useState([]);

//   useEffect(() => {
//     (async () => {
//       try { const f = await fetchFolders(); setFolders(f); } 
//       catch (err) { console.error(err); }
//       try { const d = await fetchDocumentsMetadata(); setDocs(d); } 
//       catch (err) { console.error(err); }
//     })();
//   }, []);

//   return (
//     <div>
//       <h2 className="text-2xl font-semibold mb-4">Dashboard</h2>
//       <div className="grid md:grid-cols-3 gap-4">
//         <div className="col-span-2 bg-white p-4 rounded shadow">
//           <h3 className="font-semibold mb-2">Recent Documents</h3>
//           {docs.length === 0 ? <div className="text-gray-500">No documents yet</div> :
//             <ul className="space-y-2">
//               {docs.slice().reverse().slice(0,6).map(d => (
//                 <li key={d.id} className="flex justify-between items-center">
//                   <div>{d.filename}</div>
//                   <div className="flex items-center gap-3">
//                     <Link to={`/documents/${d.id}`} className="text-sm text-indigo-600">View</Link>
//                   </div>
//                 </li>
//               ))}
//             </ul>}
//         </div>

//         <div className="bg-white p-4 rounded shadow">
//           <h3 className="font-semibold mb-2">Folders</h3>
//           {folders.length === 0 ? <div className="text-gray-500">No folders yet</div> :
//             <ul className="space-y-2">{folders.map(f => <li key={f.id} className="border rounded p-2">{f.name}</li>)}</ul>}
//         </div>
//       </div>

//       <div className="mt-6">
//         <Link to="/upload" className="px-4 py-2 bg-indigo-600 text-white rounded">Upload a document</Link>
//         <Link to="/documents" className="px-4 py-2 ml-3 border rounded">Go to documents</Link>
//       </div>
//     </div>
//   );
// }
import React, { useEffect, useState } from "react";
import { fetchFolders, fetchDocumentsMetadata } from "../services/api";
import { Link } from "react-router-dom";

export default function Dashboard() {
  //   const [folders, setFolders] = useState([]);
  //   const [docs, setDocs] = useState([]);
  //   const [loadingFolders, setLoadingFolders] = useState(true);
  //   const [loadingDocs, setLoadingDocs] = useState(true);

  //   useEffect(() => {
  //     (async () => {
  //       // Fetch folders
  //       try {
  //         const f = await fetchFolders();
  //         console.log("Fetched folders:", f); // Debug log
  //         // Ensure folders is always an array
  //         setFolders(Array.isArray(f) ? f : f?.folders || []);
  //       } catch (err) {
  //         console.error("Error fetching folders:", err);
  //         setFolders([]);
  //       } finally {
  //         setLoadingFolders(false);
  //       }

  //       // Fetch documents
  //       try {
  //         const d = await fetchDocumentsMetadata();
  //         console.log("Fetched documents:", d); // Debug log
  //         // Ensure docs is always an array
  //         setDocs(Array.isArray(d) ? d : d?.documents || []);
  //       } catch (err) {
  //         console.error("Error fetching documents:", err);
  //         setDocs([]);
  //       } finally {
  //         setLoadingDocs(false);
  //       }
  //     })();
  //   }, []);

  //   return (
  //     <div>
  //       <h2 className="text-2xl font-semibold mb-4">Dashboard</h2>

  //       <div className="grid md:grid-cols-3 gap-4">
  //         {/* Recent Documents */}
  //         <div className="col-span-2 bg-white p-4 rounded shadow">
  //           <h3 className="font-semibold mb-2">Recent Documents</h3>
  //           {loadingDocs ? (
  //             <div className="text-gray-500">Loading documents...</div>
  //           ) : docs.length === 0 ? (
  //             <div className="text-gray-500">No documents yet</div>
  //           ) : (
  //             <ul className="space-y-2">
  //               {docs
  //                 .slice()
  //                 .reverse()
  //                 .slice(0, 6)
  //                 .map((d) => (
  //                   <li key={d.id} className="flex justify-between items-center">
  //                     <div>{d.filename}</div>
  //                     <div className="flex items-center gap-3">
  //                       <Link
  //                         to={`/documents/${d.id}`}
  //                         className="text-sm text-indigo-600"
  //                       >
  //                         View
  //                       </Link>
  //                     </div>
  //                   </li>
  //                 ))}
  //             </ul>
  //           )}
  //         </div>

  //         {/* Folders */}
  //         <div className="bg-white p-4 rounded shadow">
  //           <h3 className="font-semibold mb-2">Folders</h3>
  //           {loadingFolders ? (
  //             <div className="text-gray-500">Loading folders...</div>
  //           ) : folders.length === 0 ? (
  //             <div className="text-gray-500">No folders yet</div>
  //           ) : (
  //             <ul className="space-y-2">
  //               {(folders || []).map((f) => (
  //                 <li key={f.id} className="border rounded p-2">
  //                   {f.name}
  //                 </li>
  //               ))}
  //             </ul>
  //           )}
  //         </div>
  //       </div>

  //       {/* Links */}
  //       <div className="mt-6">
  //         <Link
  //           to="/upload"
  //           className="px-4 py-2 bg-indigo-600 text-white rounded"
  //         >
  //           Upload a document
  //         </Link>
  //         <Link to="/documents" className="px-4 py-2 ml-3 border rounded">
  //           Go to documents
  //         </Link>
  //       </div>
  //     </div>
  //   );
  // }
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  const handleFileChange = (e) => {
    setFile(e.target.files[0]);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setFile(e.dataTransfer.files[0]);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
  };

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true);
    setProgress(0);

    // Fake progress animation
    const interval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) {
          clearInterval(interval);
          setUploading(false);
          return 100;
        }
        return prev + 10;
      });
    }, 300);
  };

  return (
    <div className="upload-page">
      <div className="upload-card">
        <h2 className="upload-title">Upload to Secure Vault</h2>
        <p className="upload-subtitle">
          Store your important documents safely & securely.
        </p>

        <div
          className="dropzone"
          onDrop={handleDrop}
          onDragOver={handleDragOver}
        >
          <input
            type="file"
            id="fileInput"
            onChange={handleFileChange}
            hidden
          />
          <label htmlFor="fileInput" className="dropzone-label">
            {file ? (
              <div className="file-preview">
                <strong>{file.name}</strong>
                <span>({(file.size / 1024).toFixed(2)} KB)</span>
              </div>
            ) : (
              <>
                <span className="upload-icon">📂</span>
                <p>Drag & drop a file here, or <span className="browse-text">browse</span></p>
              </>
            )}
          </label>
        </div>

        {uploading && (
          <div className="progress-bar">
            <div
              className="progress-fill"
              style={{ width: `${progress}%` }}
            ></div>
          </div>
        )}

        <button
          className="upload-btn"
          onClick={handleUpload}
          disabled={!file || uploading}
        >
          {uploading ? "Uploading..." : "Upload Document"}
        </button>
      </div>
    </div>
  );
}