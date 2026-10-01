import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis } from "recharts";

export type ActivityMixDatum = { name: string; value: number };

// Loaded with React.lazy so the charts library is only fetched on pages that draw a chart.
export default function ActivityMixChart({ data }: { data: ActivityMixDatum[] }) {
  return (
    <ResponsiveContainer>
      <BarChart data={data} margin={{ left: -24, right: 8 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="#d8dee8" />
        <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fontSize: 11 }} />
        <Tooltip cursor={{ fill: "rgba(16,185,129,.08)" }} />
        <Bar dataKey="value" fill="#10b981" radius={[8, 8, 2, 2]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
