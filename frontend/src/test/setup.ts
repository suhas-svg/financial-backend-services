import "@testing-library/jest-dom/vitest";
import { configure } from "@testing-library/react";

// Route pages are lazy-loaded; on a cold transform cache (always the case in CI)
// the default 1s findBy timeout is shorter than the first import of a page.
configure({ asyncUtilTimeout: 5000 });

// jsdom 26 defines window.localStorage but leaves its value undefined, so every
// read of it throws "Cannot read properties of undefined (reading 'getItem')".
// That took out 60 tests across two files while the app code under test was fine.
// Provide a minimal in-memory Storage so component tests exercise real behaviour.
if (typeof window !== "undefined" && !window.localStorage) {
  const store = new Map<string, string>();
  const shim: Storage = {
    get length() {
      return store.size;
    },
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    getItem: (key: string) => (store.has(key) ? store.get(key)! : null),
    setItem: (key: string, value: string) => {
      store.set(String(key), String(value));
    },
    removeItem: (key: string) => {
      store.delete(String(key));
    },
    clear: () => store.clear(),
  };
  Object.defineProperty(window, "localStorage", { value: shim, configurable: true, writable: true });
}
