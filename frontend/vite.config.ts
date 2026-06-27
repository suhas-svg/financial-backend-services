import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const accountApiTarget = process.env.VITE_ACCOUNT_API_TARGET ?? "http://localhost:8080";
const transactionApiTarget = process.env.VITE_TRANSACTION_API_TARGET ?? "http://localhost:8081";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/account-api": {
        target: accountApiTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/account-api/, "")
      },
      "/transaction-api": {
        target: transactionApiTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/transaction-api/, "")
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    exclude: ["node_modules/**", "dist/**", "tests/e2e/**"],
    globals: true
  }
});
