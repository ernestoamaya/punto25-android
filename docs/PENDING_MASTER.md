# Punto25 — Pendientes Maestros

Documento canónico de Control Maestro para pendientes funcionales, técnicos, UX/UI y de hitos futuros de Punto25.

## Reglas de uso

- Este archivo es la fuente externa estable para el inventario `P25-PEND-*`.
- Los IDs son estables: no se reutilizan para otro tema.
- Un pendiente `ARCHIVADO` no debe reaparecer como activo salvo decisión expresa posterior.
- Un pendiente `ABSORBIDO` conserva trazabilidad histórica, pero su alcance vive en otro ID.
- La autorización funcional para implementar una tanda sigue requiriendo literalmente: `APROBADO, IMPLEMENTAR TANDA`.
- Este inventario no sustituye `REGRESSION_MATRIX.md`: los `P25-PEND-*` gestionan trabajo pendiente; los `REG-*` gestionan invariantes/tests ejecutables.
- Prioridades del proyecto: integridad de datos, aislamiento, identidad/autorización, seguridad, consistencia financiera, regresión, funcionalidad y UI/UX.
- Ante dudas de identidad/autorización/pertenencia: FAIL CLOSED.
- Los datos Alpha siguen siendo descartables hasta declaración expresa `DATA COMPATIBILITY FREEZE`.
- Costo autorizado actual: USD 0.

## Estado resumido

| ID | Nombre | Estado | Clase / tipo |
|---|---|---|---|
| P25-PEND-01 | ORDER-LOCATION-PRESENTATION | EN CURSO | Clase C / UX-presentación |
| P25-PEND-02 | ADMIN-ORDER-ZONE-OVERRIDE | PENDIENTE | Clase B alta / financiera |
| P25-PEND-03 | UNSAVED-CHANGES-GUARD | PENDIENTE | UX/integridad de formularios |
| P25-PEND-04 | SUPPORT-ROLE-SEPARATION | PENDIENTE | UX/funcional |
| P25-PEND-05 | Validación homogénea de teléfonos | PENDIENTE DE AUDITORÍA | Consistencia/validación |
| P25-PEND-06 | Calendarios homogéneos para fechas | PENDIENTE DE AUDITORÍA | UX/validación |
| P25-PEND-07 | Alta de turnos por múltiples fechas | ARCHIVADO | Funcionalidad sustituida |
| P25-PEND-08 | DOCS-CURRENT-STATE | PENDIENTE | Documentación/gobernanza |
| P25-PEND-09 | ZONE-GEOMETRY-CONFIG | DISEÑADO PARA DESPUÉS | Funcional/geográfico |
| P25-PEND-10 | ZONE-GEO-AUTO-RESOLUTION | DISEÑADO PARA DESPUÉS | Funcional/financiero-geográfico |
| P25-PEND-11 | Reconciliación visual integral UX/UI | PENDIENTE UX/UI | UX/UI |
| P25-PEND-12 | TODO-3B | ARCHIVADO / ABSORBIDO | Histórico |
| P25-PEND-13 | Deuda técnica / warnings / deprecaciones | DIFERIDO | Consolidación técnica |
| P25-PEND-14 | DATA COMPATIBILITY FREEZE | HITO FUTURO | Gobernanza de datos |
| P25-PEND-15 | Prepublicación Play Store / privacidad / PI | HITO FUTURO | Publicación/compliance |

---

## P25-PEND-01 — ORDER-LOCATION-PRESENTATION

**Estado:** EN CURSO.

**Objetivo:**
- eliminar de Cliente, Rider y Admin la exposición visual de `Pin retiro`, `Pin entrega`, URLs legacy de Google Maps y coordenadas técnicas;
- usar exclusivamente `GeoPoint` estructurados para mapas;
- agregar visor interno read-only MapLibre/OpenFreeMap;
- preservar la navegación externa Rider ya validada;
- no parsear `order.detail`, URLs ni direcciones para reconstruir coordenadas.

**Seguridad/integridad:**
- Cliente sólo ve pedidos propios;
- Rider sólo ve puntos de pedidos asignados a su sesión autenticada;
- Admin mantiene su autorización aislada;
- visor estrictamente read-only.

**Estado técnico conocido al crear este documento:** rama `order-location-presentation` activa; no mergear hasta revisión de Control Maestro.

---

## P25-PEND-02 — ADMIN-ORDER-ZONE-OVERRIDE

**Estado:** PENDIENTE — Clase B alta.

**Objetivo:** permitir que Administración corrija la zona aplicada a un pedido cuando la realidad operativa/geográfica no coincida con la zona declarada.

**Requisitos conceptuales:**
- distinguir y preservar `zona declarada` vs `zona aplicada`;
- admitir selección de zona permanente existente;
- reutilizar el editor global para crear una nueva zona permanente, sin duplicar fuente de verdad;
- admitir una `zona circunstancial` por pedido, no incorporada al catálogo global;
- motivo obligatorio de modificación;
- auditoría explícita;
- recálculo determinista de tarifa base/adicionales;
- analizar interacción con pagos, importes esperados, balances y conciliación antes de implementar;
- pedidos completados/cancelados permanecen inmutables; correcciones posteriores deben ser ajustes/conciliaciones.

**Exclusión:** no incluye polígonos ni autodetección geográfica.

---

## P25-PEND-03 — UNSAVED-CHANGES-GUARD

**Estado:** PENDIENTE.

**Objetivo:** impedir pérdida accidental de datos modificados en cualquier formulario/modal editable.

**Comportamiento esperado:**
- si no hay cambios, salir normalmente;
- si hay estado `dirty`, interceptar botón `VOLVER`, Back Android/gesto y dismiss equivalente;
- mostrar confirmación clara, por ejemplo: `Hay cambios sin guardar. Si salís, se descartarán.`;
- acciones recomendadas: `SEGUIR EDITANDO` y `DESCARTAR CAMBIOS`;
- no cerrar por toque exterior cuando implique pérdida de cambios;
- guardado fallido no debe cerrar la pantalla.

**Antecedentes:** existe protección parcial en editores concretos (por ejemplo TODO-2, Turnos y edición de pedido), pero no una política transversal para toda la app.

**Áreas mínimas a auditar:** Compras, Encargos, Trámites, Envíos, perfiles, altas/ediciones Admin y Rider, y cualquier modal editable restante.

---

## P25-PEND-04 — SUPPORT-ROLE-SEPARATION

**Estado:** PENDIENTE.

**Problema actual:** Cliente y Rider comparten el mismo componente/listado de motivos de soporte; actualmente sólo cambia el título/contexto.

**Objetivo:** mantener infraestructura reutilizable, pero definir motivos apropiados por rol.

**Dirección funcional preliminar:**
- Cliente: pedido, pago/cobro, cuenta/perfil, problemas con la app, otro motivo;
- Rider: pedido/entrega, turnos, balance/pagos, documentación, problemas con la app, otro motivo.

**UX/UI:** textos, jerarquía y nomenclatura definitivos deben pasar por revisión UX/UI antes de implementación.

---

## P25-PEND-05 — Validación homogénea de teléfonos

**Estado:** PENDIENTE DE AUDITORÍA.

**Origen:** antiguo TODO-3B punto 1; TODO-3B fue archivado y este alcance queda absorbido aquí.

**Objetivo:** todos los campos donde se agregue o edite un teléfono deben utilizar la misma validación canónica.

**Antes de implementar:**
- localizar y reconstruir la validación telefónica ya existente;
- no inventar una regla nueva si ya existe una canónica;
- inventariar transversalmente altas, ediciones, perfiles, formularios, Cliente, Rider y datos administrativos;
- determinar normalización/rechazo real actualmente esperado.

**Testing esperado:** REG-* de consistencia, aceptación/rechazo y normalización según la regla canónica.

---

## P25-PEND-06 — Calendarios homogéneos para fechas

**Estado:** PENDIENTE DE AUDITORÍA.

**Origen:** antiguo TODO-3B punto 2; TODO-3B fue archivado y este alcance queda absorbido aquí.

**Objetivo:** eliminar escritura libre de fechas `dd/mm/yyyy` donde corresponda y unificar selección mediante calendario.

**Requisitos:**
- selección de día;
- desplazamiento entre meses;
- cambio directo/fácil de año;
- no obligar a recorrer mes por mes para años alejados;
- revisar también date pickers/calendarios existentes para consistencia;
- evitar fechas inválidas o mal formateadas.

**Antes de implementar:** inventariar todos los campos de fecha y calendarios actuales para no duplicar lógica.

---

## P25-PEND-07 — Alta de turnos por múltiples fechas

**Estado:** ARCHIVADO — IMPLEMENTADA OTRA FUNCIONALIDAD SUPLETORIA.

**Decisión:** no implementar la funcionalidad original de selección múltiple de fechas concretas desde calendario para alta de turnos.

**Regla:** no debe reaparecer como pendiente salvo nueva decisión expresa.

---

## P25-PEND-08 — DOCS-CURRENT-STATE

**Estado:** PENDIENTE.

**Objetivo:** reconciliar documentación del repositorio con el estado técnico real actual.

**Alcance previsto:**
- README con stack/flujo Alpha actual;
- remover afirmaciones actuales obsoletas sobre Google Maps, `MAPS_API_KEY` y `ALPHA_ADMIN_PIN`, preservando historia cuando corresponda;
- actualizar SECURITY_AUDIT con estado y racional USD 0;
- actualizar PUBLICATION_CHECKLIST;
- reflejar MapLibre/OpenFreeMap, Admin auth aislada, packaging reproducible y WhatsApp pausado/intencionalmente opcional.

**Secuencia:** después de estabilizar fixes funcionales prioritarios.

---

## P25-PEND-09 — ZONE-GEOMETRY-CONFIG

**Estado:** DISEÑADO PARA DESPUÉS.

**Objetivo:** permitir definir cobertura geográfica de zonas mediante polígonos sobre MapLibre/OpenFreeMap.

**Diseño conceptual aprobado:**
- uno o varios polígonos por zona;
- editor Admin específico dentro de zonas, claramente identificado;
- dibujar/editar/eliminar polígonos;
- validar geometrías inválidas;
- evitar solapamientos entre zonas activas salvo una futura regla explícita de prioridad;
- switch general `Asignación automática de zonas por ubicación`, inicialmente OFF;
- no dejar activar la modalidad si la configuración presenta problemas graves;
- no modificar precio/nombre/categoría desde el editor geométrico: la geometría sólo define dónde aplica una zona.

**Regla histórica:** cambios futuros de polígonos no deben recalcular pedidos históricos.

---

## P25-PEND-10 — ZONE-GEO-AUTO-RESOLUTION

**Estado:** DISEÑADO PARA DESPUÉS.

**Dependencia:** P25-PEND-09 completo y validado.

**Objetivo:** resolver automáticamente `GeoPoint → zona` y conectar esa resolución con la tarifación existente.

**Reglas conceptuales:**
- origen/destino obligatorios mediante pin manual y/o GPS cuando la modalidad esté activa;
- para DELIVERY, resolver retiro y entrega;
- para SHOPPING, resolver comercio, entrega y retiro previo cuando exista;
- la dirección escrita es descriptiva; la zona geográfica se determina por GeoPoint;
- los polígonos determinan zona, no precio;
- el motor tarifario existente sigue siendo autoridad;
- entre los puntos que determinan tarifa base prevalece la zona de mayor costo;
- retiro previo conserva el adicional/regla de retiro vigente;
- si un punto no cae en ninguna zona: no inventar zona; requerir revisión/tarifa circunstancial;
- Admin override debe conservar `zona detectada`, `zona aplicada`, motivo y auditoría.

**Casos críticos:** límites, lat/lon, geometrías inválidas, zonas inactivas, fuera de cobertura, pedidos de tres puntos y solapamientos.

---

## P25-PEND-11 — Reconciliación visual integral UX/UI

**Estado:** PENDIENTE UX/UI.

**Objetivo:** auditoría integral y priorización de experiencia de Punto25 sin mezclar automáticamente todos los hallazgos en una sola tanda funcional.

**Áreas:**
- jerarquía visual;
- navegación;
- formularios;
- mapas;
- botones/acciones;
- estados vacíos/loading/error;
- mensajes;
- densidad;
- consistencia Cliente/Rider/Admin;
- accesibilidad;
- adaptación de pantallas.

**Gobernanza:** UX/UI propone y clasifica; Control Maestro convierte sólo los ítems aprobados en tandas de Desarrollo.

---

## P25-PEND-12 — TODO-3B

**Estado:** ARCHIVADO — ALCANCE ABSORBIDO POR P25-PEND-05 Y P25-PEND-06.

**Decisión consolidada:**
- antiguo punto 1, validación global de teléfonos → P25-PEND-05;
- antiguo punto 2, calendarios/fechas sin escritura libre → P25-PEND-06;
- antiguo punto 3, alta de turnos por múltiples fechas → P25-PEND-07, archivado por funcionalidad supletoria;
- criterios transversales de inspección, seguridad, autorización y tests se incorporan a P25-PEND-05/P25-PEND-06.

**Regla:** `TODO-3B` ya no debe mencionarse como futura tanda independiente.

---

## P25-PEND-13 — Deuda técnica / warnings / deprecaciones

**Estado:** DIFERIDO — consolidación futura.

**Conocido actualmente:**
- Kotlin/Java type mismatch en LocalStore;
- deprecaciones/legacy de Turnos;
- `menuAnchor` deprecated;
- `!!` innecesarios;
- warnings ShiftStoreV2;
- librerías nativas MapLibre no stripped;
- `sdkmanager` deprecated;
- avisos de versión Gradle;
- advertencias de runtime Node en Actions.

**Regla:** no corregir automáticamente dentro de tandas funcionales salvo que sea necesario para la tanda o se convierta en riesgo real. Proponer consolidación cuando la deuda lo justifique.

---

## P25-PEND-14 — DATA COMPATIBILITY FREEZE

**Estado:** HITO FUTURO.

**Objetivo:** declaración explícita antes de la primera versión con datos reales/no descartables.

**Antes del freeze:**
- datos Alpha descartables;
- resets permitidos;
- cambios incompatibles de esquema sin migración de datos de prueba cuando simplifiquen/aseguren la solución.

**Desde el freeze:**
- datos reales no descartables;
- todo cambio incompatible exige migración explícita;
- agregar rollback/recuperación cuando el riesgo lo justifique.

**Regla:** no declarar implícitamente.

---

## P25-PEND-15 — Prepublicación Play Store / privacidad / PI / disclosures

**Estado:** HITO FUTURO.

**Objetivo:** revisión final previa a publicación/producción.

**Incluye:**
- privacidad;
- documentos y disclosures;
- permisos Android;
- propiedad intelectual;
- branding;
- licencias/atribuciones;
- requisitos vigentes de Play Store;
- requisitos cambiantes de Google/Firebase/Cloudflare/Meta cuando correspondan;
- configuración accidentalmente privada/pública;
- checklist de publicación.

**Regla:** verificar fuentes oficiales actuales cuando llegue el hito.

---

## Secuencia recomendada vigente

Mientras P25-PEND-01 siga en curso, no mezclar nuevos fixes en su implementación.

Orden recomendado después de cerrarlo:

1. revisión breve UX/UI de los hallazgos actuales;
2. P25-PEND-03 — UNSAVED-CHANGES-GUARD;
3. P25-PEND-04 — SUPPORT-ROLE-SEPARATION;
4. auditoría P25-PEND-05 y P25-PEND-06 contra código real;
5. P25-PEND-02 — ADMIN-ORDER-ZONE-OVERRIDE;
6. P25-PEND-08 — DOCS-CURRENT-STATE;
7. P25-PEND-09 — ZONE-GEOMETRY-CONFIG;
8. P25-PEND-10 — ZONE-GEO-AUTO-RESOLUTION;
9. consolidaciones/hitos P25-PEND-13/14/15 cuando corresponda.

La secuencia puede cambiar por decisión funcional posterior; este archivo debe actualizarse en ese momento.
