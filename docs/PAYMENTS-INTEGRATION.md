# Payments Integration — mobile-side status log

This is the **mobile** (iOS + Android) sync record for the payments work. The
canonical web↔mobile *contract* lives in the web repo (`farmsy-test`); this file
is the git-tracked handoff between Chris (mobile) and Aviah (web).

**Source of truth for access, on every rail:** `profiles.subscription_status`,
written by each payment webhook (Stripe on web, Apple IAP, Google Play via
RevenueCat) and read back through `/api/profile/status`. RevenueCat only *sells*;
it never decides who is a member. Same rule Apple requires (guideline 3.1.1).

---

## 2026-07-13 — Android fully wired; iOS blocked by Apple account migration

### Android — DONE, ready to test
Both products are live in Play, verified by RevenueCat, and mapped into the
`Membership` entitlement and the current `default` offering:

| Package        | Product (store id)                    | State  |
| -------------- | ------------------------------------- | ------ |
| `$rc_annual`   | `farmsy_membership_yearly:yearly`     | active |
| `$rc_lifetime` | `farmsy_membership_lifetime:lifetime` | active |

- Play service account propagated, so RevenueCat can verify Play purchases.
- Cleaned out all Test Store junk products, deleted the `$rc_monthly` package
  (no monthly on any rail — DB `subscription_plan` CHECK is `('yearly','lifetime')`),
  and deleted the redundant second offering. Exactly one clean offering now.

### Needed from Aviah (web)
Run an **Android sandbox purchase test with the Supabase ledger open**, order
**yearly first, then lifetime**. Confirm the RevenueCat webhook writes to `profiles`:

- yearly  → `subscription_status='active'`, `subscription_plan='yearly'`
- lifetime → `subscription_status='active'`, `subscription_plan='lifetime'`

And confirm the **never-downgrade / lifetime-can't-be-revoked** logic (web deploy
`e19e696`) holds when the lifetime purchase lands. Confirm the webhook endpoint is
live + pointed at prod before we run it.

### iOS — BLOCKED by Apple (proven, not guessed)
Tested against Apple's own API with an **Admin** App Store Connect key:

- App Store Connect (users, apps, builds) → all **200 OK**.
- Every Developer Portal / signing endpoint (certificates, bundleIds, profiles,
  devices) → **403**: *"Unable to find a team with the given Team ID
  `R8MCFDU64H` to which you belong."*

App Store Connect is alive but the Developer Portal side is orphaned — the
personal→business account migration is stuck half-done. This blocks **all new
iOS builds AND iOS IAP activation**. Internal to Apple; not fixable on our side.

**Luuk action:** contact **Apple Developer Program Support** and get them to
repair/complete the team migration for `R8MCFDU64H`.

### iOS — pre-staged in RevenueCat as far as possible
Both iOS products exist and are attached to the `Membership` entitlement:

- `farmsy_membership_yearly`  — `inactive` (waiting on Apple)
- `farmsy_membership_lifetime` — record active in RC, but **no real App Store
  Connect IAP behind it yet**; deliberately NOT added to an offering package.

Deleted the stray `Farmsy_iOS` junk product. When Luuk clears the account, iOS is
a quick pass: create the two App Store Connect IAPs → ship a signed build → both
flip active → drop into the offering packages. Note: RC stored the iOS lifetime as
`non_renewing_subscription`; the real ASC IAP should be a **non-consumable** — RC
will re-sync the type from the store once the IAP exists (verify then).

### RevenueCat reference ids
- project `proj9adc7f29` · entitlement `entl9c18af89bd` (`Membership`)
- current offering `ofrng8eb88dc29e` (`default`)
- iOS app `app26eded3107` · Android app `app76ec11fd97`
