// utils/storage.js
export const getUserFromStorage = () => {
  try {
    const userString = localStorage.getItem("dl_user");
    if (!userString) return null; // no user stored
    return JSON.parse(userString); // parse JSON safely
  } catch (err) {
    console.error("Error parsing user from localStorage", err);
    return null; // fallback if corrupted
  }
};
