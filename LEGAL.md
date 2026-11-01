# Legal notes

This repository contains original source and documentation for controlling and
diagnosing Android projectors **that you own**, specifically the HY450 Max
(Magcubic/Hotack family, Allwinner H713).

This is not legal advice. If you need legal advice, consult a lawyer in your
jurisdiction.

## Repository boundaries

- **Original source only.** No firmware dumps, partition images, OEM framework
  jars, device-pulled binaries, signing keys, or generated APKs are included.
- **No personal data.** Do not submit serial numbers, MAC addresses, private
  network identifiers, personal backups, or other identifying device data.
- **No DRM circumvention.** Widevine, Play Integrity, streaming-service
  protections, and content decryption are out of scope and untouched.
- **Interop and owner control.** The code uses the device's existing Android
  and Allwinner interfaces to inspect and change settings on the user's own
  hardware. Compatibility is not guaranteed on other units or firmware.

## Terms of use

- Use only on devices you own or are authorized to modify.
- Privileged settings and root installation can void your warranty or make the
  device temporarily unusable. Back up original values and proceed at your own
  risk.
- Do not accept an OEM OTA with custom Magisk modules active. Disable custom
  modules before any deliberately planned system update and validate the app
  against the updated firmware before reinstalling it.
- The authors are not affiliated with Magcubic, Hotack, Allwinner, Google, or
  any other vendor mentioned. Trademarks belong to their owners and are used
  nominatively.
- All original content is provided under the
  [GNU General Public License v3.0 only](LICENSE), as-is, without warranty.
