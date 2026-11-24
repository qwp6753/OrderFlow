<script setup>
import { ref, reactive, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useAuth } from "../stores/auth";
import { orderStatuses } from "../order-status";
import api from "../api";
import { movementTypes } from "../stock-types";
const route = useRoute(),
  router = useRouter(),
  auth = useAuth(),
  loading = ref(false),
  busy = ref(false),
  detail = ref(null);
async function load() {
  loading.value = true;
  try {
    detail.value = await api.get("/orders/" + route.params.id);
  } catch {
    detail.value = null;
  } finally {
    loading.value = false;
  }
}
const shipDialog = ref(false),
  shipFormRef = ref(),
  shipForm = reactive({ carrier: "", trackingNo: "" });
const shipRules = {
  carrier: [
    { required: true, whitespace: true, message: "请输入物流公司" },
    { max: 80, message: "最多80字符" },
  ],
  trackingNo: [
    { required: true, whitespace: true, message: "请输入物流单号" },
    { max: 80, message: "最多80字符" },
  ],
};
const actions = {
  cancel: {
    title: "取消订单",
    message: "取消将释放全部预占库存；本演示不执行真实资金退款。",
    confirm: "确认取消",
    success: "订单已取消，预占库存已释放",
  },
  "simulate-payment": {
    title: "模拟确认付款",
    message: "仅模拟内部确认付款，不涉及真实支付。",
    confirm: "确认模拟付款",
    success: "已模拟确认付款",
  },
  complete: {
    title: "模拟确认收货",
    message: "仅后台模拟确认收货，不接物流平台；确认后完成订单，不再扣减库存。",
    confirm: "确认模拟收货",
    success: "订单已完成，库存未再次扣减",
  },
};
async function act(action) {
  const options = actions[action];
  try {
    await ElMessageBox.confirm(options.message, options.title, {
      confirmButtonText: options.confirm,
      cancelButtonText: "返回",
      type: "warning",
    });
  } catch {
    return;
  }
  busy.value = true;
  try {
    await api.post("/orders/" + route.params.id + "/" + action);
    ElMessage.success(options.success);
    await load();
  } catch {
    await load();
  } finally {
    busy.value = false;
  }
}
function openShip() {
  shipForm.carrier = "";
  shipForm.trackingNo = "";
  shipDialog.value = true;
}
async function ship() {
  if (!(await shipFormRef.value.validate().catch(() => false))) return;
  busy.value = true;
  try {
    await api.post("/orders/" + route.params.id + "/ship", { ...shipForm });
    shipDialog.value = false;
    ElMessage.success("发货成功，实物与预占已同步扣减");
    await load();
  } catch {
    await load();
  } finally {
    busy.value = false;
  }
}
onMounted(load);
watch(() => route.params.id, load);
</script>
<template>
  <div v-loading="loading">
    <div class="page-heading">
      <div>
        <span class="eyebrow">ORDER DETAILS</span>
        <h1>订单详情</h1>
        <p class="muted">查看成交快照与库存变动。</p>
      </div>
      <el-button @click="router.push('/orders')">返回订单列表</el-button>
    </div>
    <el-alert
      class="order-notice"
      type="info"
      title="模拟付款不涉及真实支付；待发货订单取消只释放库存，不执行资金退款。"
      :closable="false"
    /><template v-if="detail"
      ><section class="catalog-panel form-panel">
        <div class="detail-actions">
          <div>
            <h3 class="order-number">{{ detail.order.orderNo }}</h3>
            <el-tag :type="orderStatuses[detail.order.status].type">{{
              orderStatuses[detail.order.status].label
            }}</el-tag>
          </div>
          <div class="action-buttons">
            <el-button
              @click="
                router.push({
                  path: '/returns',
                  query: { orderId: detail.order.id },
                })
              "
              >售后记录</el-button
            >
            <el-button
              v-if="auth.canEdit && detail.order.status === 'PENDING_PAYMENT'"
              type="primary"
              :loading="busy"
              @click="act('simulate-payment')"
              >模拟确认付款</el-button
            ><el-button
              v-if="
                auth.canEdit &&
                ['PENDING_PAYMENT', 'PENDING_SHIPMENT'].includes(
                  detail.order.status,
                )
              "
              type="danger"
              plain
              :loading="busy"
              @click="act('cancel')"
              >取消订单</el-button
            >
            <el-button
              v-if="
                auth.canWarehouse && detail.order.status === 'PENDING_SHIPMENT'
              "
              type="primary"
              :loading="busy"
              @click="openShip"
              >订单发货</el-button
            >
            <el-button
              v-if="auth.canEdit && detail.order.status === 'SHIPPED'"
              type="primary"
              :loading="busy"
              @click="act('complete')"
              >模拟确认收货</el-button
            >
          </div>
        </div>
        <el-descriptions :column="2" border
          ><el-descriptions-item label="订单金额"
            >¥{{
              Number(detail.order.totalAmount).toFixed(2)
            }}</el-descriptions-item
          ><el-descriptions-item label="创建时间">{{
            detail.order.createdAt.replace("T", " ")
          }}</el-descriptions-item
          ><el-descriptions-item label="收件人">{{
            detail.order.recipientName
          }}</el-descriptions-item
          ><el-descriptions-item label="联系电话">{{
            detail.order.recipientPhone
          }}</el-descriptions-item
          ><el-descriptions-item label="收件地址" :span="2">{{
            detail.order.recipientAddress
          }}</el-descriptions-item
          ><el-descriptions-item label="备注" :span="2">{{
            detail.order.remark || "无"
          }}</el-descriptions-item></el-descriptions
        >
      </section>
      <section v-if="detail.shipment" class="catalog-panel form-panel">
        <h3>物流信息</h3>
        <el-descriptions :column="2" border
          ><el-descriptions-item label="物流公司">{{
            detail.shipment.carrier
          }}</el-descriptions-item
          ><el-descriptions-item label="物流单号">{{
            detail.shipment.trackingNo
          }}</el-descriptions-item
          ><el-descriptions-item label="发货时间">{{
            detail.shipment.shippedAt?.replace("T", " ") || "未发货"
          }}</el-descriptions-item
          ><el-descriptions-item
            v-if="detail.order.completedAt"
            label="完成时间"
            >{{
              detail.order.completedAt.replace("T", " ")
            }}</el-descriptions-item
          ></el-descriptions
        >
      </section>
      <section class="catalog-panel">
        <div class="panel-heading">
          <h3>订单明细 · 成交快照</h3>
          <span class="muted">商品后续改价不影响本订单</span>
        </div>
        <el-table :data="detail.items"
          ><el-table-column
            prop="productName"
            label="商品名称"
            min-width="160" /><el-table-column
            prop="skuCode"
            label="SKU 编码"
            min-width="140" /><el-table-column
            prop="skuName"
            label="规格"
            min-width="140" /><el-table-column label="成交单价" width="140"
            ><template #default="{ row }"
              >¥{{ Number(row.unitPrice).toFixed(2) }}</template
            ></el-table-column
          ><el-table-column prop="quantity" label="数量" width="100"
        /></el-table>
      </section>
      <section class="catalog-panel movement-panel">
        <div class="panel-heading">
          <h3>订单库存流水</h3>
          <span class="muted">发货同时扣减实物与预占</span>
        </div>
        <el-table :data="detail.movements" empty-text="没有订单关联流水"
          ><el-table-column
            prop="skuId"
            label="SKU ID"
            width="100"
          /><el-table-column label="类型" width="120"
            ><template #default="{ row }">{{
              movementTypes[row.movementType]
            }}</template></el-table-column
          ><el-table-column
            prop="quantityDelta"
            label="实物变动"
            width="120"
          /><el-table-column label="预占变动" width="140"
            ><template #default="{ row }"
              >{{ row.reservedDelta > 0 ? "+" : ""
              }}{{ row.reservedDelta }}</template
            ></el-table-column
          ><el-table-column
            prop="reservedAfter"
            label="变更后预占"
            width="140"
          /><el-table-column
            prop="quantityAfter"
            label="变更后实物"
            width="130"
          /><el-table-column label="时间" min-width="190"
            ><template #default="{ row }">{{
              row.createdAt.replace("T", " ")
            }}</template></el-table-column
          ></el-table
        >
      </section></template
    ><el-empty
      v-else-if="!loading"
      description="未能加载订单，请返回列表后重试"
    />
    <el-dialog
      v-model="shipDialog"
      title="填写发货信息"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
      ><el-form
        ref="shipFormRef"
        :model="shipForm"
        :rules="shipRules"
        label-position="top"
        ><el-form-item label="物流公司" prop="carrier"
          ><el-input
            v-model="shipForm.carrier"
            maxlength="80"
            placeholder="填写实际承运公司" /></el-form-item
        ><el-form-item label="物流单号" prop="trackingNo"
          ><el-input
            v-model="shipForm.trackingNo"
            maxlength="80" /></el-form-item
      ></el-form>
      <p class="muted">提交后同步扣减实物与预占库存，不重复扣减可用库存。</p>
      <template #footer
        ><el-button @click="shipDialog = false">返回</el-button
        ><el-button type="primary" :loading="busy" @click="ship"
          >确认发货</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
