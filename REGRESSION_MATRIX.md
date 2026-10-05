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
| REG-PAY-TIP-018 | Confirmar la propina de $100 la deja `CONFIRMED`, la quita de pendientes, queda visible en el dato histórico y suma exactamente $100 al balance. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-018…` |
| REG-PAY-TIP-019 | Persistencia/reinicio + nueva autenticación conserva la propina `CONFIRMED`, su importe, balance y único evento. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-019…` |
| REG-PAY-TIP-020 | Una segunda confirmación es idempotente: no duplica saldo, no duplica `TIP_TRANSFER_CONFIRMED` ni produce efectos financieros repetidos. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-020…` |
| REG-PAY-TIP-021 | Rider B no ve, no puede confirmar ni altera la propina de Rider A. | `RiderSessionAndTipIntegrationTest.REG-PAY-TIP-021…` |
| REG-RIDER-AUTH-001 | Sin sesión Rider autenticada, las acciones self-service sensibles fallan cerrado. | `RiderSessionAndTipIntegrationTest.REG-RIDER-AUTH-001…` |
| REG-RIDER-AUTH-002 | Una sesión Rider A no puede operar recursos self-service de Rider B. | `RiderSessionAndTipIntegrationTest.REG-RIDER-AUTH-002…` |
| REG-RIDER-AUTH-003 | La lectura de pedidos activos queda protegida por sesión: Rider A ve sólo los propios y nunca los de Rider B. | `RiderEligibilityBoundaryRegressionTest.REG-RIDER-AUTH-003…` |
| REG-SHIFT-AUTH-001 | Rider A no puede reservar ni cancelar turnos actuando como Rider B. | `RiderSessionAndTipIntegrationTest.REG-SHIFT-AUTH-001…` |
| REG-ORDER-AUTH-001 | Rider A no puede tomar ni modificar pedidos actuando como Rider B; las acciones administrativas explícitas quedan atribuidas a `ADMIN`. | `RiderSessionAndTipIntegrationTest.REG-ORDER-AUTH-001…` |
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

Los nuevos bugs de Clase A deben incorporar, cuando sea técnicamente razonable, un `REG-*` y un test permanente antes de cerrar la tanda que los corrige.
