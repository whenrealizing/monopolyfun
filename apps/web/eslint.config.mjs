import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = [
  { ignores: ["lib/generated/**", "allure-report/**", "allure-results/**", "playwright-report/**", "test-results/**"] },
  ...nextVitals,
  ...nextTs,
];

export default eslintConfig;
