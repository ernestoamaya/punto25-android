# Punto25 — Matriz Maestra de Regresión

Esta matriz vincula invariantes críticos con tests ejecutables. Los tests son la fuente de verdad de PASS/FAIL; la tabla mantiene trazabilidad estable.

| ID | Invariante | Test ejecutable |
|---|---|---|
| REG-IDENTITY-001 | Un Cliente nunca obtiene pedidos de otro Cliente por coincidencias débiles. | `CustomerOrderOwnershipTest.differentCustomerNeverMatchesEvenWithSameVisibleName` |
| REG-HISTORY-001 | Los alias Alpha `CLI-/DEV-` recuperan historial sólo cuando corresponden al mismo teléfono verificado. | `CustomerOrderOwnershipTest.legacyCliIdMatchesVerifiedCurrentCustomer`, `previousDevIdMatchesVerifiedGoogleCustomerWithSameNumber` |
| REG-PAY-001 | Un pago confirmado queda fuera de los estados pendientes de transferencia. | `PaymentTransferPolicyTest.confirmedIsCompletedNotPending` |
| REG-PAY-AUTH-001 | Un Repartidor ajeno no puede acceder, confirmar ni reportar una transferencia. | `PaymentTransferPolicyTest.wrongRiderCannotAccessTransfer`, `wrongRiderCannotConfirm`, `wrongRiderCannotReportMissingAccreditation` |
| REG-PAY-AUTH-002 | Conocer el RID asignado no autoriza a leer el comprobante ni confirmar/reportar un pago sin la sesión Rider autenticada coincidente. | `RiderSessionAndTipIntegrationTest.REG-PAY-AUTH-002…` |
| REG-PAY-TIP-001 | Propina por transferencia: elegida → informada → confirmada. | `TipTransferPolicyTest.REG-PAY-TIP-001…` |
| REG-PAY-TIP-002 | Una propina en efectivo no se registra como propina digital. | `TipTransferPolicyTest.REG-PAY-TIP-002…` |
| REG-PAY-TIP-003 | La propina posterior no modifica el `PaymentRecord` principal confirmado. | `TipTransferPolicyTest.REG-PAY-TIP-003…` |
| REG-PAY-TIP-004 | Un Repartidor ajeno no confirma propinas. | `TipTransferPolicyTest.REG-PAY-TIP-004…` |
| REG-PAY-TIP-005 | Inconsistencias de asignación fallan cerrado. | `TipTransferPolicyTest.REG-PAY-TIP-005…` |
| REG-PAY-TIP-006 | Propinas seleccionadas/informadas no ingresan al balance. | `TipTransferPolicyTest.REG-PAY-TIP-006…` |
| REG-PAY-TIP-007 | Sólo propina de transferencia confirmada ingresa al balance. | `TipTransferPolicyTest.REG-PAY-TIP-007…` |
| REG-PAY-TIP-008 | Propina legacy en efectivo se preserva pero no computa. | `TipTransferPolicyTest.REG-PAY-TIP-008…` |
| REG-PAY-TIP-009 | Declaración repetida es idempotente. | `TipTransferPolicyTest.REG-PAY-TIP-009…` |
| REG-PAY-TIP-010 | Confirmación repetida es idempotente. | `TipTransferPolicyTest.REG-PAY-TIP-010…` |
| REG-PAY-TIP-011 | Propinas deshabilitadas fuerzan importe digital cero. | `TipTransferPolicyTest.REG-PAY-TIP-011…` |
| REG-PAY-TIP-012 | Pedido no completado no entra al flujo de propina digital. | `TipTransferPolicyTest.REG-PAY-TIP-012…` |
| REG-PAY-TIP-013 | Importe cero no crea flujo de transferencia de propina. | `TipTransferPolicyTest.REG-PAY-TIP-013…` |
| REG-PAY-TIP-014 | Datos legacy cash no aparecen como propinas digitales activas. | `TipTransferPolicyTest.REG-PAY-TIP-014…` |
| REG-PAY-TIP-015 | El balance usa una única regla y sólo suma propinas de transferencia confirmadas. | `TipTransferPolicyTest.REG-PAY-TIP-015…` |
| REG-PAY-TIP-016 | La identidad autenticada del Repartidor debe coincidir con el actor que opera la propina; conocer otro RID no autoriza. | `TipTransferPolicyTest.REG-PAY-TIP-016…` |
| REG-PAY-TIP-017 | Fixture integral realista con propina por transferencia de $100 llega a `TRANSFER_DECLARED` y produce exactamente una propina pendiente para el Rider autenticado asignado. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-017…` |
| REG-PAY-TIP-018 | Confirmar la propina de $100 la deja `TRANSFER_DECLARED`, la quita de pendientes, queda visible en el dato histórico y suma exactamente $100 al balance. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-018…` |
| REG-PAY-TIP-019 | Persistencia/reinicio + nueva autenticación conserva la propina `CONFIRMED`, su importe, balance y único evento. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-019…` |
| REG-PAY-TIP-020 | Una segunda confirmación es idempotente: no duplica saldo, no duplica `TIP_TRANSFER_CONFIRMED` ni produce efectos financieros repetidos. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-020…` |
| REG-PAY-TIP-021 | Rider B no ve, no puede confirmar ni altera la propina de Rider A. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-021…` |
| REG-RIDER-AUTH-001 | Sin sesión Rider autenticada, las acciones self-service sensibles fallan cerrado. | `RiderSessionAndTipIntegrationTest.REG-RIDER-AUTH-001…` |
| REG-RIDER-AUTH-002 | Una sesión Rider A no puede operar recursos self-service de Rider B. | `RiderSessionAndTipIntegrationTest.REG-RIDER-AUTH-002…` |
| REG-RIDER-AUTH-003 | La lectura de pedidos activos queda protegida por sesión: Rider A ve sólo los propios y nunca los de Rider B. | `RiderEligibilityBoundaryRegressionTest.REG-RIDER-AUTH-003…` |
| REG-SHIFT-RULE-001 | Las reglas aceptan sólo días/horarios/cupo válidos, incluido inicio `00:00` y fin `24:00`. | `ShiftSchedulePolicyTest.REG-SHIFT-RULE-001…` |
| REG-SHIFT-RULE-002 | Las reglas detectan solapamientos por ventanas reales, incluido domingo → lunes. | `ShiftSchedulePolicyTest.REG-SHIFT-RULE-002…` |
| REG-SHIFT-DATE-STRICT-001 | Las fechas internas usan ISO estricto y las fechas imposibles/ambiguas se rechazan. | `ShiftSchedulePolicyTest.REG-SHIFT-DATE-STRICT-001…` |
| REG-SHIFT-CONCRETE-001 | Un `ConcreteShift` mantiene `serviceDate` canónica e inmutable durante la edición. | `ShiftConcreteIntegrationTest.REG-SHIFT-CONCRETE-001…` |
| REG-SHIFT-GEN-RANGE-001 | La generación manual es inclusiva, soporta un día, múltiples semanas, días sin reglas y límites inicial/final parciales. | `ShiftSchedulePolicyTest.REG-SHIFT-GEN-RANGE-001…` |
| REG-SHIFT-GEN-IDEMPOTENT-001 | La identidad de un turno generado es `(originRuleId, serviceDate)` y una confirmación repetida no duplica lineage. | `ShiftSchedulePolicyTest.REG-SHIFT-GEN-IDEMPOTENT-001…`, `ShiftConcreteIntegrationTest.REG-SHIFT-GEN-IDEMPOTENT-001…` |
| REG-SHIFT-GEN-IDEMPOTENT-002 | Una ocurrencia generada luego editada/deshabilitada como excepción conserva identidad y no se recrea ni sobrescribe. | `ShiftSchedulePolicyTest.REG-SHIFT-GEN-IDEMPOTENT-002…`, `ShiftConcreteIntegrationTest.REG-SHIFT-GEN-IDEMPOTENT-002…` |
| REG-SHIFT-GEN-CONFLICT-001 | Un turno ajeno solapado invalida la generación completa. | `ShiftSchedulePolicyTest.REG-SHIFT-GEN-CONFLICT-001…` |
| REG-SHIFT-GEN-CONFLICT-002 | Un turno ajeno deshabilitado también bloquea una generación solapada. | `ShiftSchedulePolicyTest.REG-SHIFT-GEN-CONFLICT-002…` |
| REG-SHIFT-GEN-ATOMIC-001 | Error de validación/conflicto deja memoria y persistencia sin aplicación parcial. | `ShiftSchedulePolicyTest.REG-SHIFT-GEN-ATOMIC-001…`, `ShiftConcreteIntegrationTest.REG-SHIFT-GEN-ATOMIC-001…` |
| REG-SHIFT-GEN-PERSIST-001 | Reglas, turnos concretos, reservas y excepciones sobreviven recarga coherentemente. | `ShiftConcreteIntegrationTest.REG-SHIFT-GEN-PERSIST-001…` |
| REG-SHIFT-TEMPLATE-FUTURE-001 | Editar una regla afecta sólo generaciones futuras y no reescribe ocurrencias materializadas. | `ShiftConcreteIntegrationTest.REG-SHIFT-TEMPLATE-FUTURE-001…` |
| REG-SHIFT-TEMPLATE-DELETE-001 | Eliminar una regla preserva ocurrencias concretas existentes, reservas y lineage. | `ShiftConcreteIntegrationTest.REG-SHIFT-TEMPLATE-DELETE-001…` |
| REG-SHIFT-OVERNIGHT-001 | Turnos que cruzan medianoche usan intervalos reales `[inicio, fin)` y permanecen activos sólo dentro de la ventana correcta. | `ShiftSchedulePolicyTest.REG-SHIFT-OVERNIGHT-001…`, `ShiftConcreteIntegrationTest.REG-SHIFT-OVERNIGHT-001…` |
| REG-SHIFT-EDIT-RESERVED-001 | Con reservas activas, cambiar el horario del turno concreto está bloqueado. | `ShiftConcreteIntegrationTest.REG-SHIFT-EDIT-RESERVED-001…` |
| REG-SHIFT-EDIT-RESERVED-002 | Con reservas activas, capacidad puede aumentar o bajar hasta `reservedCount`, nunca por debajo. | `ShiftConcreteIntegrationTest.REG-SHIFT-EDIT-RESERVED-002…`, `ShiftReservationV2IntegrationTest.REG-SHIFT-EDIT-RESERVED-002…` |
| REG-SHIFT-DISABLE-001 | Un turno concreto con reservas activas no puede deshabilitarse. | `ShiftConcreteIntegrationTest.REG-SHIFT-DISABLE-001…` |
| REG-SHIFT-RESERVATION-DATE-001 | La reserva recibe `concreteShiftId`; la fecha se deriva exclusivamente del `ConcreteShift`. | `ShiftConcreteIntegrationTest.REG-SHIFT-RESERVATION-DATE-001…` |
| REG-SHIFT-AUTH-001 | Rider A no puede reservar ni cancelar turnos actuando como Rider B. | `RiderSessionAndTipIntegrationTest.REG-SHIFT-AUTH-001…` |
| REG-SHIFT-ALPHA-RESET-001 | El reset lógico v2 elimina sólo legado de Turnos, reconcilia disponibilidad y conserva Cliente/config/pedidos/pagos y otros datos ajenos. | `ShiftConcreteIntegrationTest.REG-SHIFT-ALPHA-RESET-001…`, `ShiftAlphaResetScopeTest.REG-SHIFT-ALPHA-RESET-001…` |
| REG-SHIFT-DIALOG-001 | Editores/calendarios nuevos respetan TODO-2: toque exterior no cierra y Back/CANCEL protegen cambios dirty. | `ShiftV2UiPolicyTest.REG-SHIFT-DIALOG-001…` |
| REG-SHIFT-GEN-PREVIEW-001 | Previsualizar generación no persiste ni altera memoria. | `ShiftGenerationConcurrencyTest.REG-SHIFT-GEN-PREVIEW-001…` |
| REG-SHIFT-GEN-CONCURRENCY-001 | Dos controladores no pueden confirmar generación usando un snapshot obsoleto. | `ShiftGenerationConcurrencyTest.REG-SHIFT-GEN-CONCURRENCY-001…` |
| REG-SHIFT-STORE-CAS-001 | Una escritura v2 con revisión obsoleta falla sin pisar el snapshot vigente. | `ShiftGenerationConcurrencyTest.REG-SHIFT-STORE-CAS-001…` |
| REG-SHIFT-CANCEL-001 | Una cancelación reciente impone el cooldown existente de 15 minutos. | `ShiftReservationV2IntegrationTest.REG-SHIFT-CANCEL-001…` |
| REG-SHIFT-CANCEL-002 | La segunda cancelación de la misma ocurrencia bloquea una nueva reinscripción. | `ShiftReservationV2IntegrationTest.REG-SHIFT-CANCEL-002…` |
| REG-SHIFT-CANCEL-003 | Cancelar el único turno activo desactiva disponibilidad y persiste auditoría. | `ShiftReservationV2IntegrationTest.REG-SHIFT-CANCEL-003…` |
| REG-SHIFT-UI-001 | Administración distingue reglas, generación y turnos concretos sin exponer lineage técnico. | `ShiftV2UiPolicyTest.REG-SHIFT-UI-001…` |
| REG-SHIFT-WIRING-001 | Los entry points operativos de Admin y Repartidor delegan exclusivamente en las pantallas v2; las implementaciones v1 quedan no navegables. | `ShiftV2UiPolicyTest.REG-SHIFT-WIRING-001…` |
| REG-ORDER-AUTH-001 | Rider A no puede tomar ni modificar pedidos actuando como Rider B; las acciones administrativas explícitas quedan atribuidas a `ADMIN`. | `RiderSessionAndTipIntegrationTest.REG-ORDER-AUTH-001…` |
| REG-ORDER-RECEPTION-001 | Con recepción pausada, el dominio rechaza una nueva solicitud sin alterar pedidos, pagos, eventos de pedidos preexistentes ni el draft, incluso tras recarga. | `OrderReceptionPolicyTest.REG-ORDER-RECEPTION-001…` |
| REG-ORDER-RECEPTION-002 | Un flujo iniciado mientras estaba habilitado falla cerrado si Administración pausa antes del envío final. | `OrderReceptionPolicyTest.REG-ORDER-RECEPTION-002…` |
| REG-ORDER-RECEPTION-003 | Pausar nuevas solicitudes no bloquea operaciones válidas sobre pedidos ya existentes. | `OrderReceptionPolicyTest.REG-ORDER-RECEPTION-003…` |
| REG-ORDER-RECEPTION-004 | Reactivar la recepción vuelve a permitir la creación normal de solicitudes. | `OrderReceptionPolicyTest.REG-ORDER-RECEPTION-004…` |
| REG-ORDER-RECEPTION-005 | `closedMessage` personalizado persiste, se devuelve en el rechazo y no se sobrescribe al alternar HABILITADA/PAUSADA. | `OrderReceptionPolicyTest.REG-ORDER-RECEPTION-005…` |
| REG-ORDER-RECEPTION-UI-001 | Admin expone estado textual HABILITADA/PAUSADA y Review consulta `acceptingOrders` actual, muestra `closedMessage` y deshabilita envío durante la pausa. | `OrderReceptionPolicyTest.REG-ORDER-RECEPTION-UI-001…` |
| REG-RIDER-ELIG-001 | Fixture Rider persistido previo a dev3.8, activo, aprobado y con documentación obligatoria aprobada conserva login, Turnos, reserva y Disponibilidad. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-001…` |
| REG-RIDER-ELIG-002 | Un Rider creado con el modelo actual, activo, aprobado y con documentación obligatoria aprobada tiene la misma elegibilidad operativa. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-002…` |
| REG-RIDER-ELIG-003 | La sesión Rider A nunca habilita elegibilidad ni operaciones self-service de Rider B. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-003…` |
| REG-RIDER-ELIG-004 | Documento obligatorio `PENDING`: login permitido, nuevo trabajo denegado con motivo tipado. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-004…` |
| REG-RIDER-ELIG-005 | Documento obligatorio `REJECTED`: login permitido, nuevo trabajo denegado con motivo tipado. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-005…` |
| REG-RIDER-ELIG-006 | Documento obligatorio no cargado: login permitido, nuevo trabajo denegado con motivo tipado. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-006…` |
| REG-RIDER-ELIG-007 | Rider `SUSPENDED`: login e información propia permitidos; Turnos, reserva, Disponibilidad y pedidos nuevos denegados. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-007…` |
| REG-RIDER-ELIG-008 | Un Rider suspendido puede continuar/finalizar únicamente un pedido ya asignado con transición válida, pero no tomar uno nuevo. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-008…` |
| REG-RIDER-ELIG-009 | Documentación que deja de ser apta permite continuar/finalizar únicamente un pedido ya asignado, pero bloquea pedidos nuevos. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ELIG-009…` |
| REG-RIDER-ELIG-010 | `PENDING_APPROVAL` no puede usar la excepción de continuidad de pedido ya asignado; la excepción queda limitada a SUSPENDED/documentación no apta. | `RiderEligibilityBoundaryRegressionTest.REG-RIDER-ELIG-010…` |
| REG-RIDER-LOGIN-001 | Cuenta desactivada + contraseña correcta devuelve `DEACTIVATED` y no crea sesión. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-LOGIN-001…` |
| REG-RIDER-LOGIN-002 | Cuenta desactivada + contraseña incorrecta devuelve el mismo `INVALID_CREDENTIALS` genérico y no revela estado/identidad. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-LOGIN-002…` |
| REG-RIDER-LOGIN-003 | Una invitación de un Rider desactivado no crea credencial ni sesión operativa al canjearse. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-LOGIN-003…` |
| REG-RIDER-SESSION-003 | Desactivar un Rider autenticado invalida inmediatamente su sesión y toda capacidad protegida, incluido un pedido previamente asignado. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-SESSION-003…` |
| REG-RIDER-SESSION-004 | Recrear controlador/Activity no convierte `RIDER_WORKSPACE + RID` restaurados en una sesión Rider válida. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-SESSION-004…` |
| REG-RIDER-SESSION-005 | Un Rider desactivado no puede continuar/finalizar un pedido en curso aunque hubiera sido asignado antes de la desactivación. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-SESSION-005…` |
| REG-RIDER-HISTORY-002 | “Entregas” cuenta sólo `COMPLETED`; `CANCELLED/REJECTED` pueden conservarse en historial y el historial queda aislado por sesión Rider A/B. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-HISTORY-002…` |
| REG-RIDER-DENIAL-001 | Las denegaciones de nuevo trabajo exponen un motivo tipado utilizable por UI, incluyendo documentación. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-DENIAL-001…` |
| REG-RIDER-DENIAL-002 | Los wrappers usados por UI publican el motivo tipado y éste se transforma en un mensaje entendible, no en no-op silencioso. | `RiderEligibilityBoundaryRegressionTest.REG-RIDER-DENIAL-002…` |
| REG-RIDER-ADMIN-001 | Las acciones/vistas administrativas no crean sesión Rider ni habilitan el workspace self-service. | `RiderEligibilityAndSessionIntegrationTest.REG-RIDER-ADMIN-001…` |
| REG-ADMIN-AUTH-001 | Android/config de build no contiene `ALPHA_ADMIN_PIN` como credencial operativa. | `AdminAccessPolicyTest.REG-ADMIN-AUTH-001…` |
| REG-ADMIN-AUTH-002 | Un request Admin sin Firebase token válido falla cerrado con 401. | Backend `worker/test/admin-auth.test.js` — `REG-ADMIN-AUTH-002…` |
| REG-ADMIN-AUTH-003 | Un Firebase UID autenticado ausente de `admin_users` recibe 403. | Backend `worker/test/admin-auth.test.js` — `REG-ADMIN-AUTH-003…` |
| REG-ADMIN-AUTH-004 | Un Firebase UID Admin habilitado recibe autorización. | Backend `worker/test/admin-auth.test.js` — `REG-ADMIN-AUTH-004…` |
| REG-ADMIN-AUTH-005 | Un Firebase UID Admin deshabilitado recibe 403. | Backend `worker/test/admin-auth.test.js` — `REG-ADMIN-AUTH-005…` |
| REG-ADMIN-AUTH-006 | Config/D1 ausente o fallando nunca autoriza Administración. | Backend `worker/test/admin-auth.test.js` — `REG-ADMIN-AUTH-006…` |
| REG-ADMIN-AUTH-007 | El UID de autorización proviene del usuario Firebase verificado y no puede reemplazarse por parámetros/cabeceras. | Backend `worker/test/admin-auth.test.js` — `REG-ADMIN-AUTH-007…` |
| REG-ADMIN-AUTH-008 | Todas las respuestas del gate Admin usan `Cache-Control: no-store`. | Backend `worker/test/admin-auth.test.js` — `REG-ADMIN-AUTH-008…` |
| REG-ADMIN-ANDROID-001 | Android no compara PIN local ni referencia `BuildConfig.ALPHA_ADMIN_PIN`; usa el gate backend con Firebase ID Token. | `AdminAccessPolicyTest.REG-ADMIN-ANDROID-001…` |
| REG-ADMIN-ANDROID-002 | Sin sesión Firebase el acceso Admin se deniega antes de llamar al backend. | `AdminAccessPolicyTest.REG-ADMIN-ANDROID-002…` |
| REG-ADMIN-ANDROID-003 | Backend 403 no concede la sesión Admin transitoria. | `AdminAccessPolicyTest.REG-ADMIN-ANDROID-003…` |
| REG-ADMIN-ANDROID-004 | Error de backend/red falla cerrado y no concede navegación Admin. | `AdminAccessPolicyTest.REG-ADMIN-ANDROID-004…` |
| REG-ADMIN-ANDROID-005 | Sólo `200` con `authorized=true` concede la sesión Admin transitoria. | `AdminAccessPolicyTest.REG-ADMIN-ANDROID-005…` |
| REG-ADMIN-ANDROID-006 | Salir del área Admin o cerrar sesión invalida la autorización transitoria. | `AdminAccessPolicyTest.REG-ADMIN-ANDROID-006…` |
| REG-ADMIN-ANDROID-007 | Un proceso recreado no conserva autorización Admin y obliga a pasar nuevamente por el gate backend. | `AdminAccessPolicyTest.REG-ADMIN-ANDROID-007…` |

| REG-ZONE-PRESENTATION-001 | Las zonas se ordenan sólo para presentación por categoría y nombre, ignorando mayúsculas/tildes, con estabilidad determinista y sin mutar el orden persistido. | `ZonePresentationTest` |
| REG-DIALOG-DISMISS-001 | Las ventanas propias de Punto25 no se cierran ni ejecutan acciones por toque exterior; Atrás sigue habilitado salvo bloqueos deliberados o protección de cambios pendientes. | `DialogDismissPolicyTest.REG-DIALOG-DISMISS-001…` |
| REG-RIDER-EDIT-DIRTY-001 | Alta de Repartidor parte limpia y cualquier cambio pendiente relevante activa dirty state. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DIRTY-001…` |
| REG-RIDER-EDIT-DIRTY-002 | Teléfono, fecha, vehículo, domicilio, límite, aprobación y documentos forman parte del dirty state de Alta/Editar Repartidor. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DIRTY-002…` |
| REG-RIDER-EDIT-DIRTY-003 | Evaluar dirty state no muta el `RiderProfile` original. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DIRTY-003…` |
| REG-RIDER-EDIT-DISCARD-001 | Atrás/CANCELAR cierran limpio y exigen confirmación cuando hay cambios pendientes; toque exterior nunca descarta. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DISCARD-001…` |
| REG-RIDER-EDIT-REVERT-001 | Revertir exactamente un cambio al estado inicial devuelve el formulario a limpio. | `DialogDismissPolicyTest.REG-RIDER-EDIT-REVERT-001…` |
| REG-RIDER-EDIT-REVERT-002 | Revertir por completo múltiples cambios devuelve el formulario a limpio. | `DialogDismissPolicyTest.REG-RIDER-EDIT-REVERT-002…` |
| REG-RIDER-EDIT-SAVE-001 | Guardar un Repartidor continúa persistiendo los campos/documentos normalmente; un fallo de guardado no debe cerrar el formulario. | `DialogDismissPolicyTest.REG-RIDER-EDIT-SAVE-001…` |

Los nuevos bugs de Clase A deben incorporar, cuando sea técnicamente razonable, un `REG-*` y un test permanente antes de cerrar la tanda que los corrige.
