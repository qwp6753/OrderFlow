import { defineStore } from "pinia";
import { ref, computed } from "vue";
import api from "../api";
export const useAuth = defineStore("auth", () => {
  const user = ref(null);
  const canEdit = computed(() =>
    user.value?.roles.some((role) => ["ADMIN", "OPERATOR"].includes(role)),
  );
  const canWarehouse = computed(() =>
    user.value?.roles.some((role) => ["ADMIN", "WAREHOUSE"].includes(role)),
  );
  async function load() {
    user.value = await api.get("/auth/me");
  }
  async function login(form) {
    const result = await api.post("/auth/login", form);
    sessionStorage.setItem("orderflow-token", result.token);
    await load();
  }
  function logout() {
    sessionStorage.removeItem("orderflow-token");
    user.value = null;
  }
  return { user, canEdit, canWarehouse, load, login, logout };
});
