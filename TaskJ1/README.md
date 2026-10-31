# Task J1

Java 17+, Gradle Wrapper. Run commands from `TaskJ1`.

## Build and tests

```bash
./gradlew test installDist
```

The distribution includes Bouncy Castle. The RSA-8192 test can take noticeably longer than other tests.

Coverage reports are generated after `./gradlew test`. Open
`build/reports/jacoco/test/html/index.html` for the HTML report. The XML report is
`build/reports/jacoco/test/jacocoTestReport.xml`. No application classes are excluded.

## Server

```bash
./build/install/TaskJ1/bin/TaskJ1 --port 8080 --threads 4 --issuer-name "Nerlikh67CoolCerts" --issuer-key config/SUPER_SECRET_ISSUER.key
```

Alternatively:

```bash
./gradlew run --args='--port 8080 --threads 4'
```

| Option | Default |
| --- | --- |
| `--port` | `8080` |
| `--threads` | Available processors; any positive value is accepted |
| `--issuer-name` | `Nerlikh67CoolCerts` |
| `--issuer-key` | `config/SUPER_SECRET_ISSUER.key` |

The server listens on all local interfaces. Port `0` asks the OS for a free port; the actual port is printed at startup. Stop with Ctrl+C.

## Client

In another terminal:

```bash
java -cp 'build/install/TaskJ1/lib/*' ru.ashemchuk.Client --name Anna --host localhost --port 8080 --output-dir output/anna
```

Alternatively:

```bash
./gradlew runClient --args='--name Anna --output-dir output/anna'
```

The client saves `identity.key` (PKCS#8 PEM) and `identity.crt` (X.509 PEM). The public key is in the certificate. Existing files in the chosen output directory are replaced. Use the server's IP or DNS name with `--host` for another computer.

Delay reading for ten seconds after sending the complete request:

```bash
java -cp 'build/install/TaskJ1/lib/*' ru.ashemchuk.Client --name Anna --delay 10 --output-dir output/slow
```

Disconnect immediately after sending the request:

```bash
java -cp 'build/install/TaskJ1/lib/*' ru.ashemchuk.Client --name Anna --disconnect
```

`--name` is required. Other defaults: `--host localhost`, `--port 8080`, `--delay 0`, `--output-dir output`. Delay is a nonnegative whole number of seconds. `--disconnect` takes precedence over delay. Both programs support `--help`.

## Issuer key

The included P-384 issuer key deliberately has private value `4`, referencing the eMudhra incident. It is an intentionally insecure lab fixture. Client keys are independently generated RSA-8192 keys.

To recreate the fixture at a new path:

```bash
java -cp 'build/install/TaskJ1/lib/*' ru.ashemchuk.IssuerKeyTool config/another-issuer.key
```

The tool does not overwrite an existing file. The parent directory must exist. Paths are relative to the working directory unless absolute.

## Structure

- `Main`: configuration, issuer-key loading and application lifecycle
- `Server`: selector loop, connections and the completed-response queue
- `generation.CryptoGenerator`: fixed worker pool and cache of futures by name
- `generation.KeyGenerator` and `generation.CertificateGenerator`: RSA and X.509 generation
- `generation.IssuerKeyLoader`: PKCS#8 PEM loading
- `network.RequestDecoder`: incremental ASCII request parsing
- `network.ResponseEncoder` and `network.ResponseDecoder`: response protocol
- `network.ClientState`: per-connection input and output state
- `Client`: command-line client and PEM files

Only the server thread accesses the generator's map. Workers complete futures without changing the map. Each request subscribes to its future; completion queues a client-specific result and wakes the selector. No worker sends data to sockets. Successful futures remain cached for the server's lifetime; a later request retries a failed generation.

## Protocol

One request and response per TCP connection. The request is an ASCII name followed by a zero byte. An empty name is accepted; bytes above 127 are rejected.

The response is binary, with big-endian lengths:

1. Four-byte length of the private key
2. PKCS#8 DER private-key bytes
3. Four-byte length of the certificate
4. X.509 DER certificate bytes

The server closes after sending the complete response. Failed requests close without a response. The client detects incomplete responses and rejects nonpositive lengths or parts larger than 16 MiB.

## Tests

Tests cover RSA-8192, certificate fields and signature, special characters in names, response encoding, incremental requests, pending and completed cache hits, retry after generation failure, 100 simultaneous clients, a slow reader with a large response, and continued service after invalid or disconnected clients. Network tests use RSA-2048 identities to keep them fast; the real RSA size is tested separately.
