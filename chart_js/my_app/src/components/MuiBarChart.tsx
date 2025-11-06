// import * as React from 'react';
// import { BarChart } from '@mui/x-charts/BarChart';

// export default function MuiBarChart() {
//   return (
//     <BarChart
//       xAxis={[{ data: ['group A', 'group B', 'group C'], label: "Groups" }]}
//       yAxis={[{label: "Amount of Sales"}]}
//       series={[{ data: [4, 3, 5], label: "Winter" }, { data: [1, 6, 3], label: "Spring" }, { data: [2, 5, 6], label: "Summer" }]}
//       height={450}
//       margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
//       //colors={['#0088FE', '#00C49F', '#FFBB28']}
//       //borderRadius={8}
//     />
//   );
// }
import * as React from 'react';
import Papa from 'papaparse';
import { BarChart } from '@mui/x-charts/BarChart';

interface ChartData {
  xAxis: Array<{ data: string[]; label: string }>;
  yAxis: Array<{ label: string }>;
  series: Array<{ data: number[]; label: string }>;
}

interface CSVRow {
    timestamp: string;
    date: string;
    time: string;
    sender: string;
    message: string;
    media: string;
    line_number: string;
    datetime: string;
    hour: string;
    day_of_week: string;
    month: string;
    year: string;
    emotion: string;
    day: string;
    accumulated_messages: string;
}

export default function MuiBarChart() {
  const [chartData, setChartData] = React.useState<ChartData | null>(null);

  React.useEffect(() => {
    Papa.parse('df_react.csv', {
      download: true,
      header: true,
      complete: (result: any) => {
        const data = result.data.filter((row: CSVRow) => row.sender); // filter out empty rows
        
        // Get unique senders with their final accumulated_messages value
        const senderMap = new Map<string, number>();
        data.forEach((d: CSVRow) => {
          const sender = d.sender;
          const accumulated = +d.accumulated_messages;
          // Keep the latest (highest) accumulated_messages for each sender
          if (!senderMap.has(sender) || accumulated > senderMap.get(sender)!) {
            senderMap.set(sender, accumulated);
          }
        });

        const xLabels = Array.from(senderMap.keys());
        const accumulated_messages = Array.from(senderMap.values());

        setChartData({
          xAxis: [{ data: xLabels, label: 'Senders' }],
          yAxis: [{ label: 'Total Messages' }],
          series: [
            { data: accumulated_messages, label: 'Total Messages' },
          ],
        });
      },
    });
  }, []);

  if (!chartData) return <div>Loading chart...</div>;

  return (
    <BarChart
      xAxis={chartData.xAxis}
      yAxis={chartData.yAxis}
      series={chartData.series}
      height={450}
      margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
    />
  );
}
