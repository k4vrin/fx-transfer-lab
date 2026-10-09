# ADR-0011: Use JWT access tokens and enforce resource ownership in the application

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Planned; security dependency exists, resource-server configuration absent

## Context

An authenticated caller can still submit another customer's account ID. A token
proves an identity and granted scopes only after verification; it does not prove
ownership of the source, destination, quote, or retrieved transfer.

## Decision and rationale

Use Spring Security as an OAuth2 Resource Server accepting JWT access tokens.
Validate signature and key trust, issuer, audience, expiry, not-before, and the
required `fx-transfer:read` or `fx-transfer:write` scope. In the foundation, `sub`
identifies the stable customer UUID. Do not build an authorization server here.

The application loads the relevant resources and verifies customer ownership.
Do not trust a request-supplied customer ID or treat an account ID as permission.
Use the verified identity for idempotency scope too, so retry results cannot be
shared across customers accidentally.

Preserve the existing error contract: invalid authentication is `401`, missing
scope is `403`, account ownership failure is `403` with `ACCOUNT_ACCESS_DENIED`,
and a caller-visible missing resource is `404`. Responses must not reveal another
customer's resource details. Use stable Problem Details codes from the contract.

## Alternatives and consequences

- Application-issued credentials would add identity lifecycle and token issuance
  to a project whose initial purpose is financial booking.
- API scopes alone cannot decide object ownership.
- Controller-only checks can be bypassed by another entry point to the use case.

This separates token verification from domain access rules. It requires a clear
issuer contract, key configuration, and application authorization tests.
The existing Spring Security starter does not by itself implement this resource
server configuration or establish ownership enforcement.

## Evidence and revisit conditions

See [authentication contract](../DESIGN_DECISIONS.md#11-authentication-and-authorization),
[HTTP matrix](../DESIGN_DECISIONS.md#9-http-and-stable-error-contract), and
[current dependencies](../../pom.xml).

Revisit for operator roles, joint ownership, delegated access, a separate party
mapping, or a token subject that no longer equals the internal customer UUID.
