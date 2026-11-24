<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from "chart.js";

Chart.register(
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
);
const props = defineProps({
  label: { type: String, required: true },
  dates: { type: Array, required: true },
  series: { type: Array, required: true },
  currency: Boolean,
});
const canvas = ref();
let chart;

function data() {
  return {
    labels: [...props.dates],
    datasets: props.series.map(({ label, color, values }) => ({
      label,
      borderColor: color,
      backgroundColor: color,
      data: [...values],
      borderWidth: 2,
      pointRadius: props.dates.length <= 30 ? 2 : 0,
      pointHoverRadius: 4,
    })),
  };
}

onMounted(() => {
  chart = new Chart(canvas.value, {
    type: "line",
    data: data(),
    options: {
      animation: false,
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      scales: {
        x: { ticks: { maxTicksLimit: 8, maxRotation: 0 } },
        y: {
          beginAtZero: true,
          ticks: props.currency
            ? { callback: (value) => "¥" + value }
            : { precision: 0 },
        },
      },
    },
  });
});
watch(
  () => [props.dates, props.series],
  () => {
    chart.data = data();
    chart.update();
  },
);
onBeforeUnmount(() => chart?.destroy());
</script>

<template>
  <div class="chart-area">
    <canvas ref="canvas" role="img" :aria-label="label" />
  </div>
</template>

<style scoped>
.chart-area {
  height: 244px;
  min-width: 0;
}
</style>
