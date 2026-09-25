# Puppy Clicker — Desktop

This folder is reserved for a future desktop edition of Puppy Clicker. No desktop executable or implementation has been added yet.

Future desktop clients must follow the [PupEye global enforcement client contract](../docs/pupeye/global-enforcement-client-contract.md), using secure key storage supported by the chosen platform.

## Intended structure

Keep desktop-specific source, packaging, installers, and configuration here once a platform and framework are selected. Use the existing shared artwork in [`../assets/`](../assets/) and preserve the established game and character identities.

The desktop edition can share code with the website where appropriate, but it should remain independently buildable and should not disturb the native Android project in `../App/`. No desktop framework or supported operating-system list has been selected yet.
