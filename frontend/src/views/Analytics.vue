<script setup>
import { computed, onMounted, ref } from "vue";
import TrendChart from "../components/TrendChart.vue";
import api from "../api";

const dashboard = ref(null),
  dates = ref(null),
  preset = ref(7);
const ranking = ref("sku");
const loading = ref(false),
  failed = ref(false);
const lowStock = ref([]),
  lowTotal = ref(0),
  lowPage = ref(1);
const lowLoading = ref(false),
  lowFailed = ref(false);
const money = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
});
const orderLabels = {
  PENDING_PAYMENT: "待付款",
  PENDING_SHIPMENT: "待发货",
  SHIPPED: "已发货",
  COMPLETED: "已完成",
  CANCELED: "已取消",
};
const returnLabels = {
  PENDING_REVIEW: "待审核",
  REJECTED: "已拒绝",
  PENDING_RECEIPT: "待验收",
  COMPLETED: "退货完成",
};
const statusCount = (summary, status) => summary.byStatus[status] ?? 0;
const labels = computed(
  () => dashboard.value?.trend.map((row) => row.date) ?? [],
);
const orderSeries = computed(() => [
  {
    label: "新建订单",
    color: "#185be8",
    values: dashboard.value?.trend.map((row) => row.createdOrders) ?? [],
  },
  {
    label: "完成订单",
    color: "#739cf3",
    values: dashboard.value?.trend.map((row) => row.completedOrders) ?? [],
  },
]);
const amountSeries = computed(() => [
  {
    label: "模拟成交金额",
    color: "#185be8",
    values: dashboard.value?.trend.map((row) => Number(row.amount)) ?? [],
  },
]);

async function load() {
  loading.value = true;
  failed.value = false;
  try {
    const params = preset.value
      ? { days: preset.value }
      : { from: dates.value[0], to: dates.value[1] };
    const result = await api.get("/analytics/dashboard", { params });
    dashboard.value = result;
    dates.value = [result.from, result.to];
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}

async function loadLowStock() {
  lowLoading.value = true;
  lowFailed.value = false;
  try {
    const result = await api.get("/analytics/low-stock", {
      params: { page: lowPage.value, size: 10 },
    });
    lowStock.value = result.records;
    lowTotal.value = result.total;
  } catch {
    lowFailed.value = true;
  } finally {
    lowLoading.value = false;
  }
}

function quick(days) {
  preset.value = days;
  load();
}

function custom(range) {
  if (!range) {
    quick(7);
    return;
  }
  preset.value = 0;
  load();
}

onMounted(() => {
  load();
  loadLowStock();
});
</script>

<template>
  <div class="analytics">
    <div class="page-heading">
      <div>
        <span class="eyebrow">OPERATIONS OVERVIEW</span>
        <h1>运营看板</h1>
        <p class="muted">
          跟踪订单履约、SKU 销量和售后变化。业务日期按上海时间计算。
        </p>
      </div>
    </div>
    <div class="range-controls">
      <el-button-group>
        <el-button
          :disabled="loading"
          :type="preset === 7 ? 'primary' : ''"
          @click="quick(7)"
          >最近 7 天</el-button
        >
        <el-button
          :disabled="loading"
          :type="preset === 30 ? 'primary' : ''"
          @click="quick(30)"
          >最近 30 天</el-button
        >
      </el-button-group>
      <el-date-picker
        v-model="dates"
        :disabled="loading"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        @change="custom"
      />
    </div>
    <el-alert
      class="order-notice"
      type="info"
      :closable="false"
      title="金额为演示统计。模拟净成交额按期间完成订单减去期间完成退货计算，不代表实际收付款。"
    />

    <el-alert
      v-if="failed"
      type="error"
      :closable="false"
      title="运营数据加载失败"
    >
      <el-button text @click="load">重新加载</el-button>
    </el-alert>
    <el-skeleton v-else-if="!dashboard && loading" :rows="8" animated />
    <div v-else-if="dashboard" v-loading="loading">
      <section class="metrics" aria-label="运营概览">
        <div class="metric">
          <span>期间新建订单</span
          ><strong data-testid="orders-total">{{
            dashboard.orders.total
          }}</strong>
        </div>
        <div
          class="metric"
          v-for="(label, status) in orderLabels"
          :key="status"
        >
          <span>{{ label }}订单</span
          ><strong>{{ statusCount(dashboard.orders, status) }}</strong>
        </div>
        <div class="metric">
          <span>模拟成交总金额</span
          ><strong data-testid="gross">{{
            money.format(dashboard.grossAmount)
          }}</strong>
        </div>
        <div class="metric">
          <span>已完成退货预计退款</span
          ><strong data-testid="returns-amount">{{
            money.format(dashboard.completedReturns.amount)
          }}</strong>
        </div>
        <div class="metric">
          <span>模拟净成交额</span
          ><strong data-testid="net">{{
            money.format(dashboard.netAmount)
          }}</strong>
        </div>
        <div class="metric">
          <span>期间申请中待处理售后</span
          ><strong>{{ dashboard.pendingReturns }}</strong>
        </div>
        <div class="metric">
          <span>当前 SKU 数</span
          ><strong>{{ dashboard.inventory.skuCount }}</strong>
        </div>
        <div class="metric">
          <span>当前低库存 SKU</span
          ><strong>{{ dashboard.inventory.lowCount }}</strong>
        </div>
      </section>
      <p class="muted metric-note">
        订单状态卡片按创建日期筛选，显示这些订单的当前状态。金额和完成趋势按对应的完成日期统计。
      </p>

      <div class="analytics-grid">
        <section class="catalog-panel chart-panel">
          <h3>订单数量趋势</h3>
          <TrendChart
            label="每日新建和完成订单数量"
            :dates="labels"
            :series="orderSeries"
          />
        </section>
        <section class="catalog-panel chart-panel">
          <h3>模拟成交金额趋势</h3>
          <TrendChart
            label="每日模拟成交金额"
            :dates="labels"
            :series="amountSeries"
            currency
          />
        </section>
      </div>

      <section class="catalog-panel analytics-section">
        <div class="panel-heading">
          <div>
            <h3>销量前 10</h3>
            <span class="muted">按完成销量降序 · 期间退货独立计入</span>
          </div>
          <el-radio-group v-model="ranking"
            ><el-radio-button value="sku">按 SKU</el-radio-button
            ><el-radio-button value="product"
              >按商品</el-radio-button
            ></el-radio-group
          >
        </div>
        <el-table
          :data="ranking === 'sku' ? dashboard.topSkus : dashboard.topProducts"
          empty-text="当前期间没有销量或已完成退货"
        >
          <el-table-column
            prop="productName"
            label="商品快照"
            min-width="180"
          />
          <el-table-column
            v-if="ranking === 'sku'"
            prop="skuCode"
            label="SKU 快照"
            min-width="140"
          />
          <el-table-column prop="sold" label="完成销量" width="100" />
          <el-table-column prop="returned" label="退货件数" width="100" />
          <el-table-column prop="netSold" label="净销量" width="95" />
          <el-table-column label="模拟销售金额" min-width="145"
            ><template #default="{ row }">{{
              money.format(row.amount)
            }}</template></el-table-column
          >
        </el-table>
      </section>

      <div class="analytics-grid lower-grid">
        <section class="catalog-panel chart-panel">
          <h3>售后状态分布</h3>
          <p class="muted">
            期间新建申请 {{ dashboard.afterSales.total }} 单，按当前状态分布。
          </p>
          <div
            class="status-row"
            v-for="(label, status) in returnLabels"
            :key="status"
          >
            <span>{{ label }}</span>
            <el-progress
              :show-text="false"
              :percentage="
                dashboard.afterSales.total
                  ? Math.round(
                      (statusCount(dashboard.afterSales, status) * 100) /
                        dashboard.afterSales.total,
                    )
                  : 0
              "
            />
            <strong>{{ statusCount(dashboard.afterSales, status) }} 单</strong>
          </div>
          <p class="muted metric-note">
            按验收日期统计已完成 {{ dashboard.completedReturns.cases }} 单，退回
            {{ dashboard.completedReturns.units }} 件。
          </p>
        </section>
        <section class="catalog-panel chart-panel">
          <div class="stock-heading">
            <h3>库存汇总</h3>
            <router-link to="/inventory">查询 SKU 库存</router-link>
          </div>
          <p class="muted">当前库存独立于日期范围，可用 = 实物 − 预占。</p>
          <div class="metrics stock-metrics">
            <div class="metric">
              <span>实物</span
              ><strong>{{ dashboard.inventory.physical }}</strong>
            </div>
            <div class="metric">
              <span>预占</span
              ><strong>{{ dashboard.inventory.reserved }}</strong>
            </div>
            <div class="metric">
              <span>可用</span
              ><strong>{{ dashboard.inventory.available }}</strong>
            </div>
          </div>
        </section>
      </div>

      <section class="catalog-panel analytics-section">
        <div class="panel-heading">
          <h3>低库存预警</h3>
          <span class="muted"
            >可用数量低于 {{ dashboard.lowStockThreshold }} 件</span
          >
        </div>
        <el-alert
          v-if="lowFailed"
          type="error"
          :closable="false"
          title="库存预警加载失败"
          ><el-button text @click="loadLowStock">重新加载</el-button></el-alert
        >
        <template v-else>
          <el-table
            v-loading="lowLoading"
            :data="lowStock"
            empty-text="暂无低库存 SKU"
          >
            <el-table-column prop="productName" label="商品" min-width="180" />
            <el-table-column prop="skuCode" label="SKU" min-width="150" />
            <el-table-column prop="physical" label="实物" width="95" />
            <el-table-column prop="reserved" label="预占" width="95" />
            <el-table-column label="可用" width="95"
              ><template #default="{ row }"
                ><el-tag type="warning">{{ row.available }}</el-tag></template
              ></el-table-column
            >
          </el-table>
          <div class="pagination">
            <el-pagination
              v-model:current-page="lowPage"
              :page-size="10"
              :total="lowTotal"
              layout="total, prev, pager, next"
              @current-change="loadLowStock"
            />
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.range-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
}
.metric {
  min-width: 0;
  padding: 18px;
  border: 1px solid #dce5f1;
  border-radius: 8px;
  background: #fff;
}
.metric span {
  display: block;
  color: #596a83;
  font-size: 13px;
}
.metric strong {
  display: block;
  margin-top: 8px;
  font-size: 23px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.metrics:not(.stock-metrics) .metric:first-child {
  border-color: #bbcff9;
  background: #f0f5ff;
}
.metrics:not(.stock-metrics) .metric:first-child strong {
  color: #104bbc;
}
.metric-note {
  margin: 14px 0 20px;
}
.analytics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.chart-panel {
  padding: 20px;
  min-width: 0;
}
.chart-panel h3 {
  margin: 0 0 12px;
}
.analytics-section,
.lower-grid {
  margin-top: 18px;
}
.status-row {
  display: grid;
  grid-template-columns: 75px 1fr 55px;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
  font-size: 13px;
}
.status-row strong {
  text-align: right;
}
.stock-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.stock-heading a {
  color: #185be8;
  font-size: 13px;
}
.stock-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
@media (max-width: 980px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
