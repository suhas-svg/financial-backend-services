import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useState } from "react";
import { Bell, Check, CheckCheck } from "lucide-react";
import { Badge, Button, EmptyState, Field, Panel, Select } from "../components/ui";
import { getNotificationSummary, listNotifications, markAllNotificationsRead, markNotificationRead } from "../lib/queries";
import type { Notification, NotificationSeverity, NotificationStatus, NotificationType } from "../types";

const notificationTypes: Array<NotificationType | ""> = ["", "TRANSACTION_COMPLETED", "TRANSACTION_FAILED", "ACCOUNT_FROZEN", "ACCOUNT_UNFROZEN", "DISPUTE_CREATED", "DISPUTE_STATUS_UPDATED"];
const severities: Array<NotificationSeverity | ""> = ["", "INFO", "SUCCESS", "WARNING", "CRITICAL"];
const statuses: Array<NotificationStatus | ""> = ["", "UNREAD", "READ"];

export function NotificationsPage() {
  const [status, setStatus] = useState<NotificationStatus | "">("");
  const [type, setType] = useState<NotificationType | "">("");
  const [severity, setSeverity] = useState<NotificationSeverity | "">("");
  const queryClient = useQueryClient();
  const notifications = useQuery({ queryKey: ["notifications", status, type, severity], queryFn: () => listNotifications({ status, type, severity }) });
  const summary = useQuery({ queryKey: ["notification-summary"], queryFn: getNotificationSummary });
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["notifications"] });
    queryClient.invalidateQueries({ queryKey: ["notification-summary"] });
  };
  const markRead = useMutation({ mutationFn: markNotificationRead, onSuccess: invalidate });
  const markAllRead = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: invalidate });
  const unread = summary.data?.unread ?? 0;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Notifications</h1>
          <p className="text-sm text-muted">{unread} unread</p>
        </div>
        <Button variant="secondary" onClick={() => markAllRead.mutate()} disabled={!unread || markAllRead.isPending}>
          <CheckCheck className="h-4 w-4" />
          Mark all read
        </Button>
      </div>

      <Panel>
        <div className="grid gap-3 md:grid-cols-3">
          <Field label="Status">
            <Select value={status} onChange={(event) => setStatus(event.target.value as NotificationStatus | "")}>
              {statuses.map((value) => <option key={value || "all"} value={value}>{value || "All statuses"}</option>)}
            </Select>
          </Field>
          <Field label="Type">
            <Select value={type} onChange={(event) => setType(event.target.value as NotificationType | "")}>
              {notificationTypes.map((value) => <option key={value || "all"} value={value}>{value || "All types"}</option>)}
            </Select>
          </Field>
          <Field label="Severity">
            <Select value={severity} onChange={(event) => setSeverity(event.target.value as NotificationSeverity | "")}>
              {severities.map((value) => <option key={value || "all"} value={value}>{value || "All severities"}</option>)}
            </Select>
          </Field>
        </div>
      </Panel>

      <Panel title="Inbox">
        {notifications.error instanceof Error ? <p className="text-sm text-danger">{notifications.error.message}</p> : null}
        {notifications.isLoading ? <p className="text-sm text-muted">Loading notifications...</p> : null}
        {!notifications.isLoading && !notifications.data?.content.length ? <EmptyState title="No notifications found" detail="Adjust filters to review another inbox segment." /> : null}
        {notifications.data?.content.length ? <NotificationList notifications={notifications.data.content} onMarkRead={(id) => markRead.mutate(id)} markingId={markRead.variables} /> : null}
      </Panel>
    </div>
  );
}

function NotificationList({ notifications, onMarkRead, markingId }: { notifications: Notification[]; onMarkRead: (id: number) => void; markingId?: number }) {
  return (
    <div className="grid gap-3">
      {notifications.map((notification) => (
        <article key={notification.notificationId} className="rounded-md border border-line bg-white p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <Bell className="h-4 w-4 text-brand" />
                <h3 className="text-sm font-semibold text-ink">{notification.title}</h3>
                <Badge tone={severityTone(notification.severity)}>{notification.severity}</Badge>
                <Badge tone={notification.status === "UNREAD" ? "info" : "neutral"}>{notification.status}</Badge>
              </div>
              <p className="mt-2 text-sm text-muted">{notification.message}</p>
              <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted">
                <span>{notification.type}</span>
                <SourceLink notification={notification} />
                <span>{new Date(notification.createdAt).toLocaleString()}</span>
              </div>
            </div>
            {notification.status === "UNREAD" ? (
              <Button variant="secondary" onClick={() => onMarkRead(notification.notificationId)} disabled={markingId === notification.notificationId}>
                <Check className="h-4 w-4" />
                Mark read
              </Button>
            ) : null}
          </div>
        </article>
      ))}
    </div>
  );
}

function SourceLink({ notification }: { notification: Notification }) {
  if (notification.sourceType === "TRANSACTION") {
    return <Link className="font-medium text-brand hover:underline" to={`/transactions?transactionId=${notification.sourceId}`}>TRANSACTION {notification.sourceId}</Link>;
  }
  if (notification.sourceType === "DISPUTE") {
    return <Link className="font-medium text-brand hover:underline" to="/disputes">DISPUTE {notification.sourceId}</Link>;
  }
  if (notification.sourceType === "ACCOUNT") {
    return <Link className="font-medium text-brand hover:underline" to="/accounts">ACCOUNT {notification.sourceId}</Link>;
  }
  return <span>{notification.sourceType} {notification.sourceId}</span>;
}

function severityTone(severity: NotificationSeverity) {
  if (severity === "SUCCESS") return "good";
  if (severity === "WARNING") return "warn";
  if (severity === "CRITICAL") return "bad";
  return "info";
}
