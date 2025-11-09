import * as React from 'react';
import { BarChart } from '@mui/x-charts/BarChart';
import { readCSV, getFinalValueByGroup } from './CSVReader';

interface ChartData {
  xAxis: Array<{ data: string[]; label: string }>;
  yAxis: Array<{ label: string }>;
  series: Array<{ data: number[]; label: string }>;
}

export default function MuiBarChart() {
  const [chartData, setChartData] = React.useState<ChartData | null>(null);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    const loadData = async () => {
      try {
        const csvData = await readCSV('df_react.csv');
        const senderTotals = getFinalValueByGroup(csvData.rows, 'sender', 'accumulated_messages');
        
        setChartData({
          xAxis: [{ data: Object.keys(senderTotals), label: 'Senders' }],
          yAxis: [{ label: 'Total Messages' }],
          series: [{ data: Object.values(senderTotals), label: 'Total Messages' }]
        });
        
        setLoading(false);
      } catch (error) {
        console.error('Error loading CSV:', error);
        setLoading(false);
      }
    };
    loadData();
  }, []);

  if (loading) return <div>Loading...</div>;
  if (!chartData) return <div>Error loading data</div>;

  return (
    <BarChart
      xAxis={chartData.xAxis}
      yAxis={chartData.yAxis}
      series={chartData.series}
      height={300}
      margin={{ top: 10, right: 5, left: 5, bottom: 5 }}  
    />
  );
}
