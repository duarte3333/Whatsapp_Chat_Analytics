import * as React from 'react';
import { Grid, Paper } from '@mui/material';
import BarChart from './BarChart';
import LineChartByWeek from './LineChartByWeek';

export default function Dashboard() {
  return (
    <Grid container spacing={2} sx={{ padding: 2 }}>
      {/* Row 1 */}
      <Grid size={{ xs: 12, sm: 4 }}>
        <Paper sx={{ height: '100%' }}>
        </Paper>
      </Grid>
      <Grid size={{ xs: 12, sm: 4 }}>
        <Paper sx={{ height: '100%' }}>
        </Paper>
      </Grid>
      <Grid size={{ xs: 12, sm: 4 }}>
        <Paper sx={{ height: '100%' }}>
          {/* Empty for now */}
        </Paper>
      </Grid>

      {/* Row 2 */}
      <Grid size={{ xs: 12, sm: 4 }}>
        <Paper sx={{height: '100%' }}>
          <BarChart />
        </Paper>
      </Grid>
      <Grid size={{ xs: 12, sm: 4 }}>
        <Paper sx={{height: '100%' }}>
          <LineChartByWeek />
        </Paper>
      </Grid>
      <Grid size={{ xs: 12, sm: 4 }}>
        <Paper sx={{height: '100%' }}>
            
        </Paper>
      </Grid>
    </Grid>
  );
}

