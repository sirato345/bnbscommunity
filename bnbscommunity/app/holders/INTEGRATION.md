# BNBs holders integration

## Migration in progress

- Replace the `/holders` iframe with the native BNBs Light holders interface.
- Migrate the CSV upload interface to `/holders/csv`; keep the existing Django backend.
- Preserve the original project and its existing deployment.
- Copied the existing holders components, styles, and images; added the Recharts dependency.
- The existing Django `/csv` endpoint responded successfully to a read-only request from the community origin.
- `/holders` now renders the copied interface without an iframe. `/holders/csv` uploads to the existing backend and returns to `/holders`.
- The interface displays errors on data or upload failures; unavailable token prices no longer fall back to fabricated values.
- Added valid table markup for native rendering and adjusted images and navigation for Next.js.
- `npm install` completed with an existing React Native peer-dependency warning. The existing community development server responded on port 3000.
- The first TypeScript check found a JSX syntax error in the upload error message; corrected before continuing verification.
- A second TypeScript check exceeded the deliberately limited 512 MB Node heap; a full type-check has not been completed.
- Browser verification found a mobile table nesting warning; corrected the table structure.
- Verified desktop data loading, charts, logos, and address search against the existing API. Verified mobile address search and chart selection with browser emulation and sample data.
- Verified CSV upload success/redirect and failure display using intercepted responses; no real file was uploaded. Verified data/price error displays with intercepted failures.
- Django upload POST preflight accepted both the community origin and localhost; actual upload authorization and production upload were not tested.
- No production build or physical-device test was run.
- TypeScript transpilation syntax checks passed for the migrated and modified code; `git diff --check` passed.
- Restored the original holders font, native form/table defaults, and an independently scrolling content area below the community header to match the former iframe presentation.
- Restored the search tables' original browser-default spacing, which Tailwind had reset.
- The mobile scrolling area begins below the taller mobile navigation header.
- Compared computed font and control styles with the deployed BNBs Light page; verified desktop and emulated mobile scrolling, search, and mobile chart selection. Physical-device validation remains pending.
- Restored normal button font weight so the Update button does not inherit the price table's bold text.
- Verified the Update button font family, size, and weight in desktop and emulated mobile browsers against the original desktop button.
- Removed the holders loading messages while retaining the data-loading error message.
- Verified the loading message is absent during a pending request and that mocked success and failure still display the table and error respectively.
- Validation and any remaining limitations will be recorded here.
