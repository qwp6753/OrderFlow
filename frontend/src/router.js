import { createRouter, createWebHistory } from "vue-router";
import Login from "./views/Login.vue";
import AppLayout from "./components/AppLayout.vue";
import Products from "./views/Products.vue";
import { useAuth } from "./stores/auth";
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: Login },
    {
      path: "/",
      component: AppLayout,
      children: [
        { path: "", redirect: "/analytics" },
        {
          path: "analytics",
          name: "analytics",
          component: () => import("./views/Analytics.vue"),
          meta: { requiresEdit: true },
        },
        { path: "products", component: Products },
        { path: "returns", component: () => import("./views/Returns.vue") },
        { path: "inventory", component: () => import("./views/Inventory.vue") },
        { path: "orders", component: () => import("./views/Orders.vue") },
        {
          path: "orders/new",
          component: () => import("./views/OrderCreate.vue"),
          meta: { requiresEdit: true },
        },
        {
          path: "orders/:id",
          component: () => import("./views/OrderDetail.vue"),
        },
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/products" },
  ],
});
router.beforeEach(async (to) => {
  if (to.path !== "/login" && !sessionStorage.getItem("orderflow-token"))
    return "/login";
  if (to.meta.requiresEdit) {
    const auth = useAuth();
    if (!auth.user) {
      try {
        await auth.load();
      } catch {
        return "/login";
      }
    }
    if (!auth.canEdit)
      return to.name === "analytics" ? "/inventory" : "/orders";
  }
});
export default router;
