<script setup>
import { computed, onMounted, ref, reactive } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useAuth } from "../stores/auth";
import api from "../api";

const auth = useAuth();
const route = useRoute();
const statuses = {
  PENDING_REVIEW: "待审核",
  REJECTED: "已拒绝",
  PENDING_RECEIPT: "待验收",
  COMPLETED: "退货完成",
};
const filters = reactive({
  orderId: route.query.orderId || "",
  status: "",
  page: 1,
});
const rows = ref([]),
  total = ref(0),
  loading = ref(false),
  busy = ref(false);
const current = ref(null),
  detailOpen = ref(false),
  createOpen = ref(false);
const options = ref([]);
const draft = reactive({ orderItemId: null, quantity: 1, reason: "" });
const selected = computed(() =>
  options.value.find((row) => row.item.id === draft.orderItemId),
);

async function load() {
  loading.value = true;
  try {
    const result = await api.get("/returns", {
      params: {
        ...filters,
        orderId: filters.orderId || undefined,
        status: filters.status || undefined,
      },
    });
    rows.value = result.records;
    total.value = result.total;
  } catch {
    /* 全局请求提示 */
  } finally {
    loading.value = false;
  }
}

function search() {
  filters.page = 1;
  load();
}

async function openCreate() {
  busy.value = true;
  try {
    options.value = await api.get("/returns/availability", {
      params: { orderId: filters.orderId },
    });
    Object.assign(draft, { orderItemId: null, quantity: 1, reason: "" });
    createOpen.value = true;
  } catch {
    /* 全局请求提示 */
  } finally {
    busy.value = false;
  }
}

async function create() {
  busy.value = true;
  try {
    const created = await api.post("/returns", draft);
    createOpen.value = false;
    ElMessage.success("退货申请已提交");
    await load();
    await openDetail(created.id);
  } catch {
    /* 保留表单供修改 */
  } finally {
    busy.value = false;
  }
}

async function openDetail(id) {
  try {
    current.value = await api.get("/returns/" + id);
    detailOpen.value = true;
  } catch {
    /* 全局请求提示 */
  }
}

async function act(action) {
  const labels = {
    approve: "审核通过",
    reject: "拒绝申请",
    note: "记录验收异常",
    receive: "确认全部验收入库",
  };
  let body = {};
  try {
    if (action === "approve") {
      await ElMessageBox.confirm("通过后交由仓库验收。", labels[action]);
    } else {
      const receipt = action === "receive";
      const { value } = await ElMessageBox.prompt(
        receipt
          ? "确认商品全部符合重新销售条件。可填写验收备注。"
          : "请填写原因或处理备注。",
        labels[action],
        {
          inputType: "textarea",
          inputValidator: (value) =>
            value?.length > 500
              ? "最多500字"
              : !receipt && !value?.trim()
                ? "请填写原因"
                : true,
        },
      );
      body = receipt ? { remark: value || "" } : { reason: value };
    }
  } catch {
    return;
  }

  const id = current.value.request.id;
  busy.value = true;
  try {
    await api.post("/returns/" + id + "/" + action, body);
    ElMessage.success(labels[action] + "成功");
  } catch {
    /* 全局请求提示 */
  } finally {
    await openDetail(id);
    await load();
    busy.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <div class="page-heading">
      <div>
        <span class="eyebrow">AFTER SALES</span>
        <h1>售后退货</h1>
        <p class="muted">从退货申请、运营审核到仓库验收入库。</p>
      </div>
    </div>
    <el-alert
      class="order-notice"
      type="info"
      :closable="false"
      title="本系统仅供演示。预计退款金额按原成交价计算，退货完成表示已验收入库。本系统不执行真实资金退款。"
    />
    <section class="catalog-panel">
      <el-form inline class="filters" @submit.prevent="search">
        <el-form-item label="原订单 ID"
          ><el-input
            v-model="filters.orderId"
            clearable
            placeholder="从订单详情进入可自动填写"
        /></el-form-item>
        <el-form-item label="售后状态">
          <el-select
            v-model="filters.status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option
              v-for="(label, key) in statuses"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button native-type="submit" type="primary">查询售后</el-button>
          <el-button
            v-if="auth.canEdit"
            :disabled="!filters.orderId"
            :loading="busy"
            @click="openCreate"
            >申请退货</el-button
          >
        </el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="rows" empty-text="暂无售后申请">
        <el-table-column prop="returnNo" label="售后单号" min-width="300" />
        <el-table-column prop="orderId" label="原订单 ID" width="110" />
        <el-table-column label="状态" width="120"
          ><template #default="{ row }"
            ><el-tag>{{ statuses[row.status] }}</el-tag></template
          ></el-table-column
        >
        <el-table-column
          prop="reason"
          label="申请原因"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="申请时间" min-width="180"
          ><template #default="{ row }">{{
            row.createdAt.replace("T", " ")
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="100"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row.id)"
              >查看售后</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="filters.page"
          :page-size="10"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </section>

    <el-dialog v-model="createOpen" title="申请退货" width="550px">
      <el-form label-position="top">
        <el-form-item label="原订单明细">
          <el-select
            v-model="draft.orderItemId"
            placeholder="选择有剩余可退数量的明细"
            @change="draft.quantity = 1"
          >
            <el-option
              v-for="row in options"
              :key="row.item.id"
              :value="row.item.id"
              :disabled="row.remaining <= 0"
              :label="row.item.skuCode + ' · 可退 ' + row.remaining"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="退货数量"
          ><el-input-number
            v-model="draft.quantity"
            :precision="0"
            :min="1"
            :max="selected?.remaining || 1"
        /></el-form-item>
        <el-form-item label="退货原因"
          ><el-input
            v-model="draft.reason"
            type="textarea"
            maxlength="500"
            show-word-limit
        /></el-form-item>
        <p v-if="selected">
          预计退款金额 ¥{{
            (selected.item.unitPrice * draft.quantity).toFixed(2)
          }}
        </p>
      </el-form>
      <template #footer
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="!selected || !draft.quantity || !draft.reason.trim()"
          @click="create"
          >提交退货申请</el-button
        ></template
      >
    </el-dialog>

    <el-dialog v-model="detailOpen" title="售后详情" width="min(840px, 94vw)">
      <template v-if="current">
        <p>
          <strong>{{ current.request.returnNo }}</strong
          >　<el-tag>{{ statuses[current.request.status] }}</el-tag>
        </p>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="原订单"
            ><router-link :to="'/orders/' + current.request.orderId">{{
              current.request.orderId
            }}</router-link></el-descriptions-item
          >
          <el-descriptions-item label="申请人 ID">{{
            current.request.createdBy
          }}</el-descriptions-item>
          <el-descriptions-item label="申请原因">{{
            current.request.reason
          }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{
            current.request.createdAt.replace("T", " ")
          }}</el-descriptions-item>
          <el-descriptions-item label="审核人 ID">{{
            current.request.reviewedBy ?? "—"
          }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{
            current.request.reviewedAt?.replace("T", " ") ?? "—"
          }}</el-descriptions-item>
          <el-descriptions-item
            v-if="current.request.rejectionReason"
            label="拒绝原因"
            :span="2"
            >{{ current.request.rejectionReason }}</el-descriptions-item
          >
          <el-descriptions-item label="仓库处理人 ID">{{
            current.request.receivedBy ?? "—"
          }}</el-descriptions-item>
          <el-descriptions-item label="验收时间">{{
            current.request.receivedAt?.replace("T", " ") ?? "—"
          }}</el-descriptions-item>
          <el-descriptions-item label="验收备注" :span="2">{{
            current.request.receiptRemark || "—"
          }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="current.lines">
          <el-table-column prop="original.skuCode" label="原 SKU" />
          <el-table-column prop="item.quantity" label="退货数量" width="110" />
          <el-table-column label="预计退款金额" width="160"
            ><template #default="{ row }"
              >¥{{ Number(row.item.refundAmount).toFixed(2) }}</template
            ></el-table-column
          >
        </el-table>
      </template>
      <template #footer>
        <template
          v-if="auth.canEdit && current?.request.status === 'PENDING_REVIEW'"
        >
          <el-button :loading="busy" @click="act('reject')">拒绝申请</el-button>
          <el-button type="primary" :loading="busy" @click="act('approve')"
            >审核通过</el-button
          >
        </template>
        <template
          v-if="
            auth.canWarehouse && current?.request.status === 'PENDING_RECEIPT'
          "
        >
          <el-button :loading="busy" @click="act('note')"
            >记录验收异常</el-button
          >
          <el-button type="primary" :loading="busy" @click="act('receive')"
            >确认全部验收入库</el-button
          >
        </template>
      </template>
    </el-dialog>
  </div>
</template>
