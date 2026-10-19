import React, { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { fetchFolders, createFolder, uploadDocument } from "../services/api";
import "./Upload.css";

const ALLOWED_EXTENSIONS = ["pdf", "docx", "png", "jpg", "jpeg"];
const MAX_DOC_SIZE = 10 * 1024 * 1024; // 10 MB
const MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5 MB

export default function Upload() {
  const navigate = useNavigate();
  const fileInputRef = useRef(null);

  const [folders, setFolders] = useState([]);
  const [loadingFolders, setLoadingFolders] = useState(true);

  // Form state
  const [selectedFile, setSelectedFile] = useState(null);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("GENERAL");
  const [description, setDescription] = useState("");
  const [tagsInput, setTagsInput] = useState("");

  // Legacy fields
  const [selectedFolder, setSelectedFolder] = useState("");
  const [newFolderName, setNewFolderName] = useState("");
  const [creatingFolder, setCreatingFolder] = useState(false);
  const [userId, setUserId] = useState("");
  const [folderIdInput, setFolderIdInput] = useState("");

  // UI state
  const [isDragging, setIsDragging] = useState(false);
  const [clientError, setClientError] = useState("");
  const [serverError, setServerError] = useState("");
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [uploadSuccess, setUploadSuccess] = useState(null);

  useEffect(() => {
    loadFolders();
  }, []);

  const loadFolders = async () => {
    try {
      const response = await fetchFolders();
      setFolders(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      console.error("Error fetching folders:", err);
      setFolders([]);
    } finally {
      setLoadingFolders(false);
    }
  };

  // Client-side pre-validation
  const validateSelectedFile = (file) => {
    setClientError("");
    setServerError("");
    setUploadSuccess(null);

    if (!file) return false;

    if (file.size === 0) {
      setClientError("Selected file is empty (0 bytes). Please select a valid file.");
      return false;
    }

    const name = file.name || "";
    const ext = name.split(".").pop().toLowerCase();

    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      setClientError(
        `Invalid file type ".${ext}". Allowed types: ${ALLOWED_EXTENSIONS.join(", ").toUpperCase()}`
      );
      return false;
    }

    if (["pdf", "docx"].includes(ext)) {
      if (file.size > MAX_DOC_SIZE) {
        setClientError(
          `Document exceeds 10 MB limit (${(file.size / (1024 * 1024)).toFixed(2)} MB).`
        );
        return false;
      }
    } else {
      if (file.size > MAX_IMAGE_SIZE) {
        setClientError(
          `Image exceeds 5 MB limit (${(file.size / (1024 * 1024)).toFixed(2)} MB).`
        );
        return false;
      }
    }

    return true;
  };

  const handleFileChange = (e) => {
    const file = e.target.files && e.target.files[0];
    if (file) {
      if (validateSelectedFile(file)) {
        setSelectedFile(file);
        if (!title) {
          setTitle(file.name.replace(/\.[^/.]+$/, ""));
        }
      } else {
        setSelectedFile(null);
      }
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) {
      if (validateSelectedFile(file)) {
        setSelectedFile(file);
        if (!title) {
          setTitle(file.name.replace(/\.[^/.]+$/, ""));
        }
      } else {
        setSelectedFile(null);
      }
    }
  };

  const handleFolderChange = (e) => {
    setSelectedFolder(e.target.value);
    setFolderIdInput(e.target.value);
  };

  const handleCreateFolder = async () => {
    if (!newFolderName.trim()) return alert("Enter a folder name");
    try {
      setCreatingFolder(true);
      const response = await createFolder({
        fol_name: newFolderName.trim(),
        description: "New folder",
        ownerId: userId ? parseInt(userId, 10) : null,
      });
      setFolders((prev) => [...prev, response.data]);
      setNewFolderName("");
      setSelectedFolder(response.data.fol_id);
      setFolderIdInput(response.data.fol_id);
      alert(`Folder "${response.data.fol_name}" created with ID: ${response.data.fol_id}`);
    } catch (err) {
      console.error("Error creating folder:", err);
      alert("Failed to create folder.");
    } finally {
      setCreatingFolder(false);
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setClientError("Please select or drop a file to upload.");
      return;
    }

    if (!validateSelectedFile(selectedFile)) {
      return;
    }

    try {
      setUploading(true);
      setUploadProgress(0);
      setServerError("");
      setClientError("");

      const formData = new FormData();
      formData.append("file", selectedFile);
      if (title.trim()) formData.append("title", title.trim());
      formData.append("category", category);
      if (description.trim()) formData.append("description", description.trim());

      const tags = tagsInput
        .split(",")
        .map((t) => t.trim())
        .filter((t) => t.length > 0);
      tags.forEach((tag) => formData.append("tags", tag));

      if (userId) formData.append("userId", userId);
      const targetFolderId = folderIdInput || selectedFolder || 1;
      formData.append("folderId", targetFolderId);

      const response = await uploadDocument(formData, {
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percentCompleted = Math.round(
              (progressEvent.loaded * 100) / progressEvent.total
            );
            setUploadProgress(percentCompleted);
          }
        },
      });

      setUploadSuccess(response.data);
      setSelectedFile(null);
      setTitle("");
      setDescription("");
      setTagsInput("");
      setUploadProgress(100);
      if (fileInputRef.current) fileInputRef.current.value = "";
    } catch (err) {
      console.error("Upload error:", err.response || err);
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Upload failed. Please check your file content and permissions.";
      setServerError(msg);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="upload-page">
      <div className="upload-header">
        <h1>📂 Ready to Upload?</h1>
        <p>
          Securely store your documents with enterprise-grade AES-256 and SHA-256 verification.
          Files undergo real magic-byte deep inspection before storage.
        </p>
      </div>

      {/* Success Notification */}
      {uploadSuccess && (
        <div className="upload-alert success">
          <div className="alert-content">
            <span className="alert-icon">✅</span>
            <div>
              <strong>Document Uploaded Successfully!</strong>
              <p>
                "{uploadSuccess.title || uploadSuccess.originalFileName}" (Version v{uploadSuccess.currentVersion || 1}) has been safely vaulted.
              </p>
            </div>
          </div>
          <div className="alert-actions">
            <button
              className="btn-view-doc"
              onClick={() => navigate(`/documentsfetch/${uploadSuccess.id}`)}
            >
              View Document
            </button>
            <button
              className="btn-close-alert"
              onClick={() => setUploadSuccess(null)}
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* Server Error Notification */}
      {serverError && (
        <div className="upload-alert error">
          <span className="alert-icon">⚠️</span>
          <div>
            <strong>Server Rejected Upload:</strong>
            <p>{serverError}</p>
          </div>
          <button className="btn-close-alert" onClick={() => setServerError("")}>
            ✕
          </button>
        </div>
      )}

      {/* Client Error Notification */}
      {clientError && (
        <div className="upload-alert warning">
          <span className="alert-icon">🚫</span>
          <div>
            <strong>Validation Warning:</strong>
            <p>{clientError}</p>
          </div>
          <button className="btn-close-alert" onClick={() => setClientError("")}>
            ✕
          </button>
        </div>
      )}

      <div className="upload-sections">
        {/* Main Document Details & Drag-and-Drop */}
        <div className="upload-card main-card">
          <h2>📄 Document Details & File</h2>

          {/* Drag & Drop Zone */}
          <div
            className={`dropzone ${isDragging ? "dragging" : ""} ${selectedFile ? "has-file" : ""}`}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current && fileInputRef.current.click()}
          >
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              style={{ display: "none" }}
              accept=".pdf,.docx,.png,.jpg,.jpeg"
            />
            {selectedFile ? (
              <div className="selected-file-info">
                <span className="file-icon">📄</span>
                <div className="file-meta">
                  <strong>{selectedFile.name}</strong>
                  <span>{(selectedFile.size / 1024).toFixed(1)} KB</span>
                </div>
                <button
                  type="button"
                  className="btn-remove-file"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSelectedFile(null);
                    if (fileInputRef.current) fileInputRef.current.value = "";
                  }}
                >
                  ✕
                </button>
              </div>
            ) : (
              <div className="dropzone-prompt">
                <span className="upload-cloud-icon">☁️</span>
                <p className="primary-text">Drag and drop your file here, or click to browse</p>
                <p className="sub-text">
                  Supported formats: PDF, DOCX (up to 10 MB) | PNG, JPG, JPEG (up to 5 MB)
                </p>
              </div>
            )}
          </div>

          {/* Metadata Inputs */}
          <div className="form-group">
            <label>Document Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Q4 Financial Statement (defaults to filename)"
            />
          </div>

          <div className="form-row">
            <div className="form-group flex-1">
              <label>Category</label>
              <select value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="GENERAL">General</option>
                <option value="ID">Identification (ID / Passport)</option>
                <option value="FINANCIAL">Financial / Bank</option>
                <option value="LEGAL">Legal / Contracts</option>
                <option value="MEDICAL">Medical Records</option>
                <option value="EDUCATION">Educational Certificates</option>
                <option value="PERSONAL">Personal</option>
              </select>
            </div>
            <div className="form-group flex-1">
              <label>Tags (comma-separated)</label>
              <input
                type="text"
                value={tagsInput}
                onChange={(e) => setTagsInput(e.target.value)}
                placeholder="e.g. 2026, taxes, urgent"
              />
            </div>
          </div>

          <div className="form-group">
            <label>Description</label>
            <textarea
              rows="3"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional notes or description about this document..."
            />
          </div>

          {/* Progress Bar */}
          {uploading && (
            <div className="progress-container">
              <div className="progress-labels">
                <span>Uploading & Verifying...</span>
                <span>{uploadProgress}%</span>
              </div>
              <div className="progress-bar-track">
                <div
                  className="progress-bar-fill"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}

          <button
            className="upload-submit-btn"
            onClick={handleUpload}
            disabled={uploading}
          >
            {uploading ? `Uploading (${uploadProgress}%)...` : "Upload File"}
          </button>
        </div>

        {/* Side Panel: Folder & User Config (Maintains Full Backward Compatibility) */}
        <div className="upload-card side-card">
          <h2>📁 Locker Organization</h2>
          <p className="side-description">
            Organize documents into categorized personal locker folders.
          </p>

          <div className="form-group">
            <label>User ID</label>
            <input
              type="text"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="Enter User ID"
            />
          </div>

          <div className="form-group">
            <label>Folder ID</label>
            <input
              type="text"
              value={folderIdInput}
              onChange={(e) => setFolderIdInput(e.target.value)}
              placeholder="Enter Folder ID"
            />
          </div>

          <div className="form-group">
            <label>Select Folder</label>
            {loadingFolders ? (
              <p className="loading-sm">Loading folders...</p>
            ) : (
              <select value={selectedFolder} onChange={handleFolderChange}>
                <option value="">-- Select Folder --</option>
                {folders.map((folder) => (
                  <option key={folder.fol_id} value={folder.fol_id}>
                    {folder.fol_name}
                  </option>
                ))}
              </select>
            )}
          </div>

          <hr className="divider" />

          <h3>Create New Folder</h3>
          <div className="form-group">
            <input
              type="text"
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              placeholder="New folder name"
            />
          </div>
          <button
            className="folder-create-btn"
            onClick={handleCreateFolder}
            disabled={creatingFolder}
          >
            {creatingFolder ? "Creating..." : "Create Folder"}
          </button>
        </div>
      </div>
    </div>
  );
}
