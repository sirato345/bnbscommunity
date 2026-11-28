import React, { useState } from "react";
import axios from "axios";
import { useRouter } from "next/navigation";
import "./CSVUploader.css";
// import Papa from "papaparse";

const CSVUploader = () => {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(false);
  const router = useRouter();

  // 基础配置
  const API_BASE_URL = "https://bnbs-django-275599637949.asia-northeast1.run.app";
  // const API_BASE_URL = "http://localhost:8000";

  // 方案1：基本文件上传（正确的方式）
  const handleUpload = async () => {
    if (!file) {
      return;
    }

    // Papa.parse(file, {
    //   header: true, // 将第一行作为header，自动跳过
    //   skipEmptyLines: true,
    //   complete: (results) => {
    //     console.log("解析结果:", results);
    //     // results.data 已经去掉了第一行（header）
    //     setFile(results.data);
    //   },
    //   error: (error) => {
    //     console.error("CSV解析错误:", error);
    //   },
    // });

    const formData = new FormData();

    // 正确的方式：直接添加文件
    formData.append("file", file);

    setUploading(true);
    setError(false);

    try {
      await axios.post(`${API_BASE_URL}/upload`, formData);
      router.push("/holders");
    } catch (err) {
      console.error("上传失败:", err);
      setError(true);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="CsvUploader-div">
      <h2 className="CsvUploader-title">CSV Upload</h2>

      <div>
        <label htmlFor="myFile" className="CsvUploader-dropzone">
          <span>📁 Choose CSV file</span>
          <span className="CsvUploader-dropzone-hint">.csv</span>
        </label>
        <input
          type="file"
          id="myFile"
          accept=".csv,text/csv"
          className="CsvUploader-file-input"
          onChange={(e) => setFile(e.target.files?.[0] || null)}
        />
        {file && (
          <div className="CsvUploader-file-info">
            <strong>Choosed CSV:</strong> {file.name} (
            {(file.size / 1024).toFixed(2)} KB)
          </div>
        )}
      </div>
      {error && (
        <p role="alert" className="CsvUploader-error">
          Upload failed. Please try again.
        </p>
      )}

      <div>
        <button
          onClick={handleUpload}
          disabled={!file || uploading}
          className="CsvUploader-upload-btn"
        >
          {uploading ? "Uploading..." : "Upload"}
        </button>
      </div>
    </div>
  );
};

export default CSVUploader;
