# 🚀 Stripe Payment Integration - Backend Quick Reference

## ✅ What's Been Implemented

Your backend now has a complete Stripe payment integration with:

### 📦 New Files Created (7 files)
1. **Services:** `PaymentService.java`, `StripeWebhookService.java`
2. **Controllers:** `PaymentController.java`
3. **DTOs:** `PaymentRequestDto.java`, `CheckoutSessionResponseDto.java`, `CreditsResponseDto.java`
4. **Docs:** This guide + full implementation guide

### 🔄 Modified Files (5 files)
1. **Model:** `User.java` (added credits fields)
2. **Services:** `ChatLLMService.java` (credit validation & deduction)
3. **Controllers:** `UserController.java`, `ChatLLMController.java`
4. **Config:** `build.gradle`, `application.properties`

---

## 🛠️ Required Setup

### Step 1: Environment Variables
Create/update `.env` file:
```env
STRIPE_API_KEY=sk_test_YOUR_KEY_HERE
STRIPE_WEBHOOK_SECRET=whsec_YOUR_SECRET_HERE
STRIPE_PRICE_CREDITS_50=price_YOUR_ID
STRIPE_PRICE_CREDITS_150=price_YOUR_ID
STRIPE_PRICE_CREDITS_500=price_YOUR_ID
STRIPE_PRICE_LIFETIME=price_YOUR_ID
```

### Step 2: Get Stripe Keys
1. Go to https://dashboard.stripe.com/test/apikeys
2. Copy **Secret Key** → `STRIPE_API_KEY`
3. Go to Developers → Webhooks
4. Add webhook endpoint: `your-domain.com/api/payments/webhook`
5. Select event: `checkout.session.completed`
6. Copy **Signing Secret** → `STRIPE_WEBHOOK_SECRET`

### Step 3: Create Stripe Products
1. Dashboard → Products
2. Create 4 products with prices:
   - 50 Credits ($9.99)
   - 150 Credits ($24.99)
   - 500 Credits ($59.99)
   - Lifetime Access ($99.99)
3. Copy each price ID to environment variables

### Step 4: Database Migration
Either:
- Let Hibernate auto-create columns (default: `ddl-auto=update`)
- Or run manually:
```sql
ALTER TABLE users ADD COLUMN remaining_credits INT DEFAULT 0;
ALTER TABLE users ADD COLUMN has_lifetime_access BOOLEAN DEFAULT FALSE;
```

---

## 📡 API Endpoints

### Create Checkout Session
```bash
POST /api/payments/checkout
Authorization: Bearer YOUR_JWT

{
  "type": "credits",          # or "lifetime"
  "packageId": "credits-50",   # credits-50, credits-150, credits-500, lifetime
  "amount": 9999,              # amount in cents
  "credits": 50                # optional, credits to add
}

Response:
{
  "sessionId": "cs_test_...",
  "url": "https://checkout.stripe.com/pay/..."
}
```

### Webhook Handler
```bash
POST /api/payments/webhook
Stripe-Signature: t_timestamp.signature

(Stripe sends this automatically)
```

### Get User Credits
```bash
GET /users/credits
Authorization: Bearer YOUR_JWT

Response:
{
  "remainingCredits": 42,
  "hasLifetimeAccess": false
}
```

### Send Chat Message
```bash
POST /chat-llm/{chatId}
Authorization: Bearer YOUR_JWT

{
  "question": "Summarize this chat"
}

Response (success):
{
  "response": "...",
  "remainingCredits": 41,
  "hasLifetimeAccess": false
}

Response (no credits):
HTTP 402
"Insufficient credits. Please purchase credits to continue."
```

---

## 🔐 How It Works

### Payment Flow
```
1. User clicks "Add Credits" → PaymentModal opens
2. User selects package → Frontend calls POST /api/payments/checkout
3. Backend creates Stripe session → Returns checkout URL
4. Frontend redirects to Stripe Checkout
5. User enters card details & completes payment
6. Stripe sends webhook → Backend updates user credits
7. Stripe redirects to /payment-success → Frontend shows success
```

### Chat Flow
```
1. User sends message
2. Backend checks: Has lifetime access OR has credits?
   - YES: Process request, deduct 1 credit, return response
   - NO: Return HTTP 402 error
3. Frontend shows remaining credits
4. If 0 credits: Show "Add Credits" button
```

---

## 🧪 Test It

### Test Data
Use Stripe test card: **4242 4242 4242 4242**
- Expiry: Any future date (e.g., 12/25)
- CVC: Any 3 digits (e.g., 123)

### Test Payment Endpoint
```bash
curl -X POST http://localhost:8080/api/payments/checkout \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "credits",
    "packageId": "credits-50",
    "credits": 50,
    "amount": 9999
  }'
```

### Monitor Webhooks
Dashboard → Webhooks → Select your endpoint → See event logs

---

## ⚠️ Common Issues & Fixes

### Issue: "Unknown package" error
**Fix:** Ensure `STRIPE_PRICE_CREDITS_*` env vars are set in `application.properties`

### Issue: Webhook not working
**Fix:** Verify webhook signing secret matches in `.env` as `STRIPE_WEBHOOK_SECRET`

### Issue: 402 errors when user has credits
**Fix:** Check that `ChatLLMService` receives the updated `User` object with credentials

### Issue: Stripe dependency not found
**Fix:** Run `./gradlew clean build` to refresh dependencies

---

## 📊 Database Schema

```sql
-- Added to users table:
remaining_credits INT DEFAULT 0
has_lifetime_access BOOLEAN DEFAULT FALSE
```

**Indexes (recommended):**
```sql
CREATE INDEX idx_remaining_credits ON users(remaining_credits);
CREATE INDEX idx_has_lifetime_access ON users(has_lifetime_access);
```

---

## 🔗 Frontend Integration Points

Frontend expects these endpoints to exist (all implemented):

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/payments/checkout` | POST | Create payment session |
| `/api/payments/webhook` | POST | Handle Stripe events |
| `/users/credits` | GET | Get credit balance |
| `/chat-llm/{chatId}` | POST | Send message (with credit validation) |

---

## 📝 File Locations

```
backend/
├── src/main/java/com/whatsappanalytics/whatsapp_analytics_backend/
│   ├── controller/
│   │   ├── PaymentController.java          ✨ NEW
│   │   ├── UserController.java             ✏️ MODIFIED
│   │   └── ChatLLMController.java          ✏️ MODIFIED
│   ├── service/
│   │   ├── PaymentService.java             ✨ NEW
│   │   ├── StripeWebhookService.java       ✨ NEW
│   │   └── ChatLLMService.java             ✏️ MODIFIED
│   ├── dto/
│   │   ├── PaymentRequestDto.java          ✨ NEW
│   │   ├── CheckoutSessionResponseDto.java ✨ NEW
│   │   └── CreditsResponseDto.java         ✨ NEW
│   └── model/
│       └── User.java                       ✏️ MODIFIED
├── src/main/resources/
│   └── application.properties               ✏️ MODIFIED
├── build.gradle                            ✏️ MODIFIED
├── STRIPE_IMPLEMENTATION.md                ✨ NEW
└── IMPLEMENTATION_SUMMARY.md               ✨ NEW
```

---

## 🚀 Production Checklist

- [ ] Stripe production keys configured
- [ ] Webhook endpoint added to Stripe Dashboard
- [ ] Database migration executed
- [ ] Environment variables set on server
- [ ] SSL certificate configured
- [ ] CSRF protection enabled
- [ ] Rate limiting configured
- [ ] Error logging monitored
- [ ] Payment tests passed
- [ ] Credit deduction tests passed

---

## 📞 Support

For issues:
1. Check logs in `application.properties` (logging.level.*)
2. Review Stripe webhook logs in Dashboard
3. Test with Stripe test keys first
4. See `STRIPE_IMPLEMENTATION.md` for detailed guide

---

**Implementation Date:** December 2025
**Status:** ✅ Complete & Ready for Testing
