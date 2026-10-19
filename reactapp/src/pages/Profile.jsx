import React, { useState } from "react";
import { updateUser } from "../services/api";

export default function Profile() {
  const stored = JSON.parse(localStorage.getItem("dl_user") || "null");
  const [user, setUser] = useState(stored || {});
  const [message, setMessage] = useState("");

  if (!stored) return <div>Please login to view profile</div>;

  const save = async () => {
    try {
      await updateUser(user.id, user);
      localStorage.setItem("dl_user", JSON.stringify(user));
      setMessage("Profile updated successfully.");
    } catch (err) {
      console.error(err);
      setMessage("Update failed. Check console.");
    }
  };

  return (
    <div className="max-w-md mx-auto bg-white p-6 rounded shadow">
      <h2 className="text-xl font-semibold mb-4">Profile</h2>
      <div className="space-y-3">
        <input
          value={user.name || ""}
          onChange={e => setUser({ ...user, name: e.target.value })}
          className="w-full border rounded px-3 py-2"
          placeholder="Full name"
        />
        <input
          value={user.email || ""}
          onChange={e => setUser({ ...user, email: e.target.value })}
          className="w-full border rounded px-3 py-2"
          placeholder="Email"
        />
        <input
          value={user.password || ""}
          type="password"
          onChange={e => setUser({ ...user, password: e.target.value })}
          className="w-full border rounded px-3 py-2"
          placeholder="Password"
        />
        <button onClick={save} className="px-4 py-2 bg-indigo-600 text-white rounded">Save</button>
        {message && <div className="text-sm text-gray-600 mt-2">{message}</div>}
      </div>
    </div>
  );
}
