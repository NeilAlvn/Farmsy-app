#!/bin/bash
# Export a CI archive, restore the entitlements the keyless pipeline drops, and
# refuse to produce an IPA that would ship broken.
#
# Why this exists: testflight.yml archives with CODE_SIGNING_ALLOWED=NO, so the
# app in the archive is "not signed at all" and carries no entitlements.
# xcodebuild -exportArchive then signs from the provisioning profile's baseline
# only, silently dropping com.apple.developer.associated-domains and
# aps-environment. Every build from that pipeline shipped that way: universal
# links opened Safari instead of the app, so a referrer was never credited, and
# registerForRemoteNotifications could not succeed, so no device could receive a
# push. The IPA validated and uploaded with no warnings, which is why this went
# unnoticed across releases.
#
# Writing archived-expanded-entitlements.xcent into the archive is NOT enough on
# its own -- tested 2026-11-09: the .xcent was carried and exportArchive still
# produced the same four-key IPA. The re-sign below is what actually fixes it.
#
# Usage: scripts/export-and-verify.sh <archive-dir> [export-options.plist]
#   <archive-dir>          directory containing Farmsy.xcarchive
#   [export-options.plist] defaults to ~/farmsy-build/ExportOptions.plist
#
# Needs the Apple Distribution certificate and the "Farmsy App Store" profile in
# the local keychain. No key material is read by this script and none belongs in
# it: this repo is public.
set -euo pipefail

DIR="${1:?usage: export-and-verify.sh <archive-dir> [export-options.plist]}"
PLIST="${2:-$HOME/farmsy-build/ExportOptions.plist}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ID="Apple Distribution: Vision Tech B.V. (R8MCFDU64H)"
TEAM="R8MCFDU64H"
BUNDLE="app.farmsy.ios"
SRC="$REPO/Farmsy/Farmsy.entitlements"
ENTS="$DIR/merged.entitlements"

# Derived from the repo's own entitlements file rather than hardcoded, so a key
# added there is picked up here instead of going missing in every build -- which
# is the failure mode this script exists to end.
test -f "$SRC" || { echo "FATAL: $SRC is missing"; exit 1; }
for KEY in aps-environment com.apple.developer.associated-domains; do
  /usr/libexec/PlistBuddy -c "Print :$KEY" "$SRC" >/dev/null 2>&1 \
    || { echo "FATAL: $SRC no longer declares $KEY"; exit 1; }
done

cp "$SRC" "$ENTS"
# Xcode rewrites this when it signs an archive itself; CI never signs, and the
# source keeps "development" so local debug builds still reach the sandbox APNs.
/usr/libexec/PlistBuddy -c "Set :aps-environment production" "$ENTS"
# codesign --entitlements REPLACES rather than merges, so the identifier keys the
# profile would have supplied have to be carried explicitly or they vanish.
/usr/libexec/PlistBuddy -c "Add :application-identifier string $TEAM.$BUNDLE" "$ENTS"
/usr/libexec/PlistBuddy -c "Add :com.apple.developer.team-identifier string $TEAM" "$ENTS"
/usr/libexec/PlistBuddy -c "Add :beta-reports-active bool true" "$ENTS"
/usr/libexec/PlistBuddy -c "Add :get-task-allow bool false" "$ENTS"
plutil -lint "$ENTS"

test -f "$PLIST" || { echo "FATAL: $PLIST not found; pass one as the 2nd argument"; exit 1; }

cd "$DIR"
rm -rf export repack
xcodebuild -exportArchive -archivePath Farmsy.xcarchive -exportPath export \
  -exportOptionsPlist "$PLIST" | tail -3

mkdir -p repack && cd repack
unzip -q -o ../export/Farmsy.ipa
APP=$(ls -d Payload/*.app)

# Frameworks first, then the app with the entitlements the archive never carried.
for f in "$APP"/Frameworks/*.framework; do
  [ -e "$f" ] || continue
  codesign --force --sign "$ID" --timestamp "$f" >/dev/null
done
codesign --force --sign "$ID" --entitlements "$ENTS" --generate-entitlement-der \
  --timestamp "$APP" >/dev/null

# Refuse to hand back an IPA that would ship with links or push dead.
GOT=$(codesign -d --entitlements :- "$APP" 2>/dev/null | plutil -p - 2>/dev/null)
for KEY in "com.apple.developer.associated-domains" "aps-environment" \
           "application-identifier" "beta-reports-active" \
           "com.apple.developer.team-identifier"; do
  echo "$GOT" | grep -q "$KEY" || { echo "FATAL: $KEY missing after re-sign"; exit 1; }
done
echo "$GOT" | grep -q '"aps-environment" => "production"' \
  || { echo "FATAL: aps-environment is not production -- push would hit the sandbox APNs"; exit 1; }
codesign --verify --deep --strict "$APP"
echo "entitlements ok:"; echo "$GOT" | sed 's/^/    /'

# Correct entitlements are not the same thing as a shippable build. Apple checks
# them against the provisioning profile, and rejects the upload if the bundle
# claims anything the profile does not grant:
#
#   Validation failed (409) Invalid Code Signing Entitlements. The bundle
#   contains a key that is not included in the provisioning profile:
#   'aps-environment'
#
# That happened on 2026-11-09 with an IPA whose six entitlements were exactly
# right. The profile had been marked INVALID by Apple back in July, when the App
# ID gained the push capability and no profile was reissued -- and nothing local
# noticed, because signing against an invalid profile succeeds. The 409 arrived
# after a 25 MB upload. These checks cost a second and catch it here instead.
PROFILE_PLIST="$DIR/repack/.embedded-profile.plist"
security cms -D -i "$APP/embedded.mobileprovision" > "$PROFILE_PLIST" 2>/dev/null \
  || { echo "FATAL: cannot decode $APP/embedded.mobileprovision"; exit 1; }

python3 - "$PROFILE_PLIST" "$APP" <<'PYCHECK' || exit 1
import plistlib, subprocess, sys, datetime

prof = plistlib.load(open(sys.argv[1], "rb"))
allowed = prof.get("Entitlements", {})
name, uuid = prof.get("Name"), prof.get("UUID")
print("signed by profile: %s (%s)" % (name, uuid))

expiry = prof.get("ExpirationDate")
if expiry:
    now = datetime.datetime.now(datetime.timezone.utc)
    exp = expiry if expiry.tzinfo else expiry.replace(tzinfo=datetime.timezone.utc)
    if exp <= now:
        sys.exit("FATAL: profile '%s' expired on %s" % (name, exp))
    print("profile expires: %s" % exp.date())

der = subprocess.run(["codesign", "-d", "--entitlements", ":-", sys.argv[2]],
                     capture_output=True).stdout
start = der.find(b"<?xml")
have = plistlib.loads(der[start:]) if start >= 0 else {}

missing = [k for k in have if k not in allowed]
if missing:
    sys.exit("FATAL: the profile does not grant %s -- Apple will reject this "
             "upload with a 409. Reissue the profile." % ", ".join(sorted(missing)))

if allowed.get("aps-environment") != "production":
    sys.exit("FATAL: profile grants aps-environment=%r, not 'production'"
             % allowed.get("aps-environment"))

print("profile grants every entitlement the bundle claims")
PYCHECK

# Re-zip EVERYTHING the export produced, not just Payload. Zipping Payload alone
# silently drops Symbols/ and costs the build its native crash symbolication,
# and Apple accepts it with no warnings, so nothing would tell us.
rm -f ../export/Farmsy.ipa
zip -qry ../export/Farmsy.ipa . -x '.*'
cd ..
echo "top-level entries in the repacked IPA:"
unzip -Z1 export/Farmsy.ipa | cut -d/ -f1 | sort -u | sed 's/^/    /'
echo "built: $DIR/export/Farmsy.ipa"
/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" -c "Print :CFBundleVersion" \
  repack/Payload/*.app/Info.plist
