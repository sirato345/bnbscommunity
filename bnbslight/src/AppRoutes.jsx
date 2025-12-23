import { Routes, Route } from "react-router-dom";
import App from "./App.jsx";
import CSVUploader from "./CSVUploader.jsx";

export const AppRoutes = () => {
  return (
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/csv" element={<CSVUploader />} />
    </Routes>
  );
};

export default AppRoutes;
