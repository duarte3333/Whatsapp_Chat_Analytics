# Frontend Errors - Solutions

## 🔴 Error 1: Stripe Key Not Configured

### Error:
```
[PaymentModal] VITE_STRIPE_PUBLIC_KEY is not set!
Uncaught (in promise) IntegrationError: Please call Stripe() with your publishable key. You used an empty string.
```

### Solution:

**1. Create/Update `.env` file in frontend root:**
```env
VITE_STRIPE_PUBLIC_KEY=pk_test_YOUR_PUBLISHABLE_KEY_HERE
```

**2. Get Stripe Publishable Key:**
- Go to https://dashboard.stripe.com/test/apikeys
- Copy **Publishable key** (starts with `pk_test_` for test mode)
- Add to `.env` file

**3. Update PaymentModal.tsx to handle missing key gracefully:**

```tsx
// PaymentModal.tsx
const stripePublishableKey = import.meta.env.VITE_STRIPE_PUBLIC_KEY;

useEffect(() => {
  if (!stripePublishableKey) {
    console.warn('[PaymentModal] VITE_STRIPE_PUBLIC_KEY is not set!');
    // Optionally disable payment features or show a message
    return;
  }
  
  // Initialize Stripe only if key is available
  loadStripe(stripePublishableKey).then(setStripe);
}, []);
```

**4. Restart dev server** after adding `.env` file:
```bash
npm run dev
# or
yarn dev
```

---

## 🔴 Error 2: ShowcasePieChart Not Defined

### Error:
```
ReferenceError: ShowcasePieChart is not defined
at Landing (Landing.tsx:720:77)
```

### Solution:

**Option 1: Import the component (if it exists):**

```tsx
// Landing.tsx
import ShowcasePieChart from './components/ShowcasePieChart';
// or wherever the component is located
```

**Option 2: Create the component if it doesn't exist:**

```tsx
// components/ShowcasePieChart.tsx
import React from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';

interface ShowcasePieChartProps {
  data?: Array<{ name: string; value: number }>;
}

const ShowcasePieChart: React.FC<ShowcasePieChartProps> = ({ 
  data = [
    { name: 'Group A', value: 400 },
    { name: 'Group B', value: 300 },
    { name: 'Group C', value: 300 },
  ]
}) => {
  const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042'];

  return (
    <ResponsiveContainer width="100%" height={300}>
      <PieChart>
        <Pie
          data={data}
          cx="50%"
          cy="50%"
          labelLine={false}
          label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
          outerRadius={80}
          fill="#8884d8"
          dataKey="value"
        >
          {data.map((entry, index) => (
            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
          ))}
        </Pie>
        <Tooltip />
        <Legend />
      </PieChart>
    </ResponsiveContainer>
  );
};

export default ShowcasePieChart;
```

**Option 3: Remove/comment out if not needed:**

```tsx
// Landing.tsx - line 720
// {ShowcasePieChart && <ShowcasePieChart />}  // Commented out until component is created
```

---

## 🔴 Error 3: Upload Returns 400 Bad Request

### Error:
```
POST http://localhost:8080/metrics/chat/upload-anonymous 400 (Bad Request)
```

### Possible Causes & Solutions:

**1. File type validation issue (after adding .txt support):**

Check if the file being uploaded is valid:
- Must be `.zip` OR `.txt`
- Must be ≤ 5MB
- If `.zip`, must contain `_chat.txt` file

**2. Check backend logs for specific error:**

Look at backend console/logs to see the exact validation error message.

**3. Update GuestUpload.tsx to handle errors better:**

```tsx
// GuestUpload.tsx
const handleUpload = async (file: File) => {
  try {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('http://localhost:8080/metrics/chat/upload-anonymous', {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const errorData = await response.json();
      console.error('Upload error:', errorData);
      // Show user-friendly error message
      alert(`Upload failed: ${errorData.error || 'Unknown error'}`);
      return;
    }

    const data = await response.json();
    // Handle success
  } catch (error) {
    console.error('Upload error:', error);
    alert('Failed to upload file. Please try again.');
  }
};
```

**4. Verify file validation:**

```tsx
// Before upload, validate file
const validateFile = (file: File): string | null => {
  const fileName = file.name.toLowerCase();
  
  // Check file type
  if (!fileName.endsWith('.zip') && !fileName.endsWith('.txt')) {
    return 'File must be a ZIP file or TXT file';
  }
  
  // Check file size (5MB)
  const maxSize = 5 * 1024 * 1024;
  if (file.size > maxSize) {
    return 'File size exceeds 5MB limit';
  }
  
  return null; // Valid
};

// Use before upload
const validationError = validateFile(selectedFile);
if (validationError) {
  alert(validationError);
  return;
}
```

---

## 🔧 Quick Fix Checklist

- [ ] **Stripe Key**: Add `VITE_STRIPE_PUBLIC_KEY` to `.env` file
- [ ] **ShowcasePieChart**: Import or create the component, or remove/comment it out
- [ ] **Upload Error**: Check backend logs, verify file type/size, improve error handling
- [ ] **Restart dev server** after `.env` changes

---

## 📝 Environment Variables Template

Create `.env` file in frontend root:

```env
# Stripe Configuration
VITE_STRIPE_PUBLIC_KEY=pk_test_YOUR_KEY_HERE

# Backend API URL (if needed)
VITE_API_URL=http://localhost:8080

# Frontend URL (if needed)
VITE_FRONTEND_URL=http://localhost:8081
```

---

## 🐛 Debugging Tips

1. **Check browser console** for detailed error messages
2. **Check Network tab** to see the actual request/response
3. **Check backend logs** for server-side errors
4. **Verify file format** - ensure it's a valid WhatsApp export
5. **Test with a small file first** to rule out size issues

---

## 📞 Common Issues

### Issue: Stripe still shows error after adding key
**Solution**: 
- Make sure `.env` file is in the **frontend root** directory
- Restart dev server completely
- Check that variable name is exactly `VITE_STRIPE_PUBLIC_KEY` (case-sensitive)

### Issue: ShowcasePieChart error persists
**Solution**:
- Check if component file exists
- Verify import path is correct
- Check for typos in component name
- Temporarily comment out the component usage

### Issue: Upload still fails with 400
**Solution**:
- Check backend validation logs
- Verify file is actually `.zip` or `.txt`
- Ensure file size is under 5MB
- Test with a known-good WhatsApp export file

