import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import App from './App';
import './styles.css';
import { LanguageProvider } from './i18n';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <LanguageProvider><BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter></LanguageProvider>
  </React.StrictMode>,
);
