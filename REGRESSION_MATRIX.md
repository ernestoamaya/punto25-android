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
| REG-SHIFT-AUTH-001 | Rider A no puede reservar ni cancelar turnos actuando como Rider B. | `RiderSessionAndTipIntegrationTest.REG-SHIFT-AUTH-001…` |
| REG-ORDER-AUTH-001 | Rider A no puede tomar ni modificar pedidos actuando como Rider B; las acciones administrativas explícitas quedan atribuidas a `ADMIN`. | `RiderSessionAndTipIntegrationTest.REG-ORDER-AUTH-001…` |

Los nuevos bugs de Clase A deben incorporar, cuando sea técnicamente razonable, un `REG-*` y un test permanente antes de cerrar la tanda que los corrige.
