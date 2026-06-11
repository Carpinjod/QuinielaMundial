# ⚽ Quiniela Mundial 2026

Aplicación web para crear **quinielas privadas** del Mundial de Fútbol 2026. Grupos con amigos, pronósticos, puntuaciones en tiempo real y soporte completo para el nuevo formato de **48 selecciones** y **104 partidos**.

---

## ✨ Funcionalidades

- **Grupos privados** — Crea un grupo, comparte el código de invitación. Cada miembro tiene un token único.
- **Pronósticos** — Acierta el resultado exacto de cada partido. Modificable hasta que el partido empieza.
- **104 partidos** — Los 72 de la fase de grupos (12 grupos de 4) + los 32 de eliminatorias (R32 → Final).
- **Partido Estrella ⭐** — Cada jornada puedes marcar un partido para que puntúe el doble.
- **Apuesta al Campeón 🏆** — Elige tu candidato antes de que empiece el torneo. 10 puntos extra si aciertas.
- **Clasificación en tiempo real** — Leaderboard con puntuaciones, aciertos exactos y medallas.
- **Pronósticos ocultos** — Nadie ve lo que otros pronosticaron hasta que el partido empieza. Sin copias.
- **Eliminatorias automáticas** — Cuando terminan los 72 partidos de grupos, el cuadro se rellena solo con los cruces reales.
- **Actualización automática de resultados** — Cada 5 minutos consulta [OpenLigaDB](https://www.openligadb.de/) para traer resultados reales.
- **Modo oscuro** — Diseño tipo SaaS moderno (Inspiración: Linear, Vercel, Railway).
- **Responsive** — Drawer lateral con clasificación en móvil, sidebar fija en escritorio.

## 📊 Sistema de puntuación

| Resultado | Puntos |
|-----------|--------|
| Resultado exacto (marcador acertado) | **+3** 🎯 |
| Ganador o empate acertado | **+1** ✅ |
| Partido Estrella (por jornada) | **×2** ⭐ |
| Campeón del mundo acertado | **+10** 🏆 |
| Fallo | **+0** ❌ |

## 🏗️ Arquitectura

```
                           ┌─────────────┐
                           │  OpenLigaDB  │
                           │   (API ext)  │
                           └──────┬──────┘
                                  │ poll cada 5min
                                  ▼
┌─────┐     ┌──────────────┐ ┌────────────┐ ┌───────────┐
│ User │────▶│  HttpServer  │─▶│ QuinielaApp│─▶│ StateStore│──▶ disco
│(brwr)│     │  (puerto :8080)│ │  (router)  │ │ (serial)  │   data/
└─────┘     └──────────────┘ └─────┬──────┘ └───────────┘
                                   │
                  ┌────────────────┼────────────────┐
                  ▼                ▼                ▼
           ┌──────────┐    ┌────────────┐   ┌──────────────┐
           │ Quiniela │    │  Bracket   │   │ MatchUpdate  │
           │ Service  │    │  Resolver  │   │ Service      │
           └──────────┘    └────────────┘   └──────────────┘
                  │
          ┌───────┴───────┐
          ▼               ▼
   ┌─────────┐    ┌────────────┐
   │  Group   │    │  HtmlRender│
   │ +Members │    │  (SSR)     │
   │ +Matches │    └────────────┘
   │ +Scores  │
   └─────────┘
```

### Capas

| Capa | Paquete | Responsabilidad |
|------|---------|----------------|
| **Domain** | `quinielamundial.domain` | Entidades puras: `Group`, `Member`, `Match`, `Prediction`, `ScoreBreakdown`. Sin dependencias externas. |
| **Service** | `quinielamundial.service` | Lógica de negocio: `QuinielaService` (orquestación), `BracketResolver` (cruces KO), `MatchUpdateService` (auto-actualización), `WorldCupSchedule` (calendario completo), `AuthService` (login/register). |
| **Persistence** | `quinielamundial.persistence` | `StateStore` — serialización Java a disco. |
| **Web** | `quinielamundial.web` | `HttpServer` embebido (sin frameworks), `HtmlRenderer` (SSR con HTML + CSS inline ~500 líneas), `FormData` (parseo de formularios). |
| **App** | `quinielamundial.app` | Punto de entrada (`Main`) y controlador HTTP (`QuinielaApp`). |

## 🧱 Tech Stack

| Componente | Tecnología |
|------------|-----------|
| Lenguaje | **Java 17** |
| Servidor HTTP | `com.sun.net.httpserver.HttpServer` (JDK estándar, sin Spring) |
| API externa | [OpenLigaDB](https://www.openligadb.de/) — resultados en tiempo real |
| JSON | **Gson 2.11.0** |
| Tests | **JUnit 5.10.2** |
| Frontend | HTML generado server-side + CSS Grid/Flexbox + ~500 líneas de CSS |
| Tipografía | Inter (texto) + JetBrains Mono (código) |
| Build | **Maven** |
| Persistencia | Serialización Java `data/quiniela-state.txt` |
| Despliegue | VPS Ubuntu + systemd + Caddy (HTTPS automático) |

## 🚀 Cómo ejecutar localmente

```bash
# Requisito: Java 17+
java -version

# Compilar
mvn clean package

# Ejecutar
java -jar target/quinielamundial-1.0.0-SNAPSHOT.jar

# Abrir en el navegador
open http://localhost:8080
```

Variables de entorno opcionales:

| Variable | Valor por defecto | Descripción |
|----------|------------------|-------------|
| `PORT` | `8080` | Puerto del servidor HTTP |
| `DATA_DIR` | `data` | Directorio para persistencia |

## 🐳 Despliegue en VPS

Incluye `deploy/setup.sh` para desplegar en un VPS Ubuntu 24.04 con:

- **Java 17** runtime
- **Caddy** como reverse proxy con HTTPS automático (Let's Encrypt)
- **systemd** service con autoreinicio

Pasos rápidos:

```bash
# 1. Compilar
mvn package

# 2. Subir al servidor
scp target/quinielamundial-1.0.0-SNAPSHOT.jar root@<IP>:/opt/quiniela/

# 3. Ejecutar el setup en el servidor
ssh root@<IP>
bash /opt/quiniela/setup.sh

# 4. Iniciar servicios
systemctl enable --now quiniela
systemctl restart caddy
```

## 📁 Estructura del proyecto

```
src/
├── main/java/quinielamundial/
│   ├── app/            # Punto de entrada y controlador HTTP
│   │   ├── Main.java
│   │   └── QuinielaApp.java
│   ├── domain/         # Entidades del dominio
│   │   ├── Group.java
│   │   ├── Match.java
│   │   ├── Member.java
│   │   ├── Outcome.java
│   │   ├── Prediction.java
│   │   ├── RankingEntry.java
│   │   └── ScoreBreakdown.java
│   ├── persistence/    # Persistencia a disco
│   │   └── StateStore.java
│   ├── service/        # Lógica de negocio
│   │   ├── AuthService.java
│   │   ├── BracketResolver.java
│   │   ├── MatchUpdateService.java
│   │   ├── QuinielaService.java
│   │   └── WorldCupSchedule.java
│   └── web/            # Renderizado HTML server-side
│       ├── FormData.java
│       └── HtmlRenderer.java
└── test/java/quinielamundial/
    └── QuinielaDomainTest.java
```

## 🧪 Tests

```bash
mvn test
```

9 tests unitarios sobre el core del dominio (puntuaciones, pronósticos, leaderboard).

## 📄 Licencia

MIT
