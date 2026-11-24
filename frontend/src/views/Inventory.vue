<script setup>
import { ref, reactive, onMounted, watch } from "vue";
import { useAuth } from "../stores/auth";
import { movementTypes } from "../stock-types";
import { ElMessage } from "element-plus";
import api from "../api";
const auth = useAuth(),
  tab = ref("stock"),
  loading = ref(false),
  ledgerLoading = ref(false),
  rows = ref([]),
  total = ref(0),
  movements = ref([]),
  movementTotal = ref(0),
  dates = ref([]);
const filters = reactive({ page: 1, size: 10, keyword: "" }),
  ledger = reactive({ page: 1, size: 10, skuCode: "", type: "" });
const receiptDialog = ref(false),
  receiptFormRef = ref(),
  saving = ref(false),
  selected = ref(null),
  form = reactive({ quantity: 1, remark: "" });
const rules = {
  quantity: [
    { required: true, message: "请输入正整数入库数量" },
    {
      type: "number",
      min: 1,
      max: 2147483647,
      message: "数量必须为1至2147483647的整数",
    },
  ],
  remark: [{ max: 500, message: "备注最多500字符" }],
};
async function load() {
  loading.value = true;
  try {
    const data = await api.get("/inventory", {
      params: { ...filters, keyword: filters.keyword || undefined },
    });
    rows.value = data.records;
    total.value = data.total;
  } catch {
    /* 全局错误提示 */
  } finally {
    loading.value = false;
  }
}
async function loadLedger() {
  ledgerLoading.value = true;
  try {
    const data = await api.get("/inventory/movements", {
      params: {
        ...ledger,
        skuCode: ledger.skuCode || undefined,
        type: ledger.type || undefined,
        from: dates.value?.[0],
        to: dates.value?.[1],
      },
    });
    movements.value = data.records;
    movementTotal.value = data.total;
  } catch {
    /* 全局错误提示 */
  } finally {
    ledgerLoading.value = false;
  }
}
function search() {
  filters.page = 1;
  load();
}
function searchLedger() {
  ledger.page = 1;
  loadLedger();
}
function changeTab(value) {
  if (value === "movements") loadLedger();
  else load();
}
function showLedger(row) {
  ledger.skuCode = row.skuCode;
  ledger.type = "";
  ledger.page = 1;
  dates.value = [];
  tab.value = "movements";
}
function openReceipt(row) {
  selected.value = row;
  form.quantity = 1;
  form.remark = "";
  receiptDialog.value = true;
}
async function receive() {
  if (!(await receiptFormRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api.post("/inventory/" + selected.value.skuId + "/receipts", {
      ...form,
    });
    receiptDialog.value = false;
    ElMessage.success("入库成功，实物库存已增加");
    await load();
  } catch {
    /* 服务端拒绝时保留表单 */
  } finally {
    saving.value = false;
  }
}
watch(tab, changeTab);
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <span class="eyebrow">WAREHOUSE INVENTORY</span>
        <h1>库存管理</h1>
        <p class="muted">
          单仓库存与可追溯流水，可用库存 = 实物库存 − 预占库存。
        </p>
      </div>
      <span class="muted">{{
        auth.canWarehouse ? "仓库作业" : "库存只读查询"
      }}</span>
    </div>
    <el-tabs v-model="tab"
      ><el-tab-pane label="SKU 库存" name="stock"
        ><section class="catalog-panel">
          <div class="panel-heading">
            <h3>
              SKU 库存 <span class="count">{{ total }}</span>
            </h3>
          </div>
          <el-form inline class="filters" @submit.prevent="search"
            ><el-form-item label="商品 / SKU"
              ><el-input
                v-model="filters.keyword"
                placeholder="名称或 SKU 编码"
                clearable
                maxlength="100"
                @keyup.enter="search" /></el-form-item
            ><el-form-item
              ><el-button type="primary" @click="search">查询库存</el-button
              ><el-button
                @click="
                  filters.keyword = '';
                  search();
                "
                >重置</el-button
              ></el-form-item
            ></el-form
          ><el-table
            :data="rows"
            v-loading="loading"
            row-key="skuId"
            empty-text="暂无匹配的库存"
            ><el-table-column label="商品与 SKU" min-width="270"
              ><template #default="{ row }"
                ><strong>{{ row.productName }}</strong>
                <div class="muted">
                  {{ row.skuCode }} · {{ row.skuName }}
                </div></template
              ></el-table-column
            ><el-table-column
              prop="quantity"
              label="实物库存"
              width="120"
            /><el-table-column
              prop="reservedQuantity"
              label="预占库存"
              width="120"
            /><el-table-column
              prop="availableQuantity"
              label="可用库存"
              width="120"
            /><el-table-column label="操作" width="190" fixed="right"
              ><template #default="{ row }"
                ><el-button
                  v-if="auth.canWarehouse"
                  link
                  type="primary"
                  @click="openReceipt(row)"
                  >入库</el-button
                ><el-button link type="primary" @click="showLedger(row)"
                  >查看流水</el-button
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
          </div></section></el-tab-pane
      ><el-tab-pane label="库存流水" name="movements"
        ><section class="catalog-panel">
          <div class="panel-heading">
            <h3>
              库存流水 <span class="count">{{ movementTotal }}</span>
            </h3>
            <span class="muted"
              >入库按流水编号追溯，订单变动关联订单编号。</span
            >
          </div>
          <el-form inline class="filters" @submit.prevent="searchLedger"
            ><el-form-item label="SKU 编码"
              ><el-input
                v-model="ledger.skuCode"
                clearable
                maxlength="64"
                placeholder="完整 SKU 编码" /></el-form-item
            ><el-form-item label="业务类型"
              ><el-select
                v-model="ledger.type"
                clearable
                placeholder="全部类型"
                style="width: 150px"
                ><el-option
                  v-for="type in [
                    'RECEIPT',
                    'RESERVE',
                    'RELEASE',
                    'SHIP',
                    'RETURN',
                  ]"
                  :key="type"
                  :value="type"
                  :label="movementTypes[type]" /></el-select></el-form-item
            ><el-form-item label="变动时间"
              ><el-date-picker
                v-model="dates"
                type="datetimerange"
                value-format="YYYY-MM-DDTHH:mm:ss"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                style="width: 350px" /></el-form-item
            ><el-form-item
              ><el-button type="primary" @click="searchLedger"
                >查询流水</el-button
              ><el-button
                @click="
                  ledger.skuCode = '';
                  ledger.type = '';
                  dates = [];
                  searchLedger();
                "
                >重置</el-button
              ></el-form-item
            ></el-form
          ><el-table
            :data="movements"
            v-loading="ledgerLoading"
            empty-text="暂无匹配流水"
            ><el-table-column
              prop="id"
              label="流水编号"
              width="110"
            /><el-table-column
              prop="skuCode"
              label="SKU"
              min-width="150"
            /><el-table-column label="类型" width="120"
              ><template #default="{ row }">{{
                movementTypes[row.movementType]
              }}</template></el-table-column
            ><el-table-column
              prop="quantityDelta"
              label="实物变动"
              width="110"
            /><el-table-column
              prop="reservedDelta"
              label="预占变动"
              width="110"
            /><el-table-column
              prop="quantityAfter"
              label="变更后实物"
              width="130"
            /><el-table-column
              prop="reservedAfter"
              label="变更后预占"
              width="130"
            /><el-table-column
              prop="orderNo"
              label="关联订单"
              min-width="290"
            /><el-table-column
              prop="remark"
              label="备注"
              min-width="160"
            /><el-table-column
              prop="operatorName"
              label="操作人"
              width="130"
            /><el-table-column label="时间" min-width="190"
              ><template #default="{ row }">{{
                row.createdAt.replace("T", " ")
              }}</template></el-table-column
            ></el-table
          >
          <div class="pagination">
            <el-pagination
              v-model:current-page="ledger.page"
              v-model:page-size="ledger.size"
              :total="movementTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @current-change="loadLedger"
              @size-change="searchLedger"
            />
          </div></section></el-tab-pane></el-tabs
    ><el-dialog
      v-model="receiptDialog"
      title="库存入库"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
      ><p class="muted">
        {{ selected?.productName }} · {{ selected?.skuCode }}
      </p>
      <el-form
        ref="receiptFormRef"
        :model="form"
        :rules="rules"
        label-position="top"
        ><el-form-item label="入库数量" prop="quantity"
          ><el-input-number
            v-model="form.quantity"
            :min="1"
            :max="2147483647"
            :precision="0" /></el-form-item
        ><el-form-item label="入库备注" prop="remark"
          ><el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button @click="receiptDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="receive"
          >确认入库</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
