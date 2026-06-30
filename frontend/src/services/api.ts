import axios from 'axios';

// API base URL is build-time configurable: set VITE_API_BASE_URL to the deployed backend URL.
// Falls back to the local backend for development.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor to inject JWT bearer token into requests
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Interceptor to handle JWT token expiration or invalidation (401)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('userRole');
      
      const currentPath = window.location.hash || window.location.pathname;
      if (currentPath !== '#/' && currentPath !== '#/login' && currentPath !== '#/register') {
        window.location.hash = '#/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
