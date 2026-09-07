# Farmsy — Legal & Compliance Checklist (for Luuk)

The iOS app now links to these URLs — **they must exist before App Store submission**:

| URL | Linked from | Status |
|---|---|---|
| `https://farmsy.app/privacy` | Settings → Privacy Policy | ⬜ publish page |
| `https://farmsy.app/terms` | Settings → Terms of Service | ⬜ publish page |
| `https://www.farmsy.app/profile` | Settings → Delete account | ⬜ page live ✅ — must offer full account deletion |

## 1. Privacy Policy (GDPR — required, NL/BE users)
Must state, in plain language:
- **What we collect**: account email + password (Supabase auth), favourites, subscription status (payment handled on the web), approximate/precise location (only while using the app, for nearby farms), farm submissions (name, contact details, photos, coordinates), farm claim requests (name, email, phone, KVK number).
- **Legal basis**: contract (account + subscription), consent (location, notifications), legitimate interest (map data).
- **Where it lives**: Supabase (EU region if so — confirm project region), Stripe (payments, web).
- **Retention + rights**: right to access, correct, delete, export; contact hello@farmsy.app.
- Data Protection Officer / controller identity: Vision Tech B.V., registered address, KVK number.

## 2. Account deletion (Apple requirement — App Review will check)
Apple Guideline 5.1.1(v): apps with account creation **must let users initiate account deletion inside the app**. The app now opens `farmsy.app/account` from Settings → Delete account. That page must actually offer full deletion (not just "email us"). Proper fix later: a `DELETE /api/account` endpoint the app can call directly.

## 3. App Store privacy "nutrition labels" (App Store Connect → App Privacy)
Declare before first submission:
- Contact Info: email (linked to identity)
- Location: precise location (app functionality, not linked to identity if you don't store it — we currently don't store it server-side)
- Identifiers: user ID
- User Content: photos, other user content (farm submissions)
- Purchases: subscription status
- No tracking, no third-party ads → "Data Not Used to Track You"

## 4. User-generated content policy (farm submissions + claims)
Apple 1.2 requires apps with UGC to have: a way to report objectionable content, a way to block/hide it, and published content rules. Minimum: add a "Report" mail link on farm pages (or handle via hello@farmsy.app) and a short content/takedown section in the Terms.

## 5. Terms of Service
Cover: subscription terms (billing happens on farmsy.app, renewal, cancellation), that farm data comes partly from OpenStreetMap (attribution: ODbL — add "© OpenStreetMap contributors" somewhere reachable, e.g. the Terms page), user submissions license (you may display/edit them), liability disclaimer for farm info accuracy.

## 6. Consent & cookies (web side)
The app itself sets no trackers. The web dashboard/payment flow should keep its cookie banner GDPR-compliant since app users are sent there for subscribe/delete.

## 7. Before final App Store release
- **Run the App Review account check** in the web repo and do not submit while it fails: `npm run check:review-accounts` (needs `.env.local`). It prints role, subscription state, `email_verified` and any claims for the three `appreview.*` accounts, computes access with the real rule, and exits non-zero if the expired account has access, any review account has an approved claim, or a reviewer could not log in. An approved test claim silently makes an account a farmer, and farmers never see the paywall — that is the 2.1 rejection, and it has happened once already.
- Replace the upscaled 377px app icon with a hi-res (1024px+) or vector export of the new barn logo.
- Location permission text is already user-facing; translate it (done in the app's localization catalogs).
- Confirm Supabase project region + Stripe DPA are documented in the privacy policy.
