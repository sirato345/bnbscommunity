import React, { useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import "./CSVUploader.css"
import Papa from 'papaparse';

const CSVUploader = () => {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const navigate = useNavigate();

  // 基础配置
  const API_BASE_URL = "http://localhost:8000";

  // 方案1：基本文件上传（正确的方式）
  const handleUpload = async () => {
    if (!file) {
      return;
    }

    Papa.parse(file, {
      header: true, // 将第一行作为header，自动跳过
      skipEmptyLines: true,
      complete: (results) => {
        console.log('解析结果:', results);
        // results.data 已经去掉了第一行（header）
        setFile(results.data);
      },
      error: (error) => {
        console.error('CSV解析错误:', error);
      }
    });

    const formData = new FormData();

    // 正确的方式：直接添加文件
    formData.append("file", file);

    setUploading(true);

    try {
      await axios.post(
        `${API_BASE_URL}/upload`,
        formData
        // 不要设置 headers，axios 会自动处理
      );
      navigate("/");
    } catch (err) {
      console.error("上传失败:", err);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="CsvUploader-div">
      <h2>CSV Upload</h2>

      <div>
        <label for="myFile">📁 Choose CSV file</label>
        <input
          type="file"
          id="myFile"
          accept=".csv,text/csv"
          className="CsvUploader-file-input"
          onChange={(e) => setFile(e.target.files?.[0] || null)}
        />
        {file && (
          <div style={{ marginTop: "10px" }}>
            <strong>Choosed CSV:</strong> {file.name} (
            {(file.size / 1024).toFixed(2)} KB)
          </div>
        )}
      </div>

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
