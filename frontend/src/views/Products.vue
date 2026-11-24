<script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import api from "../api";
import { useAuth } from "../stores/auth";
const auth = useAuth(),
  loading = ref(false),
  saving = ref(false),
  rows = ref([]),
  total = ref(0);
const filters = reactive({ name: "", status: "", page: 1, size: 10 });
const productDialog = ref(false),
  productFormRef = ref(),
  product = reactive({ id: null, name: "", description: "" });
const detail = ref(null),
  drawer = ref(false),
  skuLoading = ref(false),
  skus = ref([]),
  skuDialog = ref(false),
  skuFormRef = ref();
const sku = reactive({
  id: null,
  code: "",
  name: "",
  price: 0.01,
  status: "ON_SALE",
});
const productRules = {
  name: [
    { required: true, message: "请输入商品名称", trigger: "blur" },
    { max: 100, message: "最多100字符" },
  ],
  description: [{ max: 2000, message: "最多2000字符" }],
};
const skuRules = {
  code: [
    { required: true, message: "请输入 SKU 编码" },
    {
      pattern: /^[A-Za-z0-9_-]{1,64}$/,
      message: "允许1至64位字母、数字、下划线和短横线",
    },
  ],
  name: [
    { required: true, message: "请输入规格名称" },
    { max: 100, message: "最多100字符" },
  ],
  price: [
    { required: true, message: "请输入价格" },
    {
      type: "number",
      min: 0.01,
      max: 9999999999.99,
      message: "价格范围0.01至9999999999.99",
    },
  ],
};
async function load() {
  loading.value = true;
  try {
    const data = await api.get("/products", {
      params: {
        ...filters,
        status: filters.status || undefined,
        name: filters.name || undefined,
      },
    });
    rows.value = data.records;
    total.value = data.total;
  } catch {
    /* 拦截器展示错误 */
  } finally {
    loading.value = false;
  }
}
function search() {
  filters.page = 1;
  load();
}
function reset() {
  filters.name = "";
  filters.status = "";
  search();
}
function edit(row) {
  Object.assign(product, {
    id: row?.id || null,
    name: row?.name || "",
    description: row?.description || "",
  });
  productDialog.value = true;
}
async function saveProduct() {
  if (!(await productFormRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const body = { name: product.name, description: product.description };
    if (product.id) await api.put("/products/" + product.id, body);
    else await api.post("/products", body);
    productDialog.value = false;
    ElMessage.success("商品已保存");
    await load();
  } catch {
    /* 拦截器展示错误 */
  } finally {
    saving.value = false;
  }
}
const changing = ref(null);
async function toggle(row) {
  changing.value = row.id;
  try {
    await api.patch("/products/" + row.id + "/status", {
      status: row.status === "ON_SALE" ? "OFF_SALE" : "ON_SALE",
    });
    ElMessage.success("商品状态已更新");
    await load();
  } catch {
    /* 拦截器展示错误 */
  } finally {
    changing.value = null;
  }
}
async function loadSkus() {
  skuLoading.value = true;
  try {
    skus.value = await api.get("/products/" + detail.value.id + "/skus");
  } catch {
    /* 拦截器展示错误 */
  } finally {
    skuLoading.value = false;
  }
}
async function openDetail(row) {
  detail.value = null;
  skus.value = [];
  drawer.value = true;
  skuLoading.value = true;
  try {
    detail.value = await api.get("/products/" + row.id);
    await loadSkus();
  } catch {
    drawer.value = false;
  } finally {
    skuLoading.value = false;
  }
}
function editSku(row) {
  Object.assign(sku, {
    id: row?.id || null,
    code: row?.code || "",
    name: row?.name || "",
    price: row ? Number(row.price) : 0.01,
    status: row?.status || "ON_SALE",
  });
  skuDialog.value = true;
}
async function saveSku() {
  if (!(await skuFormRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const path = "/products/" + detail.value.id + "/skus";
    const body = {
      code: sku.code,
      name: sku.name,
      price: sku.price,
      status: sku.status,
    };
    if (sku.id) await api.put(path + "/" + sku.id, body);
    else await api.post(path, body);
    skuDialog.value = false;
    ElMessage.success("SKU 已保存");
    await loadSkus();
  } catch {
    /* 拦截器展示错误 */
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <span class="eyebrow">PRODUCT CATALOG</span>
        <h1>商品与 SKU</h1>
        <p class="muted">管理商品资料与销售规格，查看当前库存。</p>
      </div>
      <el-button v-if="auth.canEdit" type="primary" size="large" @click="edit()"
        >＋ 新增商品</el-button
      >
    </div>
    <section class="catalog-panel">
      <div class="panel-heading">
        <h3>
          商品目录 <span class="count">{{ total }}</span>
        </h3>
        <span class="muted">{{
          auth.canEdit ? "运营管理" : "仓库 · 只读查询"
        }}</span>
      </div>
      <el-form class="filters" inline @submit.prevent="search"
        ><el-form-item label="商品名称"
          ><el-input
            v-model="filters.name"
            clearable
            maxlength="100"
            placeholder="搜索商品名称"
            @keyup.enter="search" /></el-form-item
        ><el-form-item label="状态"
          ><el-select
            v-model="filters.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
            ><el-option label="上架" value="ON_SALE" /><el-option
              label="下架"
              value="OFF_SALE" /></el-select></el-form-item
        ><el-form-item
          ><el-button type="primary" @click="search">查询</el-button
          ><el-button @click="reset">重置</el-button></el-form-item
        ></el-form
      ><el-table
        v-loading="loading"
        :data="rows"
        empty-text="暂无符合条件的商品"
        row-key="id"
        ><el-table-column prop="id" label="ID" width="80" /><el-table-column
          label="商品信息"
          min-width="240"
          ><template #default="{ row }"
            ><div class="product-cell">
              <span class="product-icon">{{ row.name.slice(0, 1) }}</span>
              <div>
                <strong>{{ row.name }}</strong
                ><small>{{ row.description || "暂无商品描述" }}</small>
              </div>
            </div></template
          ></el-table-column
        ><el-table-column label="销售状态" width="130"
          ><template #default="{ row }"
            ><el-tag
              :type="row.status === 'ON_SALE' ? 'success' : 'info'"
              effect="light"
              >{{ row.status === "ON_SALE" ? "上架" : "下架" }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="操作" width="260" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row)"
              >详情 / SKU</el-button
            ><el-button
              v-if="auth.canEdit"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            ><el-button
              v-if="auth.canEdit"
              link
              :loading="changing === row.id"
              @click="toggle(row)"
              >{{ row.status === "ON_SALE" ? "下架" : "上架" }}</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <div class="pagination">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </section>
    <el-dialog
      v-model="productDialog"
      :title="product.id ? '编辑商品' : '新增商品'"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
      ><el-form
        ref="productFormRef"
        :model="product"
        :rules="productRules"
        label-position="top"
        ><el-form-item label="商品名称" prop="name"
          ><el-input
            v-model="product.name"
            maxlength="100"
            show-word-limit /></el-form-item
        ><el-form-item label="商品描述" prop="description"
          ><el-input
            v-model="product.description"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
        /></el-form-item>
        <p class="muted" v-if="!product.id">
          新商品默认为下架，保存后可添加 SKU。
        </p></el-form
      ><template #footer
        ><el-button @click="productDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveProduct"
          >保存商品</el-button
        ></template
      ></el-dialog
    >
    <el-drawer
      v-model="drawer"
      title="商品详情与销售规格"
      size="min(960px, 100%)"
      ><div v-if="detail">
        <div class="detail-heading">
          <div>
            <h2>{{ detail.name }}</h2>
            <p class="muted">{{ detail.description || "暂无描述" }}</p>
          </div>
          <el-tag :type="detail.status === 'ON_SALE' ? 'success' : 'info'">{{
            detail.status === "ON_SALE" ? "上架" : "下架"
          }}</el-tag>
        </div>
        <div class="panel-heading">
          <h3>SKU 与库存</h3>
          <el-button v-if="auth.canEdit" type="primary" @click="editSku()"
            >＋ 新增 SKU</el-button
          >
        </div>
        <el-table
          :data="skus"
          v-loading="skuLoading"
          empty-text="暂无 SKU，请新增销售规格"
          ><el-table-column
            prop="code"
            label="编码"
            min-width="130"
          /><el-table-column
            prop="name"
            label="规格"
            min-width="130"
          /><el-table-column label="价格" width="110"
            ><template #default="{ row }"
              >¥{{ Number(row.price).toFixed(2) }}</template
            ></el-table-column
          ><el-table-column label="状态" width="90"
            ><template #default="{ row }">{{
              row.status === "ON_SALE" ? "启用" : "停用"
            }}</template></el-table-column
          ><el-table-column
            prop="quantity"
            label="实物"
            width="70"
          /><el-table-column
            prop="reservedQuantity"
            label="预占"
            width="70"
          /><el-table-column
            prop="availableQuantity"
            label="可用"
            width="70"
          /><el-table-column v-if="auth.canEdit" label="操作" width="70"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="editSku(row)"
                >编辑</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </div>
      <el-skeleton v-else :rows="6" animated
    /></el-drawer>
    <el-dialog
      v-model="skuDialog"
      :title="sku.id ? '编辑 SKU' : '新增 SKU'"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
      ><el-form
        ref="skuFormRef"
        :model="sku"
        :rules="skuRules"
        label-position="top"
        ><el-form-item label="SKU 编码（全局唯一）" prop="code"
          ><el-input
            v-model="sku.code"
            maxlength="64"
            placeholder="例如 BAG-BLACK" /></el-form-item
        ><el-form-item label="规格名称" prop="name"
          ><el-input
            v-model="sku.name"
            maxlength="100"
            placeholder="例如 曜石黑 / 标准款" /></el-form-item
        ><el-form-item label="销售价格（元）" prop="price"
          ><el-input-number
            v-model="sku.price"
            :min="0.01"
            :max="9999999999.99"
            :precision="2"
            :step="1" /></el-form-item
        ><el-form-item label="SKU 状态"
          ><el-radio-group v-model="sku.status"
            ><el-radio value="ON_SALE">启用</el-radio
            ><el-radio value="OFF_SALE">停用</el-radio></el-radio-group
          ></el-form-item
        ></el-form
      ><template #footer
        ><el-button @click="skuDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveSku"
          >保存 SKU</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
