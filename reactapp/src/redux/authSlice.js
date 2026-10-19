import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import { loginUser as loginApi, registerUser as registerApi, getCurrentUser as getCurrentUserApi } from "../services/api";

// Safely restore intial state from localStorage
const storedToken = localStorage.getItem("token") || localStorage.getItem("accessToken") || null;
let storedUser = null;
try {
  const userJson = localStorage.getItem("user") || localStorage.getItem("dl_user");
  if (userJson) {
    storedUser = JSON.parse(userJson);
  }
} catch {
  storedUser = null;
}

export const loginUserThunk = createAsyncThunk(
  "auth/login",
  async (credentials, { rejectWithValue }) => {
    try {
      const response = await loginApi(credentials);
      const data = response.data;
      const token = data.token || data.accessToken;
      const user = data.user || {
        id: data.userId,
        name: data.username,
        email: credentials.email,
        roles: data.role ? [data.role] : ["EMPLOYEE"],
      };

      // Persist in localStorage
      localStorage.setItem("token", token);
      localStorage.setItem("accessToken", token);
      localStorage.setItem("user", JSON.stringify(user));
      localStorage.setItem("dl_user", JSON.stringify(user));

      return { token, user };
    } catch (err) {
      const message =
        err.response?.data?.message || "Invalid email or password";
      return rejectWithValue(message);
    }
  }
);

export const registerUserThunk = createAsyncThunk(
  "auth/register",
  async (userData, { rejectWithValue }) => {
    try {
      const response = await registerApi(userData);
      return response.data;
    } catch (err) {
      const message =
        err.response?.data?.message || "Registration failed. Please try again.";
      return rejectWithValue(message);
    }
  }
);

export const fetchCurrentUserThunk = createAsyncThunk(
  "auth/me",
  async (_, { rejectWithValue }) => {
    try {
      const response = await getCurrentUserApi();
      const user = response.data;
      localStorage.setItem("user", JSON.stringify(user));
      localStorage.setItem("dl_user", JSON.stringify(user));
      return user;
    } catch (err) {
      return rejectWithValue("Failed to fetch user");
    }
  }
);

const authSlice = createSlice({
  name: "auth",
  initialState: {
    user: storedUser,
    token: storedToken,
    status: "idle", // 'idle' | 'loading' | 'succeeded' | 'failed'
    error: null,
  },
  reducers: {
    loginSuccess: (state, action) => {
      const { token, user } = action.payload;
      state.token = token;
      state.user = user;
      state.status = "succeeded";
      state.error = null;

      localStorage.setItem("token", token);
      localStorage.setItem("accessToken", token);
      localStorage.setItem("user", JSON.stringify(user));
      localStorage.setItem("dl_user", JSON.stringify(user));
    },
    logout: (state) => {
      state.user = null;
      state.token = null;
      state.status = "idle";
      state.error = null;

      localStorage.removeItem("token");
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("user");
      localStorage.removeItem("dl_user");
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // Login
      .addCase(loginUserThunk.pending, (state) => {
        state.status = "loading";
        state.error = null;
      })
      .addCase(loginUserThunk.fulfilled, (state, action) => {
        state.status = "succeeded";
        state.token = action.payload.token;
        state.user = action.payload.user;
        state.error = null;
      })
      .addCase(loginUserThunk.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.payload || "Invalid email or password";
      })
      // Register
      .addCase(registerUserThunk.pending, (state) => {
        state.status = "loading";
        state.error = null;
      })
      .addCase(registerUserThunk.fulfilled, (state) => {
        state.status = "succeeded";
        state.error = null;
      })
      .addCase(registerUserThunk.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.payload || "Registration failed";
      })
      // Me
      .addCase(fetchCurrentUserThunk.fulfilled, (state, action) => {
        state.user = action.payload;
      });
  },
});

export const { loginSuccess, logout, clearError } = authSlice.actions;
export default authSlice.reducer;
