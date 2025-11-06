import * as React from 'react';
import { Typography, Box } from '@mui/material';
import { readCSV } from './CSVReader';

export default function DateRange() {
  const [startDate, setStartDate] = React.useState<string | null>(null);
  const [endDate, setEndDate] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    const loadData = async () => {
      try {
        const csvData = await readCSV('df_react.csv');
        
        // Find earliest and latest dates
        let earliestDate: Date | null = null;
        let latestDate: Date | null = null;
        
        csvData.rows.forEach(row => {
          const dateStr = row['datetime'];
          if (!dateStr || dateStr.trim() === '') return;
          const date = new Date(dateStr.trim());
          if (isNaN(date.getTime())) return;
          
          if (!earliestDate || date < earliestDate) {
            earliestDate = date;
          }
          if (!latestDate || date > latestDate) {
            latestDate = date;
          }
        });

        if (earliestDate) {
          const formatted = (earliestDate as Date).toLocaleDateString('en-US', { 
            year: 'numeric', 
            month: 'short', 
            day: 'numeric' 
          });
          setStartDate(formatted);
        }

        if (latestDate) {
          const formatted = (latestDate as Date).toLocaleDateString('en-US', { 
            year: 'numeric', 
            month: 'short', 
            day: 'numeric' 
          });
          setEndDate(formatted);
        }

        setLoading(false);
      } catch (error) {
        console.error('Error loading CSV:', error);
        setLoading(false);
      }
    };
    loadData();
  }, []);

  if (loading) return <div>Loading...</div>;
  if (!startDate || !endDate) return <div>Error loading data</div>;

  return (
    <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>
        Date Range
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
        <Typography variant="body1" component="div" sx={{ fontWeight: 500 }}>
          {startDate}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          →
        </Typography>
        <Typography variant="body1" component="div" sx={{ fontWeight: 500 }}>
          {endDate}
        </Typography>
      </Box>
    </Box>
  );
}

