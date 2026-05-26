import React from "react";
import ReactDOM from "react-dom/client";
import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis,
  CartesianGrid, Tooltip, ResponsiveContainer, Cell, Legend
} from "recharts";

const PERFECT = "#c8853b";
const CORRECT = "#4f8a67";
const DEFICIENT = "#d7b957";
const WRONG = "#b55a4c";
const UNKNOWN = "#7b8ca8";
const MODULE_BAR = "#b7a27d";

const distributionData = [
  { name: "完美", value: 2, color: PERFECT },
  { name: "正确", value: 5, color: CORRECT },
  { name: "缺漏", value: 1, color: DEFICIENT },
  { name: "错误", value: 1, color: WRONG },
  { name: "不会", value: 1, color: UNKNOWN },
];

const moduleData = [
  { name: "SpringBoot", score: 85, total: 4 },
  { name: "JVM", score: 72, total: 3 },
  { name: "MySQL", score: 60, total: 2 },
  { name: "Redis", score: 45, total: 1 },
];

const trendData = [
  { date: "05/20", score: 65, accuracy: 52 },
  { date: "05/21", score: 72, accuracy: 60 },
  { date: "05/22", score: 70, accuracy: 55 },
  { date: "05/24", score: 78, accuracy: 68 },
  { date: "05/25", score: 80, accuracy: 72 },
  { date: "05/27", score: 82, accuracy: 78 },
];

const tickStyle = {
  fill: "rgba(41,37,32,0.4)",
  fontSize: 12,
  fontFamily: "-apple-system, sans-serif",
};

const tooltipStyle = {
  contentStyle: {
    borderRadius: 12,
    border: "1px solid rgba(67,59,48,0.1)",
    fontFamily: "-apple-system, sans-serif",
    fontSize: 13,
    color: "#292520",
  },
};

function Card({ head, badge, children }) {
  return (
    <div className="card">
      <div className="card__head">
        <h2>{head}</h2>
        <span className="card__badge">{badge}</span>
      </div>
      {children}
    </div>
  );
}

function DistributionChart() {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={distributionData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.04)" vertical={false} />
        <XAxis dataKey="name" tick={tickStyle} axisLine={false} tickLine={false} />
        <YAxis hide />
        <Tooltip {...tooltipStyle} formatter={(val) => [`${val} 题`, "数量"]} />
        <Bar dataKey="value" radius={[8, 8, 3, 3]} maxBarSize={64}>
          {distributionData.map((entry, idx) => (
            <Cell key={idx} fill={entry.color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

function ModuleChart() {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={moduleData} layout="vertical" margin={{ top: 0, right: 0, bottom: 0, left: 72 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.04)" horizontal={false} />
        <XAxis type="number" domain={[0, 100]} hide />
        <YAxis
          type="category" dataKey="name"
          tick={{ ...tickStyle, fontSize: 14, fill: "#292520" }}
          axisLine={false} tickLine={false}
          width={70}
        />
        <Tooltip
          {...tooltipStyle}
          formatter={(val, _name, props) => [`${val} 分`, `${props.payload.total} 题`]}
        />
        <Bar dataKey="score" radius={[0, 6, 6, 0]} fill={MODULE_BAR} maxBarSize={16} />
      </BarChart>
    </ResponsiveContainer>
  );
}

function TrendChart() {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={trendData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.04)" vertical={false} />
        <XAxis dataKey="date" tick={tickStyle} axisLine={false} tickLine={false} />
        <YAxis hide domain={[0, 100]} />
        <Tooltip {...tooltipStyle} />
        <Legend
          iconType="line"
          wrapperStyle={{ fontFamily: "-apple-system, sans-serif", fontSize: 12, color: "rgba(41,37,32,0.6)" }}
        />
        <Line
          type="monotone" dataKey="score" name="分数"
          stroke={CORRECT} strokeWidth={2.5}
          dot={{ r: 4, fill: "#fff", stroke: CORRECT, strokeWidth: 2 }}
          activeDot={{ r: 6, fill: CORRECT, stroke: "#fff", strokeWidth: 2 }}
        />
        <Line
          type="monotone" dataKey="accuracy" name="达标率"
          stroke={PERFECT} strokeWidth={2} strokeDasharray="6 4"
          dot={{ r: 3, fill: "#fff", stroke: PERFECT, strokeWidth: 1.5 }}
          activeDot={{ r: 5, fill: PERFECT, stroke: "#fff", strokeWidth: 2 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

function App() {
  return (
    <>
      <div className="side-grid">
        <Card head="结果分布" badge="10 题">
          <DistributionChart />
        </Card>
        <Card head="模块表现" badge="4 组">
          <ModuleChart />
        </Card>
      </div>
      <Card head="历史趋势" badge="最近 6 次">
        <TrendChart />
      </Card>
    </>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(
  React.createElement(App)
);
