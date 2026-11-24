<script setup>
import { reactive, ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import api from "../api";
const router = useRouter(),
  formRef = ref(),
  saving = ref(false),
  products = ref([]),
  skus = ref([]),
  loadingProducts = ref(false),
  loadingSkus = ref(false),
  productId = ref(),
  selectedProductName = ref(""),
  skuId = ref(),
  quantity = ref(1),
  lines = ref([]);
const form = reactive({
  recipientName: "",
  recipientPhone: "",
  recipientAddress: "",
  remark: "",
});
const rules = {
  recipientName: [
    { required: true, message: "请输入收件人", trigger: "blur" },
    { max: 100, message: "最多100字符" },
  ],
  recipientPhone: [
    { required: true, message: "请输入联系电话", trigger: "blur" },
    { max: 30, message: "最多30字符" },
  ],
  recipientAddress: [
    { required: true, message: "请输入收件地址", trigger: "blur" },
    { max: 500, message: "最多500字符" },
  ],
  remark: [{ max: 500, message: "最多500字符" }],
};
const selected = computed(() => skus.value.find((s) => s.id === skuId.value));
const availableSkus = computed(() =>
  skus.value.filter(
    (s) =>
      s.status === "ON_SALE" &&
      !lines.value.some((line) => line.skuId === s.id),
  ),
);
const estimate = computed(() =>
  lines.value.reduce(
    (sum, line) => sum + Number(line.price) * line.quantity,
    0,
  ),
);
let searchSequence = 0;
async function searchProducts(name = "") {
  const sequence = ++searchSequence;
  loadingProducts.value = true;
  try {
    const data = await api.get("/products", {
      params: { name, status: "ON_SALE", size: 20 },
    });
    if (sequence === searchSequence) products.value = data.records;
  } catch {
    /* 拦截器提示 */
  } finally {
    if (sequence === searchSequence) loadingProducts.value = false;
  }
}
async function loadSkus(id) {
  selectedProductName.value = products.value.find((p) => p.id === id).name;
  skuId.value = undefined;
  skus.value = [];
  loadingSkus.value = true;
  try {
    const data = await api.get("/products/" + id + "/skus");
    if (productId.value === id) skus.value = data;
  } catch {
    /* 拦截器提示 */
  } finally {
    if (productId.value === id) loadingSkus.value = false;
  }
}
function add() {
  if (!selected.value) return;
  if (!Number.isInteger(quantity.value) || quantity.value < 1) {
    ElMessage.warning("请输入有效购买数量");
    return;
  }
  if (lines.value.length >= 50) {
    ElMessage.warning("一个订单最多50个 SKU");
    return;
  }
  lines.value.push({
    skuId: selected.value.id,
    productName: selectedProductName.value,
    code: selected.value.code,
    name: selected.value.name,
    price: selected.value.price,
    availableQuantity: selected.value.availableQuantity,
    quantity: quantity.value,
  });
  skuId.value = undefined;
  quantity.value = 1;
}
async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (!lines.value.length) {
    ElMessage.warning("请至少添加一个 SKU");
    return;
  }
  if (
    lines.value.some(
      (line) => !Number.isInteger(line.quantity) || line.quantity < 1,
    )
  ) {
    ElMessage.warning("请填写有效购买数量");
    return;
  }
  saving.value = true;
  try {
    const order = await api.post("/orders", {
      ...form,
      items: lines.value.map(({ skuId, quantity }) => ({ skuId, quantity })),
    });
    ElMessage.success("订单已创建并预占库存");
    router.replace("/orders/" + order.id);
  } catch {
    /* 后端库存/状态错误由拦截器显示，保留表单供修改 */
  } finally {
    saving.value = false;
  }
}
onMounted(() => searchProducts());
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <span class="eyebrow">NEW ORDER</span>
        <h1>创建订单</h1>
        <p class="muted">
          提交时按当前售价计价并预占库存，任一 SKU 不足将整单回滚。
        </p>
      </div>
      <el-button @click="router.push('/orders')">返回订单列表</el-button>
    </div>
    <el-alert
      class="order-notice"
      type="info"
      title="仅内部演示，不接入真实支付；页面价格为估算，最终金额与可用库存以后端提交结果为准。"
      :closable="false"
    />
    <section class="catalog-panel form-panel">
      <h3>收件信息</h3>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top"
        ><div class="form-grid">
          <el-form-item label="收件人" prop="recipientName"
            ><el-input
              v-model="form.recipientName"
              maxlength="100" /></el-form-item
          ><el-form-item label="联系电话" prop="recipientPhone"
            ><el-input v-model="form.recipientPhone" maxlength="30"
          /></el-form-item>
        </div>
        <el-form-item label="收件地址" prop="recipientAddress"
          ><el-input
            v-model="form.recipientAddress"
            maxlength="500" /></el-form-item
        ><el-form-item label="订单备注" prop="remark"
          ><el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit /></el-form-item
      ></el-form>
    </section>
    <section class="catalog-panel form-panel">
      <h3>选择商品与 SKU</h3>
      <div class="sku-picker">
        <el-select
          v-model="productId"
          filterable
          remote
          reserve-keyword
          :remote-method="searchProducts"
          :loading="loadingProducts"
          placeholder="搜索上架商品"
          aria-label="选择商品"
          @change="loadSkus"
          ><el-option
            v-for="p in products"
            :key="p.id"
            :label="p.name"
            :value="p.id" /></el-select
        ><el-select
          v-model="skuId"
          :loading="loadingSkus"
          :disabled="!productId"
          placeholder="选择销售规格"
          aria-label="选择 SKU"
          ><el-option
            v-for="s in availableSkus"
            :key="s.id"
            :label="s.code + ' · ' + s.name + ' · 可用 ' + s.availableQuantity"
            :value="s.id" /></el-select
        ><el-input-number
          v-model="quantity"
          :min="1"
          :max="1000000"
          :precision="0"
          aria-label="添加数量"
        /><el-button type="primary" :disabled="!selected" @click="add"
          >添加 SKU</el-button
        >
      </div>
      <el-table
        :data="lines"
        row-key="skuId"
        empty-text="请选择商品规格并添加至订单"
        ><el-table-column label="商品与规格" min-width="240"
          ><template #default="{ row }"
            ><strong>{{ row.productName }}</strong>
            <div class="muted">{{ row.code }} · {{ row.name }}</div></template
          ></el-table-column
        ><el-table-column label="当前单价" width="130"
          ><template #default="{ row }"
            >¥{{ Number(row.price).toFixed(2) }}</template
          ></el-table-column
        ><el-table-column
          prop="availableQuantity"
          label="可用库存"
          width="110"
        /><el-table-column label="购买数量" width="200"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.quantity"
              :min="1"
              :max="1000000"
              :precision="0"
              aria-label="购买数量" /></template></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button
              link
              type="danger"
              @click="lines = lines.filter((line) => line.skuId !== row.skuId)"
              >移除</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <div class="order-total">
        <span
          >估算总金额 <strong>¥{{ estimate.toFixed(2) }}</strong></span
        ><el-button
          type="primary"
          size="large"
          :loading="saving"
          @click="submit"
          >提交订单并预占库存</el-button
        >
      </div>
    </section>
  </div>
</template>
