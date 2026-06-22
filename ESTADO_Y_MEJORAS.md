# Quiniela Mundial 2026 — Estado actual y mejoras

## ✅ Estado actual

### Core funcional

- [x] **Crear grupos** con código de invitación
- [x] **Unirse a grupos** mediante código (genera token único de 16 caracteres)
- [x] **Autenticación por token** (cookie + formulario, sin usuario/contraseña)
- [x] **72 partidos de fase de grupos** con todos los equipos del Mundial 2026
- [x] **Pronosticar resultados** (goles local – goles visitante)
- [x] **Modificar pronóstico** hasta que empiece el partido
- [x] **Partido estrella** (uno por jornada, puntuación x2)
- [x] **Apuesta al campeón** (10 puntos extra)
- [x] **Carga de resultados** por el creador del grupo
- [x] **Auto-actualización de resultados** desde OpenLigaDB cada 5 minutos (Gson, mapeo Alemán→Inglés)
- [x] **Sistema de puntuación** (3 pts exacto, 1 pt solo resultado, 0 pts fallo)
- [x] **Clasificación en tiempo real** con top 3 destacado (medallas oro/plata/bronce)
- [x] **32 partidos de eliminatorias** (Dieciseisavos → Final)
- [x] **Revelación progresiva de los cruces** cuando terminan los grupos
- [x] **Resolución automática del bracket** según resultados
- [x] **Persistencia en disco** (`data/quiniela-state.txt`, serialización Java)
- [x] **Compatibilidad hacia atrás** con datos guardados antes de añadir eliminatorias
- [x] **Drawer lateral responsive en móvil** (FAB flotante → panel deslizable con clasificación + campeón, sin ocupar espacio en el contenido principal)

### Diseño

- [x] **Dark mode** profesional (fondo #0B1020, superficies #131A2E)
- [x] **Tipografía Inter + JetBrains Mono** (la misma que usa Vercel, Linear, etc.)
- [x] **Estética SaaS moderna** tipo Linear / Vercel / Railway
- [x] **Tarjetas redondeadas** (12–16px) con sombras sutiles y bordes
- [x] **Ranking con medallas** oro/plata/bronce y filas destacadas con glow
- [x] **Pestañas de jornadas** con scroll horizontal + pestaña de Eliminatorias
- [x] **Pronósticos ocultos** hasta que empieza el partido (sin copias)
- [x] **Drawer lateral con .5s backdrop blur** para móvil (clasificación y campeón a 1 toque)
- [x] **FAB (Floating Action Button)** con animación y sombras
- [x] **Overlay con backdrop-filter** al abrir el drawer
- [x] **Cierre del drawer** tocando fuera, ✕ o tecla Escape

### Infraestructura

- [x] **Despliegue en VPS** (Ubuntu 24.04, Java 17, Maven)
- [x] **Service systemd** con autoreinicio (`quiniela.service`)
- [x] **Nginx como reverse proxy** (puerto 80 → app :8080)
- [x] **HTTPS con Let's Encrypt** (certificado para dominio nip.io)
- [x] **Auto-renovación del certificado SSL** (certbot timer)
- [x] **Firewall UFW** (solo puertos 22, 80, 443 y 8080)
- [x] **Logs persistentes** (`/var/log/quiniela.{log,err}`)

---

## 🔮 Posibles mejoras

### 1. Notificaciones push

Que avise cuando:
- Un grupo se actualiza (alguien pronosticó, cambiaron resultados)
- Empieza un partido de tu grupo
- Se desbloquean las eliminatorias

### 2. Histórico de resultados

Pestaña para ver jornadas anteriores ya cerradas. Poder consultar pronósticos pasados y ver cómo fue evolucionando la clasificación.

### 3. Compartir grupo con enlace

En vez de tener que copiar y pegar el código, poder compartir un enlace tipo:
```
https://quiniela.tudominio.com/join/TLT26WX8
```
Y que al abrirlo te meta directo al grupo.

### 4. Sistema de empates en la clasificación

Ahora mismo si dos quedan empatados a puntos, se desempata por número de aciertos exactos. Se podría añadir un criterio más claro o mostrarlo en la interfaz.

### 5. Docker / docker-compose

Para facilitar el despliegue en vez de tener que hacer scp + systemctl manualmente.

### 6. Dominio personalizado

Actualmente funciona con IP desnuda o nip.io. Comprar un dominio (ej: `quiniela.tus-amigos.com`) y asociarlo con el certificado SSL para HTTPS sin advertencias.

### 7. Vista de grupos públicos

Página tipo "Explorar" donde se puedan ver grupos públicos, puntuaciones, y ranking global.

### 8. Estadísticas avanzadas ✅ IMPLEMENTADO

Por usuario en la página de Ajustes:
- **% de aciertos** (partidos acertados / partidos pronosticados)
- **Mejor racha** de partidos consecutivos acertados
- **Comparativa con la media del grupo** (diferencia en verde/rojo)
- **Puesto** numérico en el grupo
- Calculado sobre todos los partidos finalizados (fase de grupos + eliminatorias)
- Nueva columna `scoreMatch()` en HtmlRenderer con lógica de puntuación (incluye multiplicador de partido estrella)

### 9. Modo oscuro / claro ✅ YA IMPLEMENTADO

Toggle en el header para cambiar entre tema oscuro y claro. Persiste elección en localStorage. `toggleTheme()` + `[data-theme=light]` con paleta completa.

### 10. Tests de interfaz ✅ IMPLEMENTADO

Tests automatizados (84 nuevos tests) que verifican:
- Todos los design tokens de CSS están definidos (--bg, --surface, --pri, etc.)
- Todas las clases CSS de componentes existen (.card, .match-row, .btn-star, etc.)
- Todas las animaciones y keyframes están definidos
- Los media queries responsive están presentes
- La estructura HTML de cada página es correcta (doctype, header, main, footer)
- Cada página renderiza los componentes esperados (home: hero + cards, group: accordion + leaderboard)
- Los estados de las match cards (form mode, finished, started) generan el HTML correcto
- Las funciones JS requeridas existen (showToast, toggleDrawer, liveScores, etc.)
- Los formularios AJAX tienen la estructura correcta
- Las reglas de visibilidad de pronósticos se cumplen

---

## 🐛 Bugs conocidos

- Ninguno conocido en este momento.

---

## 📊 Stats técnicas

| Métrica | Valor |
|---------|-------|
| Lenguaje | Java 17 |
| Dependencias externas | Gson 2.11.0 (OpenLigaDB) |
| Frontend | HTML generado server-side + CSS inline (~480 líneas) |
| Persistencia | Serialización Java en disco (`data/quiniela-state.txt`) |
| Líneas de código total | ~2500 |
| Tests | 121 tests (JUnit 5) — 3 clases de tests pre-existentes + 2 nuevas de cobertura CSS y estructura de páginas |
| Servidor | VPS Ubuntu 24.04, nginx 1.24 + Let's Encrypt |
| Tiempo de actividad | 100% desde el despliegue |
