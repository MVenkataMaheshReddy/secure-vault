// import React, { useEffect, useState } from "react";
// import { fetchActivityLogs, deleteActivityLog } from "../services/api";

// export default function ActivityLogs() {
//   const [logs, setLogs] = useState([]);

//   useEffect(() => {
//     (async () => {
//       try {
//         const data = await fetchActivityLogs();
//         setLogs(data || []);
//       } catch (err) {
//         console.error(err);
//       }
//     })();
//   }, []);

//   const handleDelete = async (id) => {
//     if (!window.confirm("Delete log?")) return;
//     try {
//       await deleteActivityLog(id);
//       setLogs(logs.filter(l => l.id !== id));
//     } catch (err) {
//       console.error(err);
//     }
//   };

//   return (
//     <div>
//       <h2 className="text-2xl font-semibold mb-4">Activity Logs</h2>
//       <div className="bg-white p-4 rounded shadow">
//         <ul className="space-y-2">
//           {logs.length === 0 ? (
//             <div className="text-gray-500">No activity logs available</div>
//           ) : (
//             logs.map(l => (
//               <li key={l.id} className="flex justify-between items-center border-b py-2">
//                 <div>
//                   <div className="font-medium">{l.action}</div>
//                   <div className="text-sm text-gray-500">
//                     {l.timestamp} — userId: {l.userId} docId: {l.documentId}
//                   </div>
//                 </div>
//                 <button onClick={() => handleDelete(l.id)} className="text-red-600 px-2 py-1 border rounded">Delete</button>
//               </li>
//             ))
//           )}
//         </ul>
//       </div>
//     </div>
//   );
// }
import React, { useEffect, useState } from "react";
import { fetchActivityLogs } from "../services/api";

export default function ActivityLogs() {
  const [logs, setLogs] = useState([]);           // Always initialize as array
  const [loading, setLoading] = useState(true);   // Loading state
  const [error, setError] = useState(null);       // Error state

  // Fetch activity logs on mount
  useEffect(() => {
    (async () => {
      try {
        const response = await fetchActivityLogs();
        console.log("Activity logs API response:", response); // Debug API response

        // Ensure logs is always an array
        const logsArray = Array.isArray(response) ? response : response?.logs || [];
        setLogs(logsArray);
      } catch (err) {
        console.error("Error fetching activity logs:", err);
        setError("Failed to load activity logs");
        setLogs([]);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // Render loading state
  if (loading) return <div className="text-gray-500">Loading activity logs...</div>;

  // Render error state
  if (error) return <div className="text-red-500">{error}</div>;

  // Render empty state
  if (logs.length === 0) return <div className="text-gray-500">No activity logs found</div>;

  // Render activity logs list
  return (
    <div className="p-4">
      <h2 className="text-2xl font-semibold mb-4">Activity Logs</h2>
      <ul className="space-y-2">
        {(logs || []).map((log) => (
          <li key={log.id} className="border rounded p-2">
            <div className="font-medium">{log.activity}</div>
            <div className="text-sm text-gray-500">{log.timestamp}</div>
          </li>
        ))}
      </ul>
    </div>
  );
}
