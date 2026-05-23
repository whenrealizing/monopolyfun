import { defineConfig } from "vitepress";

const zhSidebar = [
  {
    text: "MonopolyFun",
    items: [
      { text: "01 MonopolyFun 思想", link: "/open-company-manifesto" },
    ],
  },
];

const enSidebar = [
  {
    text: "MonopolyFun",
    items: [
      { text: "01 What is MonopolyFun", link: "/en/open-company-manifesto" },
    ],
  },
];

// 中文注释：VitePress locale 配置负责顶部语言切换和侧边栏隔离，保持中英文目录各自渲染。
export default defineConfig({
  title: "MonopolyFun",
  description: "MonopolyFun open company handbook",
  cleanUrls: true,
  appearance: false,
  lastUpdated: false,
  srcExclude: ["SUMMARY.md", "README.md", "en/README.md"],
  head: [
    ["meta", { name: "theme-color", content: "#0f6a61" }],
    ["meta", { property: "og:type", content: "website" }],
    ["meta", { property: "og:site_name", content: "MonopolyFun Handbook" }],
    ["link", { rel: "preconnect", href: "https://fonts.googleapis.com" }],
    [
      "link",
      { rel: "preconnect", href: "https://fonts.gstatic.com", crossorigin: "" },
    ],
    [
      "link",
      {
        rel: "stylesheet",
        href: "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;600;700;800&family=Inter:ital,wght@0,400..600;1,400..600&family=Fira+Code:wght@400;500&display=swap",
      },
    ],
  ],
  themeConfig: {
    logo: "/brand/openmonopoly-mark.png",
    search: {
      provider: "local",
    },
    socialLinks: [
      { icon: "github", link: "https://github.com/whenrealizing/monopolyfun" },
    ],
    footer: {
      message: "Released under the MIT License.",
      copyright: "Copyright © 2026 MonopolyFun",
    },
  },
  locales: {
    root: {
      label: "中文",
      lang: "zh-CN",
      title: "MonopolyFun 手册",
      description: "MonopolyFun 开放公司手册",
      themeConfig: {
        nav: [
          { text: "MonopolyFun 思想", link: "/open-company-manifesto" },
        ],
        sidebar: zhSidebar,
        outline: {
          label: "本页目录",
        },
        docFooter: {
          prev: "上一页",
          next: "下一页",
        },
      },
    },
    en: {
      label: "English",
      lang: "en-US",
      title: "MonopolyFun Handbook",
      description: "MonopolyFun open company handbook",
      themeConfig: {
        nav: [
          { text: "What is MonopolyFun", link: "/en/open-company-manifesto" },
        ],
        sidebar: enSidebar,
        outline: {
          label: "On this page",
        },
        docFooter: {
          prev: "Previous",
          next: "Next",
        },
      },
    },
  },
});
