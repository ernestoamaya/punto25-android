# Punto25 — Matriz Maestra de Regresión

Esta matriz vincula invariantes críticos con tests ejecutables. Los tests son la fuente de verdad de PASS/FAIL; la tabla mantiene trazabilidad estable.

| ID | Invariante | Test ejecutable |
|---|---|---|
| REG-IDENTITY-001 | Un Cliente nunca obtiene pedidos de otro Cliente por coincidencias débiles. | `CustomerOrderOwnershipTest.differentCustomerNeverMatchesEvenWithSameVisibleName` |
| REG-HISTORY-001 | Los alias Alpha `CLI-/DEV-` recuperan historial sólo cuando corresponden al mismo teléfono verificado. | `CustomerOrderOwnershipTest.legacyCliIdMatchesVerifiedCurrentCustomer`, `previousDevIdMatchesVerifiedGoogleCustomerWithSameNumber` |
| REG-PAY-001 | Un pago confirmado queda fuera de los estados pendientes de transferencia. | `PaymentTransferPolicyTest.confirmedIsCompletedNotPending` |
| REG-PAY-AUTH-001 | Un Repartidor ajeno no puede acceder, confirmar ni reportar una transferencia. | `PaymentTransferPolicyTest.wrongRiderCannotAccessTransfer`, `wrongRiderCannotConfirm`, `wrongRiderCannotReportMissingAccreditation` |
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

Los nuevos bugs de Clase A deben incorporar, cuando sea técnicamente razonable, un `REG-*` y un test permanente antes de cerrar la tanda que los corrige.
