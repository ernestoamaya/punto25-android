# Security Policy

Punto25 is currently Alpha software. Do not treat the Android client as a security boundary and do not use Alpha-only controls as production security.

## Reporting a vulnerability

Please do not publish exploitable vulnerabilities, credentials, private user data or proof-of-concept attacks in a public issue.

When private vulnerability reporting is enabled for this repository, use that channel. Until then, contact the repository owner through an appropriate private channel.

Include only the information necessary to reproduce and understand the issue. Do not include real user credentials, payment data or personal data.

## Secrets and credentials

Never commit:

- signing keystores or private signing keys;
- passwords or tokens;
- `.env` files containing credentials;
- Firebase service-account private keys;
- Meta/WhatsApp application secrets;
- production API credentials;
- private certificates.

Client configuration that must be present in an Android application is not, by itself, a server-side authorization control. Sensitive authorization decisions must be enforced on trusted backend infrastructure.

## Current Alpha limitations

The current Alpha still contains local-development architecture that is not intended to provide production security guarantees. Production deployment requires server authority, appropriate authentication/authorization, data-access rules, private storage for sensitive documents, session/token controls and other hardening tracked separately.

## Signing

Stable Alpha signing material is kept outside Git and must be provided to trusted CI through repository secrets. Production/Play signing material must be separate from Alpha signing material.
