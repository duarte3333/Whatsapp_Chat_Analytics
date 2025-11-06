import * as React from 'react';
import { LineChart } from '@mui/x-charts/LineChart';
import { readCSV, countByWeek } from './CSVReader';

interface ChartData {
  xAxis: Array<{ data: number[]; label: string; valueFormatter?: (value: number) => string }>;
  yAxis: Array<{ label: string }>;
  series: Array<{ data: number[]; label: string; showMark?: boolean }>;
}

export default function LineChartByWeek() {
  const [chartData, setChartData] = React.useState<ChartData | null>(null);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    const loadData = async () => {
      try {
        const csvData = await readCSV('df_react.csv');
        const weekCounts = countByWeek(csvData.rows, 'datetime');
        
        // Sort weeks chronologically and convert to timestamps
        const sortedWeeks = Object.keys(weekCounts).sort();
        const weekTimestamps = sortedWeeks.map(week => new Date(week).getTime());
        const weekValues = sortedWeeks.map(week => weekCounts[week]);
        
        setChartData({
          xAxis: [{ 
            data: weekTimestamps, 
            label: 'Week',
            valueFormatter: (value: number) => {
              const date = new Date(value);
              return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
            }
          }],
          yAxis: [{ label: 'Total Messages' }],
          series: [{ data: weekValues, label: 'Messages per Week', showMark: false }]
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
    <LineChart
      xAxis={chartData.xAxis}
      yAxis={chartData.yAxis}
      series={chartData.series}
      height={300}
      margin={{ top: 20, right: 30, left: 20, bottom: 100 }}
    />
  );
}

