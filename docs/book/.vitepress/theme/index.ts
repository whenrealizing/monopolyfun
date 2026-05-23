import DefaultTheme from "vitepress/theme";
import DocDiagram from "./components/DocDiagram.vue";
import "./style.css";

// 中文注释：复用 VitePress 默认主题能力，只在视觉层做品牌化，降低文档框架维护成本。
export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    // 中文注释：全局注册文档图表组件，让 Markdown 只声明图表 id，避免继续维护静态图片标签。
    app.component("DocDiagram", DocDiagram);
  },
};
