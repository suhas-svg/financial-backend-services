import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

for (const route of ["/login", "/register"] as const) {
  test(`${route} has no WCAG A or AA violations`, async ({ page }) => {
    await page.goto(route);
    await expect(page.locator("main")).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();

    expect(
      results.violations.map(({ id, impact, nodes }) => ({
        id,
        impact,
        targets: nodes.map((node) => node.target)
      }))
    ).toEqual([]);
  });
}
