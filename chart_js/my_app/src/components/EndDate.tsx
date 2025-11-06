import * as React from 'react';
import { Typography } from '@mui/material';
import { readCSV } from './CSVReader';

export default function EndDate() {
  const [endDate, setEndDate] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    const loadData = async () => {
      try {
        const csvData = await readCSV('df_react.csv');
        
        // Find latest date
        let latestDate: Date | null = null;
        csvData.rows.forEach(row => {
          const dateStr = row['datetime'];
          if (!dateStr || dateStr.trim() === '') return;
          const date = new Date(dateStr.trim());
          if (isNaN(date.getTime())) return;
          
          if (!latestDate || date > latestDate) {
            latestDate = date;
          }
        });

        if (latestDate) {
          const formatted = (latestDate as Date).toLocaleDateString('en-US', { 
            year: 'numeric', 
            month: 'long', 
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
  if (!endDate) return <div>Error loading data</div>;

  return (
    <div>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        End Date
      </Typography>
      <Typography variant="h6" component="div">
        {endDate}
      </Typography>
    </div>
  );
}

