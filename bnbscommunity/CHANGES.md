# Change record

## Removed AI Analyse and AI Trading

- Removed both routes and their page-specific code, including the unused navigation entries.
- Removed the Firebase configuration and dependency used only by AI Trading.
- The standalone analysis project and remote services and data are unchanged.
- Verified both removed routes return HTTP 404 and `/holders` and `/kyberswap` return HTTP 200 on the local development server.
- Confirmed no application-code references remain and the root dependency and lockfile entries were removed.
- Lockfile update reported an existing React Native peer-dependency warning. No production build was run.
