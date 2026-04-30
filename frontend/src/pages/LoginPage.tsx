import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { Landmark } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { login } from "../lib/queries";
import { loginSchema, type LoginValues } from "../lib/schemas";
import { useAuth } from "../state/AuthProvider";
import { Button, ErrorNotice, Field, Input } from "../components/ui";

export function LoginPage() {
  const navigate = useNavigate();
  const { session, loginWithToken } = useAuth();
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { username: "", password: "" } });
  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      const token = data.token ?? data.accessToken;
      if (!token) {
        throw new Error("Login response did not include a token");
      }
      loginWithToken(token);
      navigate("/");
    }
  });

  if (session) {
    return <Navigate to="/" replace />;
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 p-4">
      <section className="w-full max-w-md rounded-lg border border-line bg-white p-6 shadow-subtle">
        <div className="mb-6 flex items-center gap-3">
          <Landmark className="h-6 w-6 text-brand" />
          <div>
            <h1 className="text-xl font-semibold">Financial Console</h1>
            <p className="text-sm text-muted">Sign in to your banking workspace.</p>
          </div>
        </div>
        <form className="grid gap-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
          <ErrorNotice message={mutation.error instanceof Error ? mutation.error.message : undefined} />
          <Field label="Username" error={form.formState.errors.username?.message}>
            <Input autoComplete="username" {...form.register("username")} />
          </Field>
          <Field label="Password" error={form.formState.errors.password?.message}>
            <Input type="password" autoComplete="current-password" {...form.register("password")} />
          </Field>
          <Button type="submit" disabled={mutation.isPending}>
            Sign in
          </Button>
        </form>
        <p className="mt-4 text-sm text-muted">
          Need an account?{" "}
          <Link className="font-medium text-brand" to="/register">
            Register
          </Link>
        </p>
      </section>
    </main>
  );
}
