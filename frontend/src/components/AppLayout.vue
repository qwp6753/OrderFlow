<script setup>
import { onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
const auth = useAuth(),
  route = useRoute(),
  router = useRouter();
const roleNames = {
  ADMIN: "系统管理员",
  OPERATOR: "运营人员",
  WAREHOUSE: "仓库人员",
};
onMounted(() => auth.load().catch(() => {}));
function logout() {
  auth.logout();
  router.replace("/login");
}
</script>
<template>
  <div class="workspace">
    <aside class="sidebar">
      <div class="brand"><b class="brand-icon">O</b> OrderFlow</div>
      <div class="sidebar-caption">业务工作台</div>
      <router-link
        v-if="auth.canEdit"
        class="menu-item"
        active-class="menu-active"
        to="/analytics"
        >▥ 运营看板</router-link
      >
      <router-link class="menu-item" active-class="menu-active" to="/products"
        ><span>▦</span> 商品与 SKU</router-link
      ><router-link
        class="menu-item"
        :class="{ 'menu-active': route.path.startsWith('/orders') }"
        to="/orders"
        ><span>▤</span> 订单管理</router-link
      >
      <router-link class="menu-item" active-class="menu-active" to="/inventory"
        ><span>▥</span> 库存管理</router-link
      >
      <router-link class="menu-item" active-class="menu-active" to="/returns"
        >↶ 售后退货</router-link
      >
      <div class="sidebar-footer">
        <span class="status-dot"></span> OrderFlow 履约管理
      </div>
    </aside>
    <div class="main">
      <header class="topbar">
        <div>
          业务管理 <span class="slash">/</span>
          {{
            route.name === "analytics"
              ? "运营看板"
              : route.path === "/returns"
                ? "售后退货"
                : route.path.startsWith("/orders")
                  ? "订单管理"
                  : route.path.startsWith("/inventory")
                    ? "库存管理"
                    : "商品与 SKU"
          }}
        </div>
        <div class="user-area">
          <span class="avatar">{{
            auth.user?.displayName?.slice(0, 1) || "用"
          }}</span>
          <div>
            <strong>{{ auth.user?.displayName }}</strong
            ><small>{{
              auth.user?.roles.map((r) => roleNames[r]).join(" / ")
            }}</small>
          </div>
          <el-button text @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="content"><router-view /></main>
    </div>
  </div>
</template>
