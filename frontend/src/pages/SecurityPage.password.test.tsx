import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "../routing";
import { AuthContext } from "../state/authContext";
import { SecurityPage } from "./SecurityPage";
import * as queries from "../lib/queries";

vi.mock("../lib/queries", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/queries")>();
  return {
    ...actual,
    getMfaStatus: vi.fn(),
    getSpendingLimits: vi.fn().mockResolvedValue([]),
    changePassword: vi.fn()
  };
});

const logout = vi.fn();

function renderPage(enrolled: boolean) {
  vi.mocked(queries.getMfaStatus).mockResolvedValue({ enrolled, recoveryCodesRemaining: enrolled ? 8 : 0 } as Awaited<ReturnType<typeof queries.getMfaStatus>>);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={{ session: null, loginWithToken: vi.fn(), logout, isAdmin: false, restoring: false }}>
        <MemoryRouter initialEntries={["/security"]}>
          <SecurityPage />
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

// The authenticator panel has its own "Current password" field; stay inside the Password form.
function passwordForm() {
  const form = screen.getByText("Changing your password signs you out on every device.").closest("form");
  if (!form) throw new Error("Password form not found");
  return within(form);
}

async function fill(label: string, value: string) {
  await userEvent.type(passwordForm().getByLabelText(label), value);
}

describe("Security page password change", () => {
  beforeEach(() => {
    vi.mocked(queries.changePassword).mockReset();
    logout.mockReset();
  });

  it("changes the password, then signs out because every session has ended", async () => {
    vi.mocked(queries.changePassword).mockResolvedValue(undefined);
    renderPage(false);

    await fill("Current account password", "correct horse battery");
    await fill("New password (at least 12 characters)", "a much longer passphrase");
    await fill("Confirm new password", "a much longer passphrase");
    await userEvent.click(passwordForm().getByRole("button", { name: "Change password" }));

    await waitFor(() => expect(logout).toHaveBeenCalled());
    expect(queries.changePassword).toHaveBeenCalledWith("correct horse battery", "a much longer passphrase", undefined);
    expect(passwordForm().queryByLabelText("Authenticator code")).not.toBeInTheDocument();
  });

  it("does not submit when the confirmation does not match", async () => {
    renderPage(false);

    await fill("Current account password", "correct horse battery");
    await fill("New password (at least 12 characters)", "a much longer passphrase");
    await fill("Confirm new password", "a different passphrase");

    expect(passwordForm().getByRole("alert")).toHaveTextContent("do not match");
    expect(passwordForm().getByRole("button", { name: "Change password" })).toBeDisabled();
    expect(queries.changePassword).not.toHaveBeenCalled();
  });

  it("asks for the authenticator code when MFA is enabled and shows server errors", async () => {
    vi.mocked(queries.changePassword).mockRejectedValue(new Error("Current password is invalid"));
    renderPage(true);

    await waitFor(() => passwordForm().getByLabelText("Authenticator code"));
    await fill("Current account password", "wrong guess here");
    await fill("New password (at least 12 characters)", "a much longer passphrase");
    await fill("Confirm new password", "a much longer passphrase");
    await fill("Authenticator code", "123456");
    await userEvent.click(passwordForm().getByRole("button", { name: "Change password" }));

    expect(await screen.findByText("Current password is invalid")).toBeInTheDocument();
    expect(queries.changePassword).toHaveBeenCalledWith("wrong guess here", "a much longer passphrase", "123456");
    expect(logout).not.toHaveBeenCalled();
  });
});
