# Matriz Maestra de Regresión — Punto25

Esta matriz vincula los invariantes críticos del producto con tests ejecutables. Debe actualizarse junto con cada corrección de seguridad, identidad, aislamiento, pagos, turnos o persistencia.

| REG-ID | Invariante | Test ejecutable |
|---|---|---|
| REG-IDENTITY-GOOGLE-001 | Un login Google no verificado no puede crear sesión local. | `GoogleIdentityPolicyTest.unverifiedGoogleAccountIsRejected` |
| REG-IDENTITY-GOOGLE-002 | El `accountId` canónico se deriva del `sub` verificado y no del email/teléfono. | `GoogleIdentityPolicyTest.canonicalIdDependsOnSubjectAndNotOnEmailOrPhone` |
| REG-IDENTITY-GOOGLE-003 | Un `sub` vacío es inválido. | `GoogleIdentityPolicyTest.blankSubjectIsRejected` |
| REG-IDENTITY-MANUAL-001 | El alta manual persiste hasta completar OTP; el alta exige nombre y teléfono válidos. | `MandadosControllerRegistrationTest.manualRegistrationPersistsAfterValidOtp` |
| REG-IDENTITY-MANUAL-002 | Un OTP incorrecto no crea identidad local ni persiste usuario. | `MandadosControllerRegistrationTest.wrongOtpDoesNotCreateIdentity` |
| REG-IDENTITY-MANUAL-003 | Si no existe OTP verificable, la confirmación falla cerrada. | `MandadosControllerRegistrationTest.confirmationFailsClosedWhenNoOtpCanBeVerified` |
| REG-IDENTITY-MANUAL-004 | Reiniciar el alta manual descarta el estado transitorio anterior. | `MandadosControllerRegistrationTest.restartingManualRegistrationDropsPreviousPendingState` |
| REG-IDENTITY-MANUAL-005 | `currentCustomerId()` y `customerOrder()` usan el ID canónico post-OTP. | `MandadosControllerRegistrationTest.ordersAreScopedToCanonicalCustomerIdAfterOtp` |
| REG-IDENTITY-MANUAL-006 | Una identidad local ya creada no puede reapropiarse con el mismo teléfono y otro código OTP. | `MandadosControllerRegistrationTest.otpCannotTakeOverAnExistingManualIdentityForTheSamePhone` |
| REG-IDENTITY-MANUAL-007 | Un usuario manual existente conserva `accountId` estable después de reiniciar la app. | `MandadosControllerRegistrationTest.existingManualUserKeepsStableAccountIdAcrossRestart` |
| REG-CUSTOMER-OWNERSHIP-001 | Un pedido con `accountId` canónico exacto pertenece al cliente actual. | `CustomerOrderOwnershipTest.exactCurrentIdMatches` |
| REG-CUSTOMER-OWNERSHIP-002 | Un pedido legacy `CLI-<teléfono>` puede reconciliarse sólo contra un cliente actual verificado con el mismo número. | `CustomerOrderOwnershipTest.legacyCliIdMatchesVerifiedCurrentCustomer` |
| REG-CUSTOMER-OWNERSHIP-003 | Un pedido previo `DEV-<teléfono>` puede reconciliarse con el cliente Google verificado actual del mismo número. | `CustomerOrderOwnershipTest.previousDevIdMatchesVerifiedGoogleCustomerWithSameNumber` |
| REG-CUSTOMER-OWNERSHIP-004 | Un `accountId` arbitrario no se reclama por coincidencia de nombre/teléfono. | `CustomerOrderOwnershipTest.arbitraryAccountIdIsNotMatchedByPhone` |
| REG-CUSTOMER-OWNERSHIP-005 | Un cliente no verificado no puede reclamar IDs legacy por teléfono. | `CustomerOrderOwnershipTest.unverifiedCustomerCannotClaimNonExactPhoneBasedLegacyId` |
| REG-CUSTOMER-OWNERSHIP-006 | Un pedido con identidad vacía nunca se asigna por heurística. | `CustomerOrderOwnershipTest.blankLegacyIdentityNeverMatches` |
| REG-CUSTOMER-REG-001 | Un Cliente con identidad canónica puede crear un pedido aun sin configuración WhatsApp de negocio. | `CustomerOrderRegistrationTest.customerOrderIsPersistedWithoutBusinessWhatsappConfiguration` |
| REG-CUSTOMER-REG-002 | Si WhatsApp de negocio existe, el pedido se registra primero y luego se abre la mensajería como canal opcional. | `CustomerOrderRegistrationTest.orderRegistrationRemainsSuccessfulWhenWhatsappTransportIsConfigured` |
| REG-CUSTOMER-REG-003 | La UI trata el registro del pedido como operación primaria y WhatsApp sólo como fallback opcional. | `CustomerOrderRegistrationTest.customerUiDoesNotBlockRegistrationWhenBusinessWhatsappIsBlank` |
| REG-CUSTOMER-REG-004 | `customerOrder(id)` sólo devuelve pedidos pertenecientes al Cliente actual; un ID ajeno falla cerrado. | `CustomerOrderRegistrationTest.customerOrderLookupIsOwnershipScoped` |
| REG-CUSTOMER-REG-005 | El alta Multi-Rider ya no genera URLs `maps.google.com` para retiro/entrega. | `CustomerOrderRegistrationTest.multiRiderOrderNoLongerGeneratesGoogleMapsUrls` |
| REG-CUSTOMER-REG-006 | El alta Modo Simple ya no genera URLs `maps.google.com` y conserva texto humano de dirección. | `CustomerOrderRegistrationTest.simpleModeOrderNoLongerGeneratesGoogleMapsUrls` |
| REG-CUSTOMER-REG-007 | La UI cliente ya no renderiza `order.detail` crudo ni usa navegación directa de detalle sin scope de ownership. | `CustomerOrderRegistrationTest.customerUiUsesOwnershipScopedStructuredOrderDetail` |
| REG-CUSTOMER-REG-008 | El formulario de pedido nuevo ofrece selector de mapa reutilizable para retiro/comercio/entrega. | `CustomerOrderRegistrationTest.newOrderUiUsesMapPickerForOrderLocations` |
| REG-CUSTOMER-REG-009 | Editar dirección invalida un pin viejo para evitar desalineación dirección/coordenadas. | `CustomerOrderRegistrationTest.changingAddressInvalidatesExistingPin` |
| REG-CUSTOMER-REG-010 | Seleccionar un punto en el mapa sincroniza dirección visible y GeoPoint en el draft. | `CustomerOrderRegistrationTest.applyingMapSelectionKeepsAddressAndCoordinatesAligned` |
| REG-CUSTOMER-REG-011 | Al editar un pedido, las coordenadas existentes se conservan y no se recrean desde `detail`. | `CustomerOrderRegistrationTest.draftFromOrderPreservesStructuredCoordinates` |
| REG-CUSTOMER-REG-012 | El formulario expone una acción de ubicación del dispositivo además del selector manual en mapa. | `CustomerOrderRegistrationTest.newOrderUiExposesCurrentLocationAction` |
| REG-CUSTOMER-REG-013 | La ubicación del dispositivo sólo se aplica al target actualmente seleccionado. | `CustomerOrderRegistrationTest.deviceLocationUsesTheRequestedTargetOnly` |
| REG-CUSTOMER-REG-014 | Si no hay teléfono verificable, el alta exige número completo válido y no lo inventa desde una identidad canónica opaca. | `MandadosControllerRegistrationTest.manualRegistrationRequiresFullPhoneWhenNoVerifiedPhoneIsAvailable` |
| REG-CUSTOMER-REG-015 | Una identidad previa `DEV-<teléfono>` puede usar ese teléfono sólo como fallback explícito de compatibilidad. | `MandadosControllerRegistrationTest.legacyDevIdentityCanProvideVerifiedPhoneFallback` |
| REG-OTP-001 | Generar OTP nuevo invalida códigos pendientes anteriores para el mismo teléfono/propósito. | `OtpRepositoryTest.issuingNewOtpInvalidatesPreviousPendingCodeForSamePhoneAndPurpose` |
| REG-OTP-002 | Un OTP de registro se consume una sola vez. | `OtpRepositoryTest.registrationOtpIsSingleUse` |
| REG-OTP-003 | Superado `maxAttempts`, el OTP queda bloqueado. | `OtpRepositoryTest.otpLocksAfterMaximumAttempts` |
| REG-OTP-004 | Los OTP expirados fallan y se purgan. | `OtpRepositoryTest.expiredOtpIsRejectedAndPurged` |
| REG-RIDER-AUTH-001 | Un código de invitación válido crea credencial Rider y no puede reutilizarse. | `RiderAuthTest.invitationCreatesCredentialAndIsSingleUse` |
| REG-RIDER-AUTH-002 | Una invitación vencida no crea credencial. | `RiderAuthTest.expiredInvitationDoesNotCreateCredential` |
| REG-RIDER-AUTH-003 | Una invitación para Rider inexistente se invalida sin crear credencial huérfana. | `RiderAuthTest.orphanInvitationIsRejectedAndRemoved` |
| REG-RIDER-AUTH-004 | Login Rider exige RID exacto y contraseña válida. | `RiderAuthTest.riderLoginRequiresExactIdAndCorrectPassword` |
| REG-RIDER-AUTH-005 | La sesión Rider se restaura sólo si la cuenta sigue siendo válida. | `RiderAuthTest.riderSessionRestoresOnlyWhileAccountRemainsUsable` |
| REG-RIDER-AUTH-006 | La sesión Rider expirada no se restaura. | `RiderAuthTest.expiredRiderSessionIsNotRestored` |
| REG-RIDER-AUTH-007 | Deshabilitar Rider invalida la sesión activa. | `RiderAuthTest.disablingRiderInvalidatesActiveSession` |
| REG-RIDER-AUTH-008 | Suspender Rider invalida la sesión activa. | `RiderAuthTest.suspendingRiderInvalidatesActiveSession` |
| REG-RIDER-AUTH-009 | Reset de acceso invalida contraseña/sesión y crea invitación de un solo uso. | `RiderAuthTest.resetAccessInvalidatesPasswordAndSessionThenCreatesSingleUseInvitation` |
| REG-RIDER-AUTH-010 | Cambiar contraseña exige contraseña actual correcta. | `RiderAuthTest.passwordChangeRequiresCurrentPassword` |
| REG-RIDER-AUTH-011 | Contraseñas débiles son rechazadas. | `RiderAuthTest.weakPasswordsAreRejected` |
| REG-RIDER-AUTH-012 | Un Rider no puede autenticarse usando la credencial de otro RID. | `RiderAuthTest.riderCannotAuthenticateWithAnotherRidersCredential` |
| REG-RIDER-AUTH-013 | Registros huérfanos de sesión/credencial/invitación se purgan al cargar. | `RiderAuthTest.orphanCredentialSessionAndInvitationArePurgedOnLoad` |
| REG-RIDER-AUTH-014 | No se crea invitación sin teléfono configurado. | `RiderAuthTest.invitationIsNotCreatedWithoutRiderPhone` |
| REG-RIDER-AUTH-015 | Flujo exacto RID válido + código nuevo + contraseña nueva habilita login posterior. | `RiderAuthTest.validRidPlusInvitationPlusNewPasswordEnablesLaterLogin` |
| REG-RIDER-AUTH-016 | No se persiste contraseña Rider en texto plano. | `RiderAuthTest.persistedCredentialDoesNotContainPlainPassword` |
| REG-RIDER-ACCESS-001 | Panel Rider y acciones sensibles quedan bloqueados sin sesión válida del RID solicitado. | `RiderAuthTest.riderPanelAndSensitiveActionsRequireMatchingSession` |
| REG-PAY-ACCESS-001 | Declarar transferencia, adjuntar comprobante y tip exige Cliente propietario autenticado. | `PaymentTransferFlowTest.unauthenticatedOrForeignCustomerCannotDeclarePaymentProofOrTip` |
| REG-PAY-ACCESS-002 | Confirmar/reportar transferencia y confirmar propina exige sesión Rider coincidente con el asignado. | `PaymentTransferFlowTest.riderTransferActionsRequireMatchingAuthenticatedSession` |
| REG-PAY-ACCESS-003 | Alias de cobro sólo se modifica desde sesión autenticada del mismo Rider. | `PaymentTransferFlowTest.riderAliasUpdateRequiresMatchingAuthenticatedSession` |
| REG-PAY-ACCESS-004 | Comprobante de pago sólo es accesible para Cliente propietario, Rider asignado autenticado o Admin. | `PaymentTransferFlowTest.proofAccessIsRoleAndOwnershipScoped` |
| REG-PAY-ACCESS-005 | `paymentForOrder` sólo expone pagos al propietario autenticado, Rider asignado autenticado o Admin. | `PaymentTransferFlowTest.paymentLookupIsScopedByAuthenticatedPrincipal` |
| REG-PAY-ACCESS-006 | Consultas Rider de pagos/propinas/balances retornan vacío o cero sin sesión válida coincidente. | `PaymentTransferFlowTest.riderFinancialQueriesRequireMatchingAuthenticatedSession` |
| REG-PAY-ACCESS-007 | `paymentForOrderAdmin` y acceso Admin a comprobantes fallan cerrados sin sesión Admin válida. | `AdminAuthTest.adminPaymentAccessFailsClosedWithoutAdminSession` |
| REG-PAY-IDEMP-001 | Redeclarar transferencia no duplica Payment ni evento. | `PaymentTransferFlowTest.paymentDeclarationIsIdempotent` |
| REG-PAY-IDEMP-002 | Reconfirmar pago no duplica evento de confirmación. | `PaymentTransferFlowTest.reconfirmingPaymentDoesNotDuplicateConfirmationEvent` |
| REG-PAY-IDEMP-003 | Readjuntar el mismo comprobante no duplica evento. | `PaymentTransferFlowTest.reattachingSameProofDoesNotDuplicateProofEvent` |
| REG-PAY-IDEMP-004 | Repetir reporte de problema no duplica evento de revisión. | `PaymentTransferFlowTest.repeatedPaymentProblemReportDoesNotDuplicateReviewEvent` |
| REG-PAY-IDEMP-005 | Confirmación positiva tras IN_REVIEW permanece idempotente. | `PaymentTransferFlowTest.confirmingAfterReviewIsStillIdempotent` |
| REG-TIP-IDEMP-001 | Redeclarar transferencia de tip no duplica eventos. | `PaymentTransferFlowTest.tipDeclarationIsIdempotent` |
| REG-TIP-IDEMP-002 | Reconfirmar tip no duplica evento. | `PaymentTransferFlowTest.tipConfirmationIsIdempotent` |
| REG-TIP-IDEMP-003 | El balance incorpora la propina confirmada una sola vez. | `PaymentTransferFlowTest.confirmedTipAffectsRiderBalanceExactlyOnce` |
| REG-TIP-CASH-001 | Pedidos en efectivo no usan el flujo digital de propina y no alteran balances digitales. | `PaymentTransferFlowTest.cashOrdersDoNotUseDigitalTipFlow` |
| REG-PAY-PROOF-001 | Con comprobante obligatorio, el Rider no puede confirmar antes de adjuntarlo. | `PaymentTransferFlowTest.proofRequiredBlocksConfirmationUntilAttachment` |
| REG-PAY-PROOF-002 | Con comprobante opcional, el Rider puede confirmar después de la declaración aun sin archivo. | `PaymentTransferFlowTest.proofOptionalAllowsConfirmationAfterDeclaration` |
| REG-PAY-PROOF-003 | Un archivo que no es imagen es rechazado como comprobante. | `PaymentTransferFlowTest.nonImageProofIsRejectedAndDoesNotPersist` |
| REG-PAY-PROOF-004 | Un payload disfrazado como imagen es rechazado por magic bytes. | `PaymentTransferFlowTest.disguisedNonImagePayloadIsRejectedByMagicBytes` |
| REG-PAY-PROOF-005 | Un payload oversized se rechaza antes de persistir. | `PaymentTransferFlowTest.oversizedProofIsRejected` |
| REG-PAY-PROOF-006 | Comprobante persistido sólo conserva URI local normalizada; referencias externas se descartan fail-closed. | `PaymentTransferFlowTest.externallyReachableProofReferencesAreDiscardedOnLoad` |
| REG-PAY-PROOF-007 | La UI Android usa selector de imágenes y no un picker arbitrario para comprobantes. | `PaymentTransferFlowTest.proofPickerIsRestrictedToImagesInAndroidUi` |
| REG-PAY-RIDER-001 | Sólo el Rider asignado autenticado puede acceder y operar la transferencia del pedido. | `PaymentTransferFlowTest.onlyAssignedRiderCanAccessAndConfirmTransfer` |
| REG-PAY-RIDER-002 | Sin asignación de Rider no hay alias individual ni confirmación Rider. | `PaymentTransferFlowTest.unassignedOrderHasNoRiderTransferDestination` |
| REG-PAY-RIDER-003 | El estado IN_REVIEW conserva el acceso del Rider para resolución posterior. | `PaymentTransferFlowTest.reviewStateKeepsRiderAccessForLaterResolution` |
| REG-PAY-DECL-001 | El importe declarado por el Cliente debe coincidir exactamente con el esperado. | `PaymentTransferFlowTest.mismatchedDeclaredAmountIsRejected` |
| REG-PAY-DECL-002 | Tras declarar transferencia el Cliente sólo puede adjuntar comprobante; no redeclarar arbitrariamente. | `PaymentTransferFlowTest.customerCannotRedeclareAfterDeclaration` |
| REG-PAY-DECL-003 | Un Cliente ajeno no puede consultar un pago por ID de pedido conocido. | `PaymentTransferFlowTest.foreignCustomerCannotReadPaymentByKnownOrderId` |
| REG-PAY-CONF-001 | Con confirmación Rider obligatoria, Cliente no puede auto-confirmar. | `PaymentTransferFlowTest.riderConfirmationPolicyBlocksCustomerAutoConfirmation` |
| REG-PAY-CONF-002 | Sin confirmación Rider obligatoria, la declaración puede cerrar el pago automáticamente. | `PaymentTransferFlowTest.declarationAutoConfirmsWhenRiderConfirmationIsDisabled` |
| REG-PAY-RATING-001 | La calificación crea tip SELECTED cuando corresponde y registra un solo rating. | `PaymentTransferFlowTest.ratingCreatesSelectedTipAndIsSingleRecord` |
| REG-PAY-LEGACY-001 | Pago legacy desconocido queda pendiente para conciliación, nunca auto-confirmado. | `PaymentTransferFlowTest.unknownLegacyPaymentStateStaysPending` |
| REG-PAY-LEGACY-002 | Pedidos simples/awaiting quote no generan Payment automáticamente. | `PaymentTransferFlowTest.simpleOrAwaitingQuoteOrdersDoNotGenerateAutomaticPayments` |
| REG-HISTORY-001 | La UI Cliente muestra timeline desde `OrderEvent` y no un estado aislado. | `CustomerOrderRegistrationTest.customerDetailRendersOrderTimeline` |
| REG-HISTORY-002 | Cancelar un pedido agrega un evento CANCELLED auditable. | `CustomerOrderRegistrationTest.cancellingOrderAppendsCancelledEvent` |
| REG-SHIFT-001 | Turnos cruzando medianoche mantienen una única ocurrencia válida con cierre al día siguiente. | `ShiftInvariantTest.overnightShiftHasSingleOccurrenceAndEndsNextDay` |
| REG-SHIFT-002 | Horarios de turno solapados son rechazados. | `ShiftInvariantTest.overlappingShiftIsRejected` |
| REG-SHIFT-003 | No se puede cancelar inscripción luego del cutoff. | `ShiftInvariantTest.cannotCancelShiftAfterCutoff` |
| REG-SHIFT-004 | Primera cancelación aplica cooldown de 15 minutos. | `ShiftInvariantTest.firstCancellationAppliesCooldown` |
| REG-SHIFT-005 | Segunda cancelación de la misma ocurrencia bloquea reinscripción. | `ShiftInvariantTest.secondCancellationBlocksRejoin` |
| REG-SHIFT-006 | Admin puede quitar al Rider y la reserva queda auditada como cancelada. | `ShiftInvariantTest.adminCanRemoveRiderFromShift` |
| REG-SHIFT-007 | No se puede eliminar un turno activo o con reservas vigentes/futuras. | `ShiftInvariantTest.activeOrReservedShiftCannotBeDeleted` |
| REG-SHIFT-008 | Editar turno conserva las inscripciones existentes. | `ShiftInvariantTest.editingShiftPreservesExistingReservations` |
| REG-SHIFT-009 | Un turno de fecha específica reemplaza las ocurrencias semanales coincidentes en esa fecha. | `ShiftInvariantTest.specificDateShiftOverridesWeeklyOccurrencesOnThatDate` |
| REG-SHIFT-010 | Reemplazo específico no duplica ocurrencias semanales. | `ShiftInvariantTest.specificDateReplacementDoesNotDuplicateWeeklyOccurrences` |
| REG-SHIFT-011 | Capacidad no puede quedar por debajo de reservas existentes. | `ShiftInvariantTest.capacityCannotDropBelowExistingReservations` |
| REG-SHIFT-012 | Rider debe estar en turno vigente para marcarse disponible. | `ShiftInvariantTest.riderMustBeOnActiveShiftToBecomeAvailable` |
| REG-SHIFT-013 | Rider fuera de turno no puede acceder a pedidos nuevos. | `ShiftInvariantTest.riderOutsideShiftCannotAccessNewOrders` |
| REG-SHIFT-014 | UI de Pedidos nuevos muestra gating por turno. | `ShiftInvariantTest.newOrdersUiContainsShiftGating` |
| REG-SHIFT-015 | Presencia Rider se normaliza a No disponible al finalizar turno, pero pedidos activos se conservan. | `ShiftInvariantTest.availabilityIsNormalizedAfterShiftEndWithoutTouchingActiveOrders` |
| REG-SHIFT-016 | Quitar al Rider de un turno activo corta disponibilidad sin tocar pedidos activos. | `ShiftInvariantTest.adminRemovalFromCurrentShiftMakesRiderUnavailableWithoutTouchingOrders` |
| REG-SHIFT-017 | Baja/inhabilitación/suspensión de Rider invalida presencia. | `ShiftInvariantTest.disablingRiderClearsAvailability` |
| REG-SHIFT-018 | Turnos simples válidos siguen funcionando. | `ShiftInvariantTest.simpleSameDayShiftRemainsValid` |
| REG-SHIFT-019 | Orden del calendario es cronológico. | `ShiftInvariantTest.occurrencesAreOrderedChronologically` |
| REG-SHIFT-020 | Auditoría de turnos sólo conserva eventos válidos y referenciales. | `ShiftInvariantTest.auditEventsRemainReferentiallyValid` |
| REG-SHIFT-PERSIST-001 | Persistencia v2 conserva templates/reservas/auditoría. | `ShiftStoreV2Test.roundTripPersistsTemplatesReservationsAndAudit` |
| REG-SHIFT-PERSIST-002 | Alta y cancelación se preservan después de reload. | `ShiftStoreV2Test.reserveThenCancelPersistsAcrossReload` |
| REG-SHIFT-PERSIST-003 | V1 migra una sola vez a v2 y no se reimporta. | `ShiftStoreV2Test.v1MigratesOnceToV2AndIsNotReimported` |
| REG-SHIFT-PERSIST-004 | Migración v1 filtra referencias huérfanas sin abortar el lote válido. | `ShiftStoreV2Test.migrationFiltersOrphanReservationsAndAuditEntries` |
| REG-SHIFT-PERSIST-005 | Migración no revive un turno v1 legacy equivalente a una baja explícita v2. | `ShiftStoreV2Test.migrationDoesNotResurrectLegacyShiftThatMatchesV2Tombstone` |
| REG-SHIFT-PERSIST-006 | Migración no revive una ocurrencia v1 deshabilitada explícitamente en v2. | `ShiftStoreV2Test.migrationDoesNotResurrectDisabledSpecificDateOccurrence` |
| REG-SHIFT-PERSIST-007 | Corrupción parcial de v2 se sanea fail-closed y reescribe snapshot válido. | `ShiftStoreV2Test.partiallyCorruptedV2StateIsSanitizedAndRewritten` |
| REG-SHIFT-PERSIST-008 | Falla de commit no avanza estado observable ni deja datos mixtos. | `ShiftStoreV2Test.failedCommitDoesNotAdvanceObservableState` |
| REG-SHIFT-PERSIST-009 | Falla de commit durante mutación no deja cambios parciales en memoria. | `ShiftStoreV2Test.controllerMutationRollsBackWhenShiftCommitFails` |
| REG-SHIFT-PERSIST-010 | Si el store transaccional v2 no carga, el controlador no cae silenciosamente a v1. | `ShiftStoreV2Test.controllerFailsClosedWhenV2StoreCannotLoad` |
| REG-SHIFT-PERSIST-011 | Si la migración v1→v2 no puede persistirse, el controlador no expone estado parcial ni legacy. | `ShiftStoreV2Test.controllerFailsClosedWhenLegacyMigrationCannotBePersisted` |
| REG-SHIFT-PERSIST-012 | Un template de turno escrito fuera de APIs v2 no se importa como estado operativo. | `ShiftStoreV2Test.v2DoesNotImportOutOfBandTemplateWrites` |
| REG-SHIFT-PERSIST-013 | Una reserva escrita fuera de APIs v2 no se importa como estado operativo. | `ShiftStoreV2Test.v2DoesNotImportOutOfBandReservationWrites` |
| REG-SHIFT-PERSIST-014 | Auditoría escrita fuera de APIs v2 no se importa como estado operativo. | `ShiftStoreV2Test.v2DoesNotImportOutOfBandAuditWrites` |
| REG-SHIFT-PERSIST-015 | Clientes con datos v2 previos a tombstones se cargan de forma compatible. | `ShiftStoreV2Test.preTombstoneV2DataRemainsCompatible` |
| REG-SHIFT-COMPAT-001 | Estado v2 sigue reflejando compatibilidad en la vista legacy durante transición. | `ShiftCompatibilityViewTest.legacyCompatibilitySnapshotMirrorsShiftState` |
| REG-SHIFT-COMPAT-002 | Adaptadores de compatibilidad no exponen estado mutable compartido. | `ShiftCompatibilityViewTest.compatibilitySnapshotsDoNotExposeMutableSharedState` |
| REG-SHIFT-CONCRETE-001 | El Rider inscripto a una ocurrencia concreta ve sólo esa ocurrencia, no toda la plantilla semanal. | `ShiftInvariantTest.riderEnrollmentIsScopedToConcreteOccurrence` |
| REG-SHIFT-CONCRETE-002 | La presencia Rider depende de la ocurrencia concreta reservada; una reserva de otra fecha no habilita disponibilidad hoy. | `ShiftInvariantTest.riderPresenceUsesConcreteOccurrenceReservation` |
| REG-SHIFT-CONCRETE-003 | Con cupo 1, ocupar una ocurrencia no llena las demás fechas de la misma plantilla. | `ShiftInvariantTest.capacityIsIndependentPerConcreteOccurrence` |
| REG-SHIFT-CONCRETE-004 | La reserva persistida conserva identidad concreta `(shiftTemplateId, serviceDate)`. | `ShiftStoreV2Test.reservationRoundTripKeepsConcreteOccurrenceIdentity` |
| REG-SHIFT-CONCRETE-005 | El Rider puede inscribirse en otra fecha de la misma plantilla semanal. | `ShiftInvariantTest.riderCanJoinAnotherDateOfSameWeeklyTemplate` |
| REG-SHIFT-CONCRETE-006 | La auditoría distingue ocurrencias concretas por `serviceDate`. | `ShiftStoreV2Test.auditRoundTripKeepsConcreteOccurrenceDate` |
| REG-SHIFT-CONCRETE-007 | Disponibilidad activa sólo se mantiene si la reserva vigente corresponde a la ocurrencia actual. | `ShiftInvariantTest.availabilityRequiresReservationForCurrentConcreteOccurrence` |
| REG-ADMIN-AUTH-001 | Un Admin válido obtiene sesión admin separada y puede reautenticarse tras logout. | `AdminAuthTest.validAdminLoginCreatesSeparateSessionAndSupportsRelogin` |
| REG-ADMIN-AUTH-002 | Contraseña Admin inválida falla cerrada y no crea sesión. | `AdminAuthTest.invalidAdminPasswordFailsClosedWithoutSession` |
| REG-ADMIN-AUTH-003 | Sesión Rider válida no concede privilegios Admin. | `AdminAuthTest.riderSessionDoesNotGrantAdminPrivileges` |
| REG-ADMIN-AUTH-004 | Sesión Admin válida no concede acceso a panel Rider. | `AdminAuthTest.adminSessionDoesNotGrantRiderPanelAccess` |
| REG-ADMIN-AUTH-005 | Logout Admin invalida privilegios y debe reautenticarse. | `AdminAuthTest.adminLogoutRequiresReauthentication` |
| REG-ADMIN-AUTH-006 | Credencial Admin persistida no contiene contraseña en texto plano. | `AdminAuthTest.persistedAdminCredentialDoesNotContainPlainPassword` |
| REG-ADMIN-AUTH-007 | Sesión Admin expirada no se restaura al reiniciar. | `AdminAuthTest.expiredAdminSessionIsNotRestored` |
| REG-ADMIN-AUTH-008 | No hay credenciales Admin hardcodeadas en source; bootstrap sólo por config local/build y fail-closed. | `AdminAuthTest.noHardcodedAdminCredentialDefaultsRemainInSource` |
| REG-ADMIN-AUTH-009 | UI Admin usa login dedicado y no shortcuts ni selector de cuentas. | `AdminAuthTest.adminUiUsesDedicatedLoginWithoutAccountPickerOrTestShortcut` |
| REG-ADMIN-AUTH-010 | Rutas Admin sólo se renderizan bajo guard central `adminSessionIsValid()`. | `AdminAuthTest.adminRoutesAreGuardedByAuthenticatedAdminSession` |
| REG-ADMIN-AUTH-011 | Acciones mutantes críticas fallan cerradas sin sesión Admin válida. | `AdminAuthTest.criticalAdminMutationsFailClosedWithoutAdminSession` |
| REG-ADMIN-AUTH-012 | Acciones mutantes críticas funcionan con sesión Admin válida. | `AdminAuthTest.criticalAdminMutationsWorkWithValidAdminSession` |
| REG-ADMIN-AUTH-013 | Un Panel Rider abierto desde Admin usa contexto explícito y no crea sesión Rider implícita. | `AdminAuthTest.adminCanInspectRiderWorkspaceWithoutCreatingRiderSession` |
| REG-ADMIN-AUTH-014 | Logout Admin corta el contexto Rider abierto desde Admin. | `AdminAuthTest.adminRiderWorkspaceLosesAccessAfterAdminLogout` |
| REG-ADMIN-AUTH-015 | Intentar cambiar de RID en contexto Admin no permite cruzar al panel de otro Rider. | `AdminAuthTest.adminRiderWorkspaceCannotCrossIntoAnotherRider` |
| REG-ADMIN-AUTH-016 | El controlador deriva el Panel Rider desde identidad autenticada o contexto Admin autorizado, nunca desde un RID arbitrario. | `AdminAuthTest.riderDashboardControllerFailsClosedForArbitraryRiderId` |
| REG-PACKAGING-BACKUP-001 | Manifest principal declara `allowBackup=false` y `fullBackupContent=false`. | `PackagingSecurityTest.mainManifestDisablesPlatformBackup` |
| REG-PACKAGING-EXPORT-001 | Toda Activity con intent-filter declara `android:exported` explícitamente. | `PackagingSecurityTest.allIntentFilterActivitiesDeclareExportedExplicitly` |
| REG-PACKAGING-NETSEC-001 | La app declara `networkSecurityConfig`, bloquea cleartext global y no posee overrides inseguros. | `PackagingSecurityTest.networkSecurityConfigFailsClosedForCleartextTraffic` |
| REG-PACKAGING-LOG-001 | No se registran contraseñas, OTP, tokens, API keys ni headers Authorization mediante APIs de log. | `PackagingSecurityTest.sourceDoesNotLogCredentialsTokensOrAuthorizationHeaders` |
| REG-PACKAGING-SECRET-001 | No hay secretos backend hardcodeados en source/build config versionados. | `PackagingSecurityTest.noBackendSecretsAreHardcodedInSourceOrVersionedBuildConfig` |
| REG-PACKAGING-WEB-001 | Si aparece WebView, no puede habilitar file access ni universal access desde file URLs. | `PackagingSecurityTest.webViewConfigurationCannotEnableUnsafeFileAccess` |
| REG-PACKAGING-HTTP-001 | Source principal no introduce endpoints `http://` de aplicación. | `PackagingSecurityTest.mainSourceDoesNotIntroduceCleartextApplicationEndpoints` |
| REG-PACKAGING-DEBUG-001 | Manifest principal no fuerza `android:debuggable=true`. | `PackagingSecurityTest.mainManifestDoesNotForceDebuggableTrue` |
| REG-PACKAGING-SCREENSHOT-001 | No se aplica `FLAG_SECURE` globalmente en la Activity principal. | `PackagingSecurityTest.mainActivityDoesNotApplyGlobalFlagSecure` |
| REG-PACKAGING-NETSEC-002 | La configuración de seguridad no reemplaza el trust store por anchors custom sin pinning. | `PackagingSecurityTest.networkSecurityDoesNotOverrideTrustAnchorsWithoutPinning` |
| REG-DIALOG-001 | Los diálogos críticos usan IME resize y un viewport acotado para mantener acciones accesibles. | `DialogBehaviorTest.dialogPropertiesUseResizeAndCriticalDialogsUseBoundedScrollableContent` |
| REG-DIALOG-002 | Los diálogos editables no se cierran por toque fuera. | `DialogBehaviorTest.editableDialogsCannotDismissByOutsideTap` |
| REG-DIALOG-003 | Si hay cambios pendientes, Back no descarta silenciosamente y exige confirmación. | `DialogBehaviorTest.dirtyEditableDialogRequiresExplicitDiscardConfirmation` |
| REG-DIALOG-004 | Cancelar con cambios pendientes exige confirmación; sin cambios puede cerrar directamente. | `DialogBehaviorTest.cancelButtonRequiresConfirmationOnlyWhenDirty` |
| REG-DIALOG-005 | El diálogo secundario de descarte también usa propiedades fail-closed y mantiene foco de decisión. | `DialogBehaviorTest.discardConfirmationDialogAlsoUsesFailClosedProperties` |
| REG-MAPS-OSS-001 | No hay SDK/API key de Google Maps; MapLibre/OpenFreeMap es el stack de mapas. | `MapSelectionPolicyTest.projectHasNoGoogleMapsSdkOrApiKeyDependency` |
| REG-MAPS-SELECTION-001 | La selección de mapa normaliza dirección humana y coordenadas estructuradas. | `MapSelectionPolicyTest.selectionNormalizesAddressAndKeepsCoordinates` |
| REG-MAPS-TARGET-001 | DELIVERY permite retiro/entrega y SHOPPING retiro previo/comercio/entrega sin cruces de target. | `MapSelectionPolicyTest.targetsRemainServiceSpecific` |
| REG-MAPS-SAME-DELIVERY-001 | “Mismo retiro y entrega” copia dirección y coordenadas estructuradas juntas. | `MapSelectionPolicyTest.sameDeliveryCopiesAddressAndCoordinatesTogether` |
| REG-MAPS-LOCATION-PERMISSION-001 | La ubicación actual sólo se habilita con permiso explícito; denegación falla cerrada sin ubicación ni estado previo. | `MapSelectionPolicyTest.currentLocationFailsClosedWithoutPermissionAndPreservesPreviousState` |
| REG-MAPS-ATTRIBUTION-001 | La UI MapLibre conserva la capa oficial de atribución y ya no renderiza atribución manual duplicada. | `MapSelectionPolicyTest.mapUiUsesOfficialAttributionWithoutManualDuplicate` |
| REG-PACKAGING-MAPS-001 | No se reintroducen dependencias Google Maps/Play Services ni meta-data de API key. | `PackagingSecurityTest.projectPackagingDoesNotReintroduceGoogleMapsSdkOrApiKeys` |
| REG-ORDER-LOCATION-PRESENTATION-001 | Cliente presenta campos estructurados y no expone Pin/URLs legacy. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-PRESENTATION-001 Cliente usa presentacion estructurada sin detail legacy` |
| REG-ORDER-LOCATION-PRESENTATION-002 | Rider presenta campos estructurados y no renderiza `order.detail` crudo ni URLs legacy. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-PRESENTATION-002 Rider no renderiza detail crudo ni URLs legacy` |
| REG-ORDER-LOCATION-PRESENTATION-003 | Admin usa presentación estructurada y controles de consulta read-only. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-PRESENTATION-003 Admin usa presentacion estructurada y controles read only` |
| REG-ORDER-LOCATION-STRUCTURED-001 | El visor obtiene ubicaciones exclusivamente de GeoPoint estructurados según tipo de servicio. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-STRUCTURED-001 ubicaciones salen solo de GeoPoint estructurados` |
| REG-ORDER-LOCATION-READONLY-001 | El visor interno no solicita ubicación/permisos ni invoca mutaciones de pedido, draft, zona o estado. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-READONLY-001 visor no tiene dependencias de mutacion ni permisos de ubicacion` |
| REG-ORDER-LOCATION-POINTS-001 | DELIVERY muestra sólo retiro/entrega existentes; null no genera punto ficticio. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-POINTS-001 DELIVERY muestra solo puntos existentes` |
| REG-ORDER-LOCATION-POINTS-002 | SHOPPING muestra sólo retiro previo/comercio/entrega existentes. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-POINTS-002 SHOPPING muestra retiro previo comercio y entrega existentes` |
| REG-ORDER-LOCATION-LEGACY-001 | Una URL legacy sin GeoPoint no genera ubicación reconstruida. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-LEGACY-001 URL legacy sin GeoPoint no crea ubicacion ficticia` |
| REG-ORDER-LOCATION-CLIENT-AUTH-001 | GeoPoints no alteran el aislamiento del Cliente; pedido ajeno sigue inaccesible. | `CustomerOrderOwnershipTest.REG-ORDER-LOCATION-CLIENT-AUTH-001 GeoPoint no altera aislamiento de Cliente` |
| REG-ORDER-LOCATION-RIDER-AUTH-001 | Sólo la sesión Rider asignada al pedido obtiene ubicaciones internas. | `RiderOrderLocationAuthorizationTest.REG-ORDER-LOCATION-RIDER-AUTH-001 solo Rider autenticado asignado obtiene ubicaciones` |
| REG-ORDER-LOCATION-MAP-001 | Conversión GeoPoint→MapLibre preserva longitude/latitude en orden correcto. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-MAP-001 GeoPoint a MapLibre conserva longitude latitude` |
| REG-ORDER-LOCATION-NOMUTATION-001 | Consultar/abrir ubicaciones no muta LocalOrder. | `OrderLocationPresentationTest.REG-ORDER-LOCATION-NOMUTATION-001 consultar ubicaciones y viewport no muta LocalOrder` |
| REG-ZONE-PRESENTATION-001 | Configuración territorial `OTHER` siempre se presenta después de las zonas operativas. | `ZonePresentationPolicyTest.OTHER siempre queda al final aunque la lista persistida venga desordenada` |
| REG-ZONE-PRESENTATION-002 | La edición Admin itera zonas con la política centralizada de orden. | `ZonePresentationPolicyTest.ConfigScreen usa la politica centralizada de orden de zonas` |
| REG-DIALOG-RIDER-001 | Alta/edición Rider no pierde cambios por Back/Cancelar y mantiene viewport scrolleable. | `DialogInteractionSafetyTest.REG-DIALOG-RIDER-001 alta y edicion Rider protegen cambios pendientes` |
| REG-DIALOG-RIDER-DOCS-001 | Revisión documental Rider protege notas no guardadas y mantiene viewport scrolleable. | `DialogInteractionSafetyTest.REG-DIALOG-RIDER-DOCS-001 revision documental protege notas pendientes` |
| REG-DIALOG-ORDER-EDIT-001 | Editar pedido exige descarte explícito ante cambios pendientes y mantiene viewport acotado. | `DialogInteractionSafetyTest.REG-DIALOG-ORDER-EDIT-001 editar pedido protege cambios pendientes` |
| REG-DIALOG-SHIFT-EDIT-001 | Editar turno exige descarte explícito ante cambios pendientes y mantiene viewport acotado. | `DialogInteractionSafetyTest.REG-DIALOG-SHIFT-EDIT-001 editar turno protege cambios pendientes` |
| REG-DIALOG-LEGAL-PDF-001 | Editor PDF legal protege metadatos/archivo pendientes y mantiene viewport acotado. | `DialogInteractionSafetyTest.REG-DIALOG-LEGAL-PDF-001 editor legal PDF protege cambios pendientes` |
| REG-DIALOG-DATE-001 | Selector de fecha no descarta una selección pendiente por Back y mantiene semántica explícita de acciones. | `DialogInteractionSafetyTest.REG-DIALOG-DATE-001 selector de fecha protege seleccion pendiente` |
| REG-RIDER-NAV-AUTH-001 | Destinos de navegación Rider requieren sesión válida coincidente y pedido asignado al mismo Rider. | `RiderOrderNavigationTest.REG-RIDER-NAV-AUTH-001 destinos requieren sesion Rider coincidente y asignacion propia` |
| REG-RIDER-NAV-DELIVERY-001 | DELIVERY sólo expone botones para retiro/entrega con GeoPoint real. | `RiderOrderNavigationTest.REG-RIDER-NAV-DELIVERY-001 DELIVERY expone solo puntos estructurados existentes` |
| REG-RIDER-NAV-SHOPPING-001 | SHOPPING expone retiro previo/comercio/entrega según GeoPoints existentes. | `RiderOrderNavigationTest.REG-RIDER-NAV-SHOPPING-001 SHOPPING expone retiro previo comercio y entrega existentes` |
| REG-RIDER-NAV-COORD-001 | URI `geo:` conserva lat/lon estructurados sin inversión y no depende del Locale. | `RiderOrderNavigationTest.REG-RIDER-NAV-COORD-001 URI geo conserva latitud longitud y Locale root` |
| REG-RIDER-NAV-READONLY-001 | Construir/abrir navegación no muta pedido, zonas, draft ni estado. | `RiderOrderNavigationTest.REG-RIDER-NAV-READONLY-001 navegacion no muta el pedido` |
| REG-RIDER-NAV-INTENT-001 | Navegación externa usa `ACTION_VIEW` neutral sin package fijo. | `RiderOrderNavigationTest.REG-RIDER-NAV-INTENT-001 Intent es ACTION_VIEW neutral sin package fijo` |
| REG-RIDER-NAV-NOAPP-001 | Ausencia de app compatible devuelve resultado controlado y no crashea. | `RiderOrderNavigationTest.REG-RIDER-NAV-NOAPP-001 ausencia de handler devuelve resultado controlado` |
| REG-RIDER-NAV-NOGMS-001 | Navegación Rider no reintroduce dependencia de Google Maps/GMS. | `RiderOrderNavigationTest.REG-RIDER-NAV-NOGMS-001 navegacion Rider no reintroduce Google Maps ni Play Services` |


La intención es que `testDebugUnitTest` sea la primera barrera automática. CI ejecuta además `assembleDebug` sobre cada push/PR; CodeQL completa el análisis estático. La validación física queda reservada para instalación, permisos reales, UI visual e integraciones de sistema.
