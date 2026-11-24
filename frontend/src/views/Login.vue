<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
const router = useRouter(),
  auth = useAuth(),
  formRef = ref(),
  busy = ref(false);
const form = reactive({ username: "", password: "" });
const rules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};
async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  busy.value = true;
  try {
    await auth.login(form);
    router.replace("/");
  } catch {
    /* 统一拦截器展示错误 */
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="login-page">
    <section class="login-story">
      <div class="brand"><b class="brand-icon">O</b> OrderFlow</div>
      <div>
        <span class="eyebrow">OPERATIONS WORKSPACE</span>
        <h1>让每一件商品，<br />有序流转。</h1>
        <p>
          连接商品、库存与履约的日常工作。<br />从清晰的数据开始，掌握业务每一步。
        </p>
        <div class="story-line">
          <span>商品管理</span><i>→</i><span>库存可视</span><i>→</i
          ><span>有序履约</span>
        </div>
      </div>
      <small>OrderFlow · 企业内部管理系统</small>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <span class="eyebrow">欢迎回来</span>
        <h2>登录工作台</h2>
        <p class="muted">使用企业内部账号继续</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submit"
          ><el-form-item label="用户名" prop="username"
            ><el-input
              v-model="form.username"
              size="large"
              maxlength="50"
              autocomplete="username"
              placeholder="请输入用户名" /></el-form-item
          ><el-form-item label="密码" prop="password"
            ><el-input
              v-model="form.password"
              type="password"
              show-password
              size="large"
              maxlength="72"
              autocomplete="current-password"
              placeholder="请输入密码" /></el-form-item
          ><el-button
            type="primary"
            size="large"
            native-type="submit"
            :loading="busy"
            style="width: 100%"
            >登录工作台</el-button
          ></el-form
        >
        <p class="login-note">
          账号由系统管理员配置。开发账号请参考项目 README。
        </p>
      </div>
    </section>
  </main>
</template>
