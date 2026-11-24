import axios from "axios";
import { ElMessage } from "element-plus";
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 15000,
});
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("orderflow-token");
  if (token) config.headers.Authorization = "Bearer " + token;
  return config;
});
api.interceptors.response.use(
  (response) => response.data.data,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem("orderflow-token");
      if (location.pathname !== "/login") location.assign("/login");
    }
    ElMessage.error(
      error.response?.data?.message || "请求失败，请检查网络或服务状态",
    );
    return Promise.reject(error);
  },
);
export default api;
