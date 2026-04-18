import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { pdfjs } from 'react-pdf';
import pdfjsWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import App from './App.jsx';
import { useAuthStore, USER_SUMMARY_KEY } from './store/authStore';
import './i18n';
import './index.css';
import 'react-pdf/dist/esm/Page/AnnotationLayer.css';
import 'react-pdf/dist/esm/Page/TextLayer.css';

// Worker servi depuis le même origine que l’app (Vite → nginx) — fiable en Docker / sans Internet.
pdfjs.GlobalWorkerOptions.workerSrc = pdfjsWorker;

const client = new QueryClient();

try {
  const raw = localStorage.getItem(USER_SUMMARY_KEY);
  if (raw) {
    useAuthStore.setState({ user: JSON.parse(raw) });
  }
} catch {
  /* ignore */
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);
