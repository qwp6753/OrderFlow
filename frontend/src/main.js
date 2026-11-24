import { createApp } from "vue";
import { createPinia } from "pinia";
import {
  ElAlert, ElButton, ElButtonGroup, ElDatePicker, ElDescriptions,
  ElDescriptionsItem, ElDialog, ElDrawer, ElEmpty, ElForm, ElFormItem,
  ElInput, ElInputNumber, ElLoadingDirective, ElOption, ElPagination,
  ElProgress, ElRadio, ElRadioButton, ElRadioGroup, ElSelect, ElSkeleton,
  ElTabPane, ElTable, ElTableColumn, ElTabs, ElTag,
} from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import router from "./router";
import "./style.css";

const app = createApp(App);
for (const component of [
  ElAlert, ElButton, ElButtonGroup, ElDatePicker, ElDescriptions,
  ElDescriptionsItem, ElDialog, ElDrawer, ElEmpty, ElForm, ElFormItem,
  ElInput, ElInputNumber, ElOption, ElPagination, ElProgress, ElRadio,
  ElRadioButton, ElRadioGroup, ElSelect, ElSkeleton, ElTabPane, ElTable,
  ElTableColumn, ElTabs, ElTag,
]) {
  app.component(component.name, component);
}
app.directive("loading", ElLoadingDirective);
app.use(createPinia()).use(router).mount("#app");
