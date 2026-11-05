![Installr Logo](https://github.com/installr-io/installr/blob/main/docs/images/installr-logo.png)

A **multi-purpose package installer**

---

This repository is part of the **Installr** project and contains `ic`, a command-line client for `installrd`.

For full documentation, visit the main repository:<br>
👉 https://github.com/installr-io/installr

## Configuration

`ic` connects to `installrd` via HTTPS and authenticates with a client certificate (mTLS). The connections are
configured as named **configs** in `~/.ic/config.yaml`; certificates and keys are embedded as PEM:

```yaml
default-config: local

configs:
  local:
    url: https://localhost:9316
    ## optional: CA certificate(s) to verify the server; if missing, the Java trust store is used
    ca: |
      -----BEGIN CERTIFICATE-----
      ...
      -----END CERTIFICATE-----
    cert: |
      -----BEGIN CERTIFICATE-----
      ...
      -----END CERTIFICATE-----
    key: |
      -----BEGIN PRIVATE KEY-----
      ...
      -----END PRIVATE KEY-----

  prod:
    url: https://installrd.example.org:9316
    ...
```

The file contains private keys, so it must only be accessible by its owner; otherwise `ic` refuses to use it:

```bash
chmod 600 ~/.ic/config.yaml
```

To insert a PEM file with the required indentation, use e.g.:

```bash
sed 's/^/      /' alice.crt
```

`ic` uses the `default-config`; select another one with `-c`:

```bash
ic -c prod -n demo install demo-app:0.1.0
```

See the [installation guide](https://github.com/installr-io/installr/blob/main/docs/INSTALL.md#4-set-up-security-mtls--roles)
for how to create certificates and grant roles.
