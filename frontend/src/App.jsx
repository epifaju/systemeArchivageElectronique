import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout.jsx';
import LoginPage from './pages/LoginPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import UploadPage from './pages/UploadPage.jsx';
import SearchPage from './pages/SearchPage.jsx';
import DocumentListPage from './pages/DocumentListPage.jsx';
import DocumentViewerPage from './pages/DocumentViewerPage.jsx';
import AdminOnly from './components/AdminOnly.jsx';
import AdminHomePage from './pages/AdminHomePage.jsx';
import AdminUsersPage from './pages/AdminUsersPage.jsx';
import AdminDocumentTypesPage from './pages/AdminDocumentTypesPage.jsx';
import RoleGate from './components/RoleGate.jsx';
import AdminOcrQueuePage from './pages/AdminOcrQueuePage.jsx';
import AdminAuditPage from './pages/AdminAuditPage.jsx';
import AdminDocumentsDeletedPage from './pages/AdminDocumentsDeletedPage.jsx';
import AdminSystemSettingsPage from './pages/AdminSystemSettingsPage.jsx';

function Protected({ children }) {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <AppLayout>{children}</AppLayout>;
}

function AdminRoute({ children }) {
  return (
    <Protected>
      <AdminOnly>{children}</AdminOnly>
    </Protected>
  );
}

function OcrQueueRoute({ children }) {
  return (
    <Protected>
      <RoleGate roles={['ADMIN', 'ARCHIVISTE']}>{children}</RoleGate>
    </Protected>
  );
}

export default function App() {
  return (
    <div>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/dashboard"
          element={
            <Protected>
              <DashboardPage />
            </Protected>
          }
        />
        <Route
          path="/upload"
          element={
            <Protected>
              <UploadPage />
            </Protected>
          }
        />
        <Route
          path="/search"
          element={
            <Protected>
              <SearchPage />
            </Protected>
          }
        />
        <Route
          path="/documents"
          element={
            <Protected>
              <DocumentListPage />
            </Protected>
          }
        />
        <Route
          path="/documents/:id"
          element={
            <Protected>
              <DocumentViewerPage />
            </Protected>
          }
        />
        <Route path="/admin" element={<AdminRoute><AdminHomePage /></AdminRoute>} />
        <Route path="/admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
        <Route path="/admin/document-types" element={<AdminRoute><AdminDocumentTypesPage /></AdminRoute>} />
        <Route path="/admin/documents-deleted" element={<AdminRoute><AdminDocumentsDeletedPage /></AdminRoute>} />
        <Route path="/admin/system-settings" element={<AdminRoute><AdminSystemSettingsPage /></AdminRoute>} />
        <Route path="/admin/ocr-queue" element={<OcrQueueRoute><AdminOcrQueuePage /></OcrQueueRoute>} />
        <Route
          path="/admin/audit"
          element={
            <Protected>
              <RoleGate roles={['ADMIN', 'AUDITEUR']}>
                <AdminAuditPage />
              </RoleGate>
            </Protected>
          }
        />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </div>
  );
}
