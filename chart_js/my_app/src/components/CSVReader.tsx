import Papa from 'papaparse';

interface CSVRow {
  [key: string]: string;
}

interface CSVData {
  rows: CSVRow[];
  columns: string[];
}

export function readCSV(filename: string): Promise<CSVData> {
  return new Promise((resolve, reject) => {
    Papa.parse(filename, {
      download: true,
      header: true,
      complete: (result: any) => {
        const data = result.data as CSVRow[];
        const validRows = data.filter(row => 
          Object.values(row).some(value => value && value.trim() !== '')
        );
        const columns = validRows.length > 0 ? Object.keys(validRows[0]) : [];
        resolve({ rows: validRows, columns });
      },
      error: (error: Error) => reject(error)
    });
  });
}

export function getUniqueValues(rows: CSVRow[], columnName: string): string[] {
  const uniqueSet = new Set<string>();
  rows.forEach(row => {
    const value = row[columnName];
    if (value && value.trim() !== '') uniqueSet.add(value.trim());
  });
  return Array.from(uniqueSet);
}

//this function counts the values of a column
export function countValues(rows: CSVRow[], columnName: string): Record<string, number> {
  const counts: Record<string, number> = {};
  rows.forEach(row => {
    const value = row[columnName];
    if (value && value.trim() !== '') {
      const trimmed = value.trim();
      counts[trimmed] = (counts[trimmed] || 0) + 1;
    }
  });
  return counts;
}

//this function sums the values of a column by a group
export function sumByGroup(rows: CSVRow[], groupBy: string, sumColumn: string): Record<string, number> {
  const sums: Record<string, number> = {};
  rows.forEach(row => {
    const group = row[groupBy];
    const num = parseFloat(row[sumColumn]);
    if (group && !isNaN(num)) {
      const trimmed = group.trim();
      sums[trimmed] = (sums[trimmed] || 0) + num;
    }
  });
  return sums;
}

//this function gets the final (maximum) value for each group
export function getFinalValueByGroup(rows: CSVRow[], groupBy: string, valueColumn: string): Record<string, number> {
  const finalValues: Record<string, number> = {};
  rows.forEach(row => {
    const group = row[groupBy];
    const num = parseFloat(row[valueColumn]);
    if (group && !isNaN(num)) {
      const trimmed = group.trim();
      // Keep the maximum value (final entry)
      if (!finalValues[trimmed] || num > finalValues[trimmed]) {
        finalValues[trimmed] = num;
      }
    }
  });
  return finalValues;
}

//this function counts messages per week
export function countByWeek(rows: CSVRow[], dateColumn: string = 'datetime'): Record<string, number> {
  const weekCounts: Record<string, number> = {};
  
  rows.forEach(row => {
    const dateStr = row[dateColumn];
    if (!dateStr || dateStr.trim() === '') return;
    
    // Parse date: format is "2021-09-03 22:41:35"
    const date = new Date(dateStr.trim());
    if (isNaN(date.getTime())) {
      console.warn('Invalid date:', dateStr);
      return;
    }
    
    // Get start of week (Monday)
    const weekStart = getWeekStart(date);
    const weekKey = formatWeekKey(weekStart);
    
    weekCounts[weekKey] = (weekCounts[weekKey] || 0) + 1;
  });
  
  return weekCounts;
}

// Get Monday of the week for a given date
function getWeekStart(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay(); // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
  const diff = day === 0 ? -6 : 1 - day; // If Sunday, go back 6 days; otherwise go to Monday
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

// Format as "YYYY-MM-DD" (Monday of that week)
function formatWeekKey(weekStart: Date): string {
  const year = weekStart.getFullYear();
  const month = String(weekStart.getMonth() + 1).padStart(2, '0');
  const day = String(weekStart.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
