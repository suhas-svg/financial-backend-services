import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const accountApiTarget = process.env.VITE_ACCOUNT_API_TARGET ?? "http://localhost:8080";
const transactionApiTarget = process.env.VITE_TRANSACTION_API_TARGET ?? "http://localhost:8081";

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          if (id.includes("recharts") || id.includes("d3-") || id.includes("victory-vendor")) return "vendor-charts";
          if (id.includes("@tanstack")) return "vendor-query";
          if (id.includes("react-hook-form") || id.includes("@hookform") || id.includes("zod")) return "vendor-forms";
          if (id.includes("lucide-react")) return "vendor-icons";
          if (id.includes("react") || id.includes("scheduler")) return "vendor-react";
          return undefined;
        }
      }
    }
  },
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
    exclude: ["node_modules/**", "dist/**", "tests/e2e/**", "tests/sandbox-e2e/**", "tests/accessibility/**"],
    globals: true,
    fileParallelism: false
  }
});
