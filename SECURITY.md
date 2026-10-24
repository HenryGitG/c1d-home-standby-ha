# Security

- Generate a unique standby token for every private build. Never commit that token.
- Port 8765 is intended only for a trusted LAN. Never forward it to the internet.
- The HTTP API uses bearer-token authentication but no TLS; use a trusted, isolated home network.
- Do not commit Home Assistant `secrets.yaml`, configuration backups or device identifiers.
- The display-control implementation is specific to the tested C1D firmware and must not be assumed safe on other devices.

Please report repository vulnerabilities through GitHub Security Advisory. Do not include live tokens or household network details in public issues.
