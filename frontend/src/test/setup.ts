import "@testing-library/jest-dom/vitest";
import { configure } from "@testing-library/react";

// Route pages are lazy-loaded; on a cold transform cache (always the case in CI)
// the default 1s findBy timeout is shorter than the first import of a page.
configure({ asyncUtilTimeout: 5000 });
