# QUINIELA MUNDIAL 2026 — INSTRUCCIONES

## Acceso

La web está en:

👉 **https://167.233.81.70.nip.io** — HTTPS con Let's Encrypt
👉 **http://167.233.81.70** — también funciona (sin HTTPS)

---

## 1. Crear el grupo

El que hace la quiniela:

1. Entra a la web
2. Pone un **nombre para el grupo**
3. Pone su **nombre**
4. Dale a **"Crear grupo"**

Te sale el código de invitación (ej: `TLT26WX8`). Ese código se lo pasas a los demás. También te guarda una cookie con tu token para que no tengas que volver a identificarte.

---

## 2. Unirse

Los demás:

1. Entran a la misma web
2. Ponen el **código del grupo**
3. Ponen su **nombre**
4. Dale a **"Entrar"**

Cada uno recibe un token único que se guarda en el navegador. Si alguien se une con el mismo nombre que otro ya existente, la web avisa y hay que elegir otro.

---

## 3. Pronosticar

Una vez dentro del grupo:

- Ves los partidos ordenados por **jornada** (pestañas arriba)
- Metes el resultado que crees (goles local – goles visitante)
- Puedes cambiar el pronóstico **hasta que empiece el partido**
- Cuando el partido empieza, se bloquea

Los pronósticos de los demás están ocultos hasta que empieza el partido — **nadie copia**.

---

## 4. Partido estrella ⭐

Cada jornada puedes marcar **un partido** como "estrella". Ese partido vale el **doble de puntos**.

Se marca con el botón "⭐ Marcar estrella" debajo del partido. Puedes cambiarlo antes de que empiece el partido.

---

## 5. Apuesta al campeón 🏆

Antes de que empiece el torneo, cada uno elige qué selección cree que va a ganar el Mundial. Si aciertas, son **10 puntos extra**.

Se hace desde el panel **📊** (esquina inferior derecha en móvil, o sidebar en ordenador).

---

## 6. Eliminatorias 🏆

Cuando terminen los **72 partidos** de la fase de grupos, se desbloquean las eliminatorias. Aparece una pestaña nueva "🏆 Eliminatorias" con el cuadro completo:

- Dieciseisavos de final → Octavos → Cuartos → Semifinales → Tercer puesto → Final

Los cruces se rellenan automáticamente según los resultados de la fase de grupos.

### 📋 Método de clasificación

En cada partido de eliminatorias puedes pronosticar **cómo se va a decidir** el pase a la siguiente ronda:

| Opción | Significa |
|--------|-----------|
| **Tiempo regular (90')** | El partido se decide en los 90 minutos reglamentarios |
| **Prórroga (120')** | El partido se va a la prórroga y ahí se decide |
| **Penaltis** | El partido llega hasta los penaltis |

**¿Cómo se determina el método real?**
- Si el partido termina con goles diferentes → se considera **Tiempo regular** (salvo que el creador del grupo lo corrija)
- Si el partido termina en empate → se considera **Penaltis** (los KO no pueden quedar en empate)
- El creador del grupo puede corregir el método manualmente si fue a la prórroga

---

## Sistema de puntos

| Resultado | Puntos |
|-----------|--------|
| Resultado exacto (ej: metiste 3-2 y fue 3-2) | **3 puntos** 🎯 |
| Acertar solo quién gana o que es empate | **1 punto** ✅ |
| **Acertar el método de clasificación** (solo eliminatorias) | **+1 punto extra** 💪 |
| Partido estrella | Todo **×2** ⭐ |
| Acertar el campeón del mundo | **+10 puntos** 🏆 |
| Fallar | 0 puntos |

> Ejemplo: si aciertas el resultado exacto Y el método, son **4 puntos** (3+1). Si aciertas solo el ganador Y el método, son **2 puntos** (1+1). Si fallas el resultado pero aciertas el método, te llevas **1 punto** igual.

---

## Clasificación

En **móvil**: toca el botón **📊** abajo a la derecha → se abre un panel lateral con la clasificación y tu apuesta al campeón. Para cerrar, toca fuera o la ✕.

En **ordenador**: la clasificación y el campeón están en la **columna derecha** siempre visible.

---

## Para el creador del grupo

Solo el que creó el grupo puede:

- **Cargar resultados** de los partidos (aunque la mayoría se actualizan solos desde OpenLigaDB)
- **Actualizar el campeón del torneo** cuando termine la final

Si el creador no carga un resultado, el sistema lo actualiza automáticamente a los pocos minutos de que termine el partido (vía OpenLigaDB).

---

¡A darle caña! 🍻
