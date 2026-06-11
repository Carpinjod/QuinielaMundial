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

### 8. Estadísticas avanzadas

Por usuario:
- % de aciertos por jornada
- Rachas
- Mejor/peor pronóstico
- Comparativa con la media del grupo

### 9. Modo oscuro / claro

Toggle para cambiar entre tema oscuro y claro (algunos prefieren claro de día).

### 10. Tests de interfaz

Tests automatizados que verifiquen que el HTML generado es correcto y que el CSS cubre todos los componentes.

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
| Tests | 9 tests unitarios (JUnit 5) |
| Servidor | VPS Ubuntu 24.04, nginx 1.24 + Let's Encrypt |
| Tiempo de actividad | 100% desde el despliegue |
