import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, Navigate } from "react-router-dom";
import { register } from "../lib/queries";
import { registerSchema, type RegisterValues } from "../lib/schemas";
import { useAuth } from "../state/AuthProvider";
import { Button, ErrorNotice, Field, Input } from "../components/ui";

export function RegisterPage() {
  const { session } = useAuth();
  const form = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), defaultValues: { username: "", password: "" } });
  const mutation = useMutation({ mutationFn: register });

  if (session) {
    return <Navigate to="/" replace />;
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 p-4">
      <section className="w-full max-w-md rounded-lg border border-line bg-white p-6 shadow-subtle">
        <h1 className="text-xl font-semibold">Create customer access</h1>
        <p className="mt-1 text-sm text-muted">New registrations receive the backend default customer role.</p>
        <form className="mt-6 grid gap-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
          <ErrorNotice message={mutation.error instanceof Error ? mutation.error.message : undefined} />
          {mutation.isSuccess ? <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">Registered {mutation.data.username}. You can sign in now.</div> : null}
          <Field label="Username" error={form.formState.errors.username?.message}>
            <Input autoComplete="username" {...form.register("username")} />
          </Field>
          <Field label="Password" error={form.formState.errors.password?.message}>
            <Input type="password" autoComplete="new-password" {...form.register("password")} />
          </Field>
          <Button type="submit" disabled={mutation.isPending}>
            Register
          </Button>
        </form>
        <p className="mt-4 text-sm text-muted">
          Already registered?{" "}
          <Link className="font-medium text-brand" to="/login">
            Sign in
          </Link>
        </p>
      </section>
    </main>
  );
}
