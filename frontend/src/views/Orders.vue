<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
import { orderStatuses } from "../order-status";
import api from "../api";
const auth = useAuth(),
  router = useRouter(),
  loading = ref(false),
  rows = ref([]),
  total = ref(0),
  dates = ref([]);
const filters = reactive({ page: 1, size: 10, orderNo: "", status: "" });
async function load() {
  loading.value = true;
  try {
    const data = await api.get("/orders", {
      params: {
        ...filters,
        orderNo: filters.orderNo || undefined,
        status: filters.status || undefined,
        from: dates.value?.[0],
        to: dates.value?.[1],
      },
    });
    rows.value = data.records;
    total.value = data.total;
  } catch {
    /* 统一拦截器提示 */
  } finally {
    loading.value = false;
  }
}
function search() {
  filters.page = 1;
  load();
}
function reset() {
  filters.orderNo = "";
  filters.status = "";
  dates.value = [];
  search();
}
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <span class="eyebrow">ORDER OPERATIONS</span>
        <h1>订单管理</h1>
        <p class="muted">追踪订单、发货与完成状态。</p>
      </div>
      <div class="action-buttons">
        <el-button
          v-if="auth.canWarehouse"
          size="large"
          @click="
            filters.status = 'PENDING_SHIPMENT';
            search();
          "
          >待发货订单</el-button
        ><el-button
          v-if="auth.canEdit"
          type="primary"
          size="large"
          @click="router.push('/orders/new')"
          >＋ 创建订单</el-button
        >
      </div>
    </div>
    <el-alert
      class="order-notice"
      title="内部业务演示：模拟付款不涉及真实支付；待发货订单取消仅释放库存，不执行资金退款。"
      type="info"
      :closable="false"
    />
    <section class="catalog-panel">
      <div class="panel-heading">
        <h3>
          订单目录 <span class="count">{{ total }}</span>
        </h3>
        <span class="muted">{{
          auth.canEdit ? "运营管理" : "仓库 · 发货作业"
        }}</span>
      </div>
      <el-form class="filters" inline @submit.prevent="search"
        ><el-form-item label="订单编号"
          ><el-input
            v-model="filters.orderNo"
            clearable
            maxlength="40"
            placeholder="完整订单编号"
            @keyup.enter="search" /></el-form-item
        ><el-form-item label="订单状态"
          ><el-select
            v-model="filters.status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
            ><el-option
              v-for="(s, key) in orderStatuses"
              :key="key"
              :label="s.label"
              :value="key" /></el-select></el-form-item
        ><el-form-item label="创建时间"
          ><el-date-picker
            v-model="dates"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 350px" /></el-form-item
        ><el-form-item
          ><el-button type="primary" @click="search">查询</el-button
          ><el-button @click="reset">重置</el-button></el-form-item
        ></el-form
      ><el-table
        :data="rows"
        v-loading="loading"
        row-key="id"
        empty-text="暂无符合条件的订单"
        ><el-table-column
          prop="orderNo"
          label="订单编号"
          min-width="300"
        /><el-table-column label="订单金额" width="150"
          ><template #default="{ row }"
            >¥{{ Number(row.totalAmount).toFixed(2) }}</template
          ></el-table-column
        ><el-table-column label="状态" width="140"
          ><template #default="{ row }"
            ><el-tag :type="orderStatuses[row.status].type">{{
              orderStatuses[row.status].label
            }}</el-tag></template
          ></el-table-column
        ><el-table-column
          prop="recipientName"
          label="收件人"
          width="130"
        /><el-table-column label="创建时间" min-width="190"
          ><template #default="{ row }">{{
            row.createdAt.replace("T", " ")
          }}</template></el-table-column
        ><el-table-column label="操作" width="100" fixed="right"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              @click="router.push('/orders/' + row.id)"
              >查看详情</el-button
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
  </div>
</template>
