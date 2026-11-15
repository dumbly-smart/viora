# ChatGPT agent inside Viora

Status: superseded; implementation has not started.

## Updated user requirement

The user clarified that the agent must use the actual chatgpt.com website,
not a native agent making Responses API requests with ChatGPT authorization.
The API-based proposal below must not be implemented under the earlier approval.
Retain the agreed per-user data/action controls in the replacement design.

The supported web-agent direction to evaluate is a Viora MCP connection used
from ChatGPT's website. Confirm the intended account/plan's connector permissions,
Android browser compatibility, and an authenticated connection back to the phone
before selecting a relay/tunnel architecture. A plain website wrapper alone
does not provide Viora tool access. The remaining sections document the
superseded proposal for context.

## Intent and agreed requirements

Add an agent inside Viora that users connect to their own ChatGPT accounts.
The user has confirmed that Viora is an open-source project and that each user
must choose what their agent can access and do. Preserve the custom UI and all
existing academic rules. Do not change the repository's license as part of this
integration; its current private-source wording needs a separate licensing choice.

## Approach

Use OpenAI's official Sign in with ChatGPT flow for open-source/local clients,
followed by direct Responses API requests and locally executed Viora tools.
This matches the requested in-app account connection and the current Android
architecture without introducing a Viora backend or a developer API key.

Alternatives considered: a ChatGPT plugin places the interaction in ChatGPT;
an API-funded backend adds separate billing and a new academic-data service.
Both change the requested experience. The recommended approach is the native
in-app agent. Account/plan eligibility remains enforced by OpenAI.

## User controls

An Agent section provides connection status, the chat entry point, data-sharing
permissions, action permissions, Manage usage, Disconnect, and Clear history.
The agent starts disconnected, with all academic sharing and actions disabled.

Data categories are individually selectable: timetable, attendance, exams,
assignments, marks/grades/CGPA, academic calendar, messages, and selected files.
Enabling a category explicitly authorizes sending the minimum relevant content
to OpenAI for the conversation. A file grant covers only files the user selects.

Each action has three settings: **Off**, **Ask each time**, and **Allow
automatically**. Initial actions cover refreshing academics, opening app screens,
changing reminder preferences, downloading selected materials, and submitting or
replacing an assessment using a user-selected file. Data access and action access
are independent; enabling one does not implicitly enable another.

Ask-each-time previews show the concrete operation and target before execution.
Automatic execution applies only within the permission's documented scope and
the current signed-in Viora account. Permissions are enforced by a local tool
dispatcher, including immediately before a queued operation executes.

Revocation cancels pending work and in-flight requests where possible. Start a
new model context after data permissions are revoked so earlier tool results are
not resent. Explain that revocation stops future sharing and cannot recall data
already transmitted. Disconnect removes local ChatGPT tokens and agent context;
provide OpenAI's account controls for server-side access and usage management.

## Authentication and Android lifecycle

Use the system browser, PKCE S256, state, nonce, and a persisted per-installation
host identifier. Initial registration uses the documented dynamic public-client
flow; retain the issued client ID only with the verified account registration.
Validate ID-token signature, issuer, audience, expiry, nonce, identity and granted
inference scopes. Identity sign-in alone does not authorize inference.

The documented callback is an HTTP listener bound only to 127.0.0.1 on an
available port with the exact registered path. Keep the selected URI identical
through authorization and exchange, and close the listener after completion,
cancellation, or timeout. Validate this browser/loopback flow on the Pixel before
building the complete chat experience. A killed app or incomplete attempt starts
fresh rather than reusing an authorization code. Do not request an always-running
service or weaken global cleartext restrictions for sign-in.

Store OAuth credentials and pending authentication state in app-private Keystore
encrypted storage, separate from VTOP credentials/cookies. Refresh expiring access
tokens; revoked sessions require reconnection. An unsuccessful reconnect leaves
the prior valid connection intact until the replacement is verified.

## Agent execution and data boundaries

Compose talks to an agent ViewModel and orchestrator. Academic reads go through
repositories/Room; academic calculations use existing deterministic domain
functions. Writes use existing application actions and repositories, retaining
deadline validation, theory/lab identity, host checks, and cache preservation.
Tool results include freshness information and explicitly represent missing data.

Use a bounded foreground tool loop with streaming, cancellation and typed errors.
Tool definitions/results use the route's supported function-tool format. Follow
current ChatGPT-plan request requirements, including store:false, stream:true,
local conversation context, and account model discovery. Do not assume hosted
MCP, file search, computer use or unrelated APIs are supported on this route.

Keep all VTOP passwords, cookies, CSRF tokens, CAPTCHA contents, OAuth credentials,
raw HTML and unrestricted database/filesystem access outside model-visible tools.
The agent receives only authorized structured academic context and selected file
content. Model text cannot grant permissions or become executable code/URLs.

OpenAI transport has its own strict host allowlist and client; never reuse the
VTOP cookie jar, browser headers, certificate pins or interceptor. Preserve the
VTOP-only client unchanged. Update privacy/network documentation to describe the
optional OpenAI connection and consented academic sharing before shipping it.
Do not claim that store:false eliminates all provider-side retention.

Chat is foreground and user-initiated in the first version. Background agents,
voice and arbitrary device control are outside this initial integration. Local
history is optional, encrypted, excluded from backup, and never a CI/log artifact.

## Failure and action recovery

Model unavailability, limits or expired ChatGPT sessions do not break ordinary
Viora screens or VTOP sync. Show the failed stage and offer reconnect, retry,
cancel, or Manage usage as appropriate; preserve valid academic caches.

Use operation IDs and receipts to avoid duplicate local actions. Revalidate account,
semester, permissions, file grant and deadline before writes. After an uncertain
upload result, reconcile with VTOP before offering another submission. A completed
remote submission cannot be promised an automatic rollback. Local setting changes
can be transactional; do not replay consequential writes after a timeout.

## Verification and builder discretion

Verify OAuth cancellation, invalid state/nonce/signature, process death, token
refresh/revocation and account replacement. Verify every tool under all three
permission modes, revocation while queued, context reset, and file-scope checks.
Use synthetic fixtures only and never record live tokens or academic payloads.
Test limits, malformed tool calls, unavailable data, and ambiguous upload results.

On the Pixel, verify actual browser authorization, one permitted academic query,
one denied tool, one confirmed local preference change, and disconnect. Do not
perform a live assessment submission merely to test the agent.

The builder may choose internal Kotlin class names, SSE/JWT libraries, bounded
loop constants and responsive layout details after checking dependency licensing
and existing UI patterns. Preserve one implementation on main and the installed
custom UI. No functionality is considered complete based solely on a login button.

## Official references

- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
