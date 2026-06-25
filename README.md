# Quiniela Mundial 2026

Web application for running **private prediction pools** for the 2026 FIFA World Cup. Supports the new 48-team format with 104 matches across group stage and knockout rounds.

---

## Features

- **Private Groups** — Create a group and share the invite code. Each member receives a unique access token.
- **Match Predictions** — Predict the exact score of every match. Editable until kickoff.
- **Full Tournament Coverage** — All 72 group-stage matches (12 groups of 4) plus 32 knockout matches (Round of 32 to Final).
- **Star Match** — Each round, select one match to score double points.
- **Champion Bet** — Pick your tournament winner before the opening match. Worth 10 bonus points.
- **Live Leaderboard** — Real-time standings with scores, exact-hit counts, and medals.
- **Hidden Predictions** — Members cannot see each other's predictions until the match starts. Prevents copying.
- **Automatic Bracket Resolution** — When the group stage ends, the knockout bracket populates automatically based on real-world results.
- **Live Score Updates** — Polls [OpenLigaDB](https://www.openligadb.de/) every 15 seconds for real-time scores during matches.
- **Server-Sent Events** — Score updates push to the browser via SSE; no polling on the client side.
- **Dark Mode** — Modern SaaS-inspired design.
- **Responsive Layout** — Collapsible drawer with standings on mobile, fixed sidebar on desktop.

## Scoring System

| Result | Points |
|--------|--------|
| Exact score match | +3 |
| Correct winner or draw | +1 |
| Star Match (one per round) | x2 |
| Correct champion prediction | +10 |
| Incorrect | +0 |

## Architecture

```
                            +-------------------+
                            |    OpenLigaDB     |
                            |   (external API)  |
                            +--------+----------+
                                     | poll every 15s
                                     v
+-------+     +---------------+ +-----------+ +--------------+
| User  +---->+  HttpServer   +>+ QuinielaApp|+>+ StateStore   +> disk
|(browser)   |  (port :8080) | | (router)  | | (serialize)  |  data/
+-------+     +---------------+ +-----+-----+ +--------------+
                                      |
                  +-------------------+-------------------+
                  v                   v                   v
           +-----------+      +------------+      +---------------+
           | Quiniela  |      |  Bracket   |      | MatchUpdate   |
           | Service   |      |  Resolver  |      | Service       |
           +-----------+      +------------+      +---------------+
                  |
          +-------+--------+
          v                v
   +----------+    +-------------+
   |  Group   |    | HtmlRenderer|
   | +Members |    | (SSR)       |
   | +Matches |    +-------------+
   | +Scores  |
   +----------+
```

### Layers

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Domain** | `quinielamundial.domain` | Pure entities: `Group`, `Member`, `Match`, `Prediction`, `ScoreBreakdown`. No external dependencies. |
| **Service** | `quinielamundial.service` | Business logic: `QuinielaService` (orchestration), `BracketResolver` (knockout pairings), `MatchUpdateService` (live score polling), `WorldCupSchedule` (full fixture list), `AuthService` (login/register). |
| **Persistence** | `quinielamundial.persistence` | `StateStore` — Java serialization to disk. |
| **Web** | `quinielamundial.web` | Embedded `HttpServer` (no framework), `HtmlRenderer` (SSR with inline CSS), `ScoreStream` (SSE hub), `FormData` (form parsing). |
| **App** | `quinielamundial.app` | Entry point (`Main`) and HTTP controller (`QuinielaApp`). |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | **Java 17** |
| HTTP Server | `com.sun.net.httpserver.HttpServer` (JDK standard library, no Spring) |
| External API | [OpenLigaDB](https://www.openligadb.de/) — real-time match results |
| JSON | **Gson 2.11.0** |
| Tests | **JUnit 5.10.2** |
| Frontend | Server-side rendered HTML + CSS Grid/Flexbox (~500 lines of CSS) |
| Typography | Inter (text) + JetBrains Mono (code) |
| Build | **Maven** |
| Persistence | Java serialization to `data/quiniela-state.txt` |
| CI/CD | GitHub Actions (build, test, deploy) |
| Deployment | Docker container on VPS with systemd + Caddy (automatic HTTPS via Let's Encrypt) |

## Quick Start

```bash
# Requirements: Java 17+
java -version

# Build
mvn clean package

# Run
java -jar target/quinielamundial-1.0.0-SNAPSHOT.jar

# Open in browser
open http://localhost:8080
```

Optional environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | HTTP server port |
| `DATA_DIR` | `data` | Persistence directory |

## Deployment

The project includes a CI/CD pipeline via GitHub Actions. On every push to `main`:

1. Build with Maven
2. Run all tests
3. Build Docker image
4. Push to GitHub Container Registry
5. Deploy to the VPS via SSH

### VPS Requirements

- Ubuntu 24.04+
- Java 17 runtime
- Docker (optional, for containerized deployment)
- Caddy for reverse proxy and automatic HTTPS

### Manual Deployment

```bash
# Build
mvn package

# Upload to server
scp target/quinielamundial-1.0.0-SNAPSHOT.jar root@<IP>:/opt/quiniela/

# Start service on server
ssh root@<IP>
systemctl restart quiniela
```

## Project Structure

```
src/
+-- main/java/quinielamundial/
|   +-- app/                # Entry point and HTTP controller
|   |   +-- Main.java
|   |   +-- QuinielaApp.java
|   +-- domain/             # Domain entities
|   |   +-- Group.java
|   |   +-- Match.java
|   |   +-- Member.java
|   |   +-- Outcome.java
|   |   +-- Prediction.java
|   |   +-- RankingEntry.java
|   |   +-- ScoreBreakdown.java
|   +-- persistence/        # Disk persistence
|   |   +-- StateStore.java
|   +-- service/            # Business logic
|   |   +-- AuthService.java
|   |   +-- BracketResolver.java
|   |   +-- MatchUpdateService.java
|   |   +-- QuinielaService.java
|   |   +-- WorldCupSchedule.java
|   +-- web/                # Server-side HTML rendering
|       +-- FormData.java
|       +-- HtmlRenderer.java
|       +-- ScoreStream.java
+-- test/java/quinielamundial/
    +-- QuinielaCSSFrameworkTest.java
    +-- QuinielaDataIntegrityTest.java
    +-- QuinielaDomainTest.java
    +-- QuinielaPageStructureTest.java
    +-- QuinielaRenderingTest.java
```

## Testing

```bash
mvn test
```

The test suite includes:
- **Domain tests**: scoring logic, predictions, leaderboard resolution
- **Data integrity tests**: fixture consistency across all 104 matches
- **Page structure tests**: layout and responsive behavior
- **CSS framework tests**: visual coverage and rendering
- **Rendering tests**: HTML output correctness

## License

MIT
