import { defineConfig } from "orval";

const apiBaseUrl = process.env.OPENAPI_SPEC_URL ?? "http://localhost:8080/v3/api-docs";

export default defineConfig({
  monopolyfun: {
    input: {
      target: apiBaseUrl,
    },
    output: {
      target: "lib/generated/api/monopolyfun.ts",
      schemas: "lib/generated/api/model",
      client: "fetch",
      mode: "split",
      clean: true,
      override: {
        fetch: {
          includeHttpResponseReturnType: false,
        },
        mutator: {
          path: "lib/api-runtime.ts",
          name: "monopolyfunFetch",
        },
      },
    },
  },
});
