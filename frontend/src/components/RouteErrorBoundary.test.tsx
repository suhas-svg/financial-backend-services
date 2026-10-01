import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Link, MemoryRouter } from "../routing";
import { RouteErrorBoundary } from "./RouteErrorBoundary";

let shouldThrow = true;

function FlakyPage() {
  if (shouldThrow) throw new Error("render exploded");
  return <p>Page content</p>;
}

describe("RouteErrorBoundary", () => {
  beforeEach(() => {
    shouldThrow = true;
    vi.spyOn(console, "error").mockImplementation(() => undefined);
  });
  afterEach(() => vi.restoreAllMocks());

  it("contains a crash to the failing page and keeps the rest of the app usable", () => {
    render(
      <MemoryRouter initialEntries={["/accounts"]}>
        <nav><Link to="/">Home</Link></nav>
        <RouteErrorBoundary><FlakyPage /></RouteErrorBoundary>
      </MemoryRouter>
    );
    expect(screen.getByRole("alert")).toHaveTextContent("This page could not be displayed");
    expect(screen.getByRole("link", { name: "Home" })).toBeInTheDocument();
  });

  it("re-renders the page when the user tries again", async () => {
    render(
      <MemoryRouter>
        <RouteErrorBoundary><FlakyPage /></RouteErrorBoundary>
      </MemoryRouter>
    );
    shouldThrow = false;
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(screen.getByText("Page content")).toBeInTheDocument();
  });

  it("clears the error after navigating to another route", async () => {
    render(
      <MemoryRouter initialEntries={["/accounts"]}>
        <Link to="/statements">Statements</Link>
        <RouteErrorBoundary><FlakyPage /></RouteErrorBoundary>
      </MemoryRouter>
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    shouldThrow = false;
    await userEvent.click(screen.getByRole("link", { name: "Statements" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Page content")).toBeInTheDocument();
  });

  it("offers a reload when a lazily loaded page chunk is missing after a release", () => {
    function MissingChunk(): never {
      throw new TypeError("Failed to fetch dynamically imported module: /assets/AccountsPage-old.js");
    }
    render(
      <MemoryRouter>
        <RouteErrorBoundary><MissingChunk /></RouteErrorBoundary>
      </MemoryRouter>
    );
    expect(screen.getByRole("button", { name: "Reload" })).toBeInTheDocument();
  });
});
