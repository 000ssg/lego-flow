
# HTTP Auth Aggregator — Requirements

## Commit: (initial) — HTTP Authentication Framework

### Original Request
> "implement HTTP authentication schemes"

### Reformulated Requirements
1. OAuth 2.0 token endpoints and resource server validation
2. Basic and Digest authentication per RFC 7617
3. SSO (Single Sign-On) with session management
4. SPNEGO/Kerberos integration via GSSAPI

### Final Design Decisions
- Split into sub-modules: core, basic-digest, oauth, sso, spnego
- Core module provides shared SPI and token abstractions
- Each scheme implemented as independent module

### Implementation Details
- Aggregator POM groups 5 sub-modules
- See sub-module REQUIREMENTS.md files for details

### Test Coverage
- core: See core/doc/REQUIREMENTS.md
- basic-digest: See basic-digest/doc/REQUIREMENTS.md
- oauth: See oauth/doc/REQUIREMENTS.md
- sso: See sso/doc/REQUIREMENTS.md
- spnego: See spnego/doc/REQUIREMENTS.md
