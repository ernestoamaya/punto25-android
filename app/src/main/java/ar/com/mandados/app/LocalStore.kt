package ar.com.mandados.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE)

    fun loadCustomer(): Customer? {
        val name = prefs.getString("customer_name", null) ?: return null
        return Customer(
            name = name,
            areaCode = prefs.getString("customer_area", "") ?: "",
            subscriber = prefs.getString("customer_sub", "") ?: "",
            locality = prefs.getString("customer_locality", "") ?: "",
            accountId = prefs.getString("customer_account_id", "") ?: "",
            googleEmail = prefs.getString("customer_google_email", "") ?: "",
            googleVerified = prefs.getBoolean("customer_google_verified", false),
            whatsappVerified = prefs.getBoolean("customer_whatsapp_verified", false),
            whatsappVerifiedAt = prefs.getString("customer_whatsapp_verified_at", null)
        )
    }

    fun saveCustomer(c: Customer) {
        prefs.edit()
            .putString("customer_name", c.name)
            .putString("customer_area", c.areaCode)
            .putString("customer_sub", c.subscriber)
            .putString("customer_locality", c.locality)
            .putString("customer_account_id", c.accountId)
            .putString("customer_google_email", c.googleEmail)
            .putBoolean("customer_google_verified", c.googleVerified)
            .putBoolean("customer_whatsapp_verified", c.whatsappVerified)
            .putString("customer_whatsapp_verified_at", c.whatsappVerifiedAt)
            .apply()
    }

    fun clearCustomer() {
        prefs.edit()
            .remove("customer_name").remove("customer_area").remove("customer_sub").remove("customer_locality")
            .remove("customer_account_id").remove("customer_google_email").remove("customer_google_verified")
            .remove("customer_whatsapp_verified").remove("customer_whatsapp_verified_at")
            .apply()
    }

    fun loadConfig(): AdminConfig {
        val base = AdminConfig()
        val zones = runCatching {
            val raw = prefs.getString("zones_json", null)
            if (raw.isNullOrBlank()) error("legacy")
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val z = arr.getJSONObject(i)
                    add(
                        ZoneConfig(
                            id = z.getString("id"),
                            name = z.optString("name", ""),
                            description = z.optString("description", ""),
                            category = z.optString("category", "OTRAS"),
                            price = z.optInt("price", 0),
                            enabled = z.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrElse {
            base.zones.map { z ->
                z.copy(
                    price = prefs.getInt("zone_${z.id}_price", z.price),
                    enabled = prefs.getBoolean("zone_${z.id}_enabled", z.enabled)
                )
            }
        }
        val payment = runCatching {
            val o = JSONObject(prefs.getString("payment_config", "{}") ?: "{}")
            PaymentConfig(
                cashEnabled = o.optBoolean("cashEnabled", true),
                riderTransferEnabled = o.optBoolean("riderTransferEnabled", true),
                qrEnabled = o.optBoolean("qrEnabled", false),
                onlineCheckoutEnabled = o.optBoolean("onlineCheckoutEnabled", false),
                transferProofRequired = o.optBoolean("transferProofRequired", true),
                riderConfirmationRequired = o.optBoolean("riderConfirmationRequired", true),
                centralAlias = o.optString("centralAlias", ""),
                qrProvider = o.optString("qrProvider", "Mercado Pago"),
                qrMode = o.optString("qrMode", "DESACTIVADO"),
                tipsEnabled = o.optBoolean("tipsEnabled", true),
                tipSuggestions = o.optJSONArray("tipSuggestions")?.toIntList() ?: listOf(500, 1000, 2000),
                customTipEnabled = o.optBoolean("customTipEnabled", true)
            )
        }.getOrDefault(PaymentConfig())
        val legal = runCatching {
            val o = JSONObject(prefs.getString("legal_profile", "{}") ?: "{}")
            LegalProfile(
                businessName = o.optString("businessName", "Punto25"),
                legalName = o.optString("legalName", ""),
                taxId = o.optString("taxId", ""),
                legalAddress = o.optString("legalAddress", ""),
                commercialAddress = o.optString("commercialAddress", ""),
                city = o.optString("city", "25 de Mayo"),
                province = o.optString("province", "Buenos Aires"),
                country = o.optString("country", "Argentina"),
                supportEmail = o.optString("supportEmail", ""),
                privacyEmail = o.optString("privacyEmail", ""),
                supportWhatsapp = o.optString("supportWhatsapp", ""),
                serviceArea = o.optString("serviceArea", "25 de Mayo, Provincia de Buenos Aires")
            )
        }.getOrDefault(LegalProfile())

        return base.copy(
            acceptingOrders = prefs.getBoolean("accepting", true),
            closedMessage = prefs.getString("closed_message", base.closedMessage) ?: base.closedMessage,
            maxPurchaseAmount = prefs.getInt("max_purchase", 50000),
            maxWeightKg = prefs.getInt("max_weight", 5),
            rainEnabled = prefs.getBoolean("rain_enabled", false),
            rainAmount = prefs.getInt("rain_amount", 0),
            prePickupMode = enumOrDefault(prefs.getString("pre_mode", null), PrePickupMode.OFF),
            prePickupValue = prefs.getInt("pre_value", 0),
            whatsappReceiver = prefs.getString("whatsapp_receiver", "") ?: "",
            supportWhatsapp = prefs.getString("support_whatsapp", legal.supportWhatsapp) ?: legal.supportWhatsapp,
            themeMode = enumOrDefault(prefs.getString("theme_mode", null), ThemeMode.SYSTEM),
            operationMode = enumOrDefault(prefs.getString("operation_mode", null), OperationMode.MULTI_RIDER),
            defaultMaxConcurrentOrders = prefs.getInt("default_max_concurrent_orders", 2).coerceAtLeast(1),
            paymentConfig = payment,
            legalProfile = legal,
            zones = zones
        )
    }

    fun saveConfig(c: AdminConfig) {
        val paymentJson = JSONObject().apply {
            put("cashEnabled", c.paymentConfig.cashEnabled)
            put("riderTransferEnabled", c.paymentConfig.riderTransferEnabled)
            put("qrEnabled", c.paymentConfig.qrEnabled)
            put("onlineCheckoutEnabled", c.paymentConfig.onlineCheckoutEnabled)
            put("transferProofRequired", c.paymentConfig.transferProofRequired)
            put("riderConfirmationRequired", c.paymentConfig.riderConfirmationRequired)
            put("centralAlias", c.paymentConfig.centralAlias)
            put("qrProvider", c.paymentConfig.qrProvider)
            put("qrMode", c.paymentConfig.qrMode)
            put("tipsEnabled", c.paymentConfig.tipsEnabled)
            put("tipSuggestions", JSONArray(c.paymentConfig.tipSuggestions))
            put("customTipEnabled", c.paymentConfig.customTipEnabled)
        }
        val legalJson = JSONObject().apply {
            put("businessName", c.legalProfile.businessName)
            put("legalName", c.legalProfile.legalName)
            put("taxId", c.legalProfile.taxId)
            put("legalAddress", c.legalProfile.legalAddress)
            put("commercialAddress", c.legalProfile.commercialAddress)
            put("city", c.legalProfile.city)
            put("province", c.legalProfile.province)
            put("country", c.legalProfile.country)
            put("supportEmail", c.legalProfile.supportEmail)
            put("privacyEmail", c.legalProfile.privacyEmail)
            put("supportWhatsapp", c.legalProfile.supportWhatsapp)
            put("serviceArea", c.legalProfile.serviceArea)
        }
        val e = prefs.edit()
            .putBoolean("accepting", c.acceptingOrders)
            .putString("closed_message", c.closedMessage)
            .putInt("max_purchase", c.maxPurchaseAmount)
            .putInt("max_weight", c.maxWeightKg)
            .putBoolean("rain_enabled", c.rainEnabled)
            .putInt("rain_amount", c.rainAmount)
            .putString("pre_mode", c.prePickupMode.name)
            .putInt("pre_value", c.prePickupValue)
            .putString("whatsapp_receiver", c.whatsappReceiver)
            .putString("support_whatsapp", c.supportWhatsapp)
            .putString("theme_mode", c.themeMode.name)
            .putString("operation_mode", c.operationMode.name)
            .putInt("default_max_concurrent_orders", c.defaultMaxConcurrentOrders)
            .putString("payment_config", paymentJson.toString())
            .putString("legal_profile", legalJson.toString())
        e.putString("zones_json", JSONArray().apply {
            c.zones.forEach { z ->
                put(JSONObject().apply {
                    put("id", z.id)
                    put("name", z.name)
                    put("description", z.description)
                    put("category", z.category)
                    put("price", z.price)
                    put("enabled", z.enabled)
                })
            }
        }.toString())
        e.apply()
    }

    fun loadOrders(): List<LocalOrder> {
        val raw = prefs.getString("orders", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val phone = o.optString("customerPhone", "")
                    val type = enumOrDefault(o.optString("serviceType", null), ServiceType.DELIVERY)
                    add(
                        LocalOrder(
                            id = o.getString("id"),
                            createdAt = o.getString("createdAt"),
                            serviceType = type,
                            category = enumOrDefault(
                                o.optString("category", null),
                                if (type == ServiceType.SHOPPING) ServiceCategory.PURCHASE else ServiceCategory.ERRAND
                            ),
                            operationMode = enumOrDefault(o.optString("operationMode", null), OperationMode.MULTI_RIDER),
                            status = enumOrDefault(o.optString("status", null), OrderStatus.PENDING),
                            customerName = o.optString("customerName", ""),
                            customerPhone = phone,
                            detail = o.optString("detail", ""),
                            baseAmount = o.optIntOrNull("baseAmount"),
                            baseZoneName = o.optStringOrNull("baseZoneName"),
                            prePickupAmount = o.optIntOrNull("prePickupAmount"),
                            rainAmount = o.optIntOrNull("rainAmount"),
                            totalAmount = o.optIntOrNull("totalAmount"),
                            whatsappMessage = o.optString("whatsappMessage", ""),
                            assignedRiderId = o.optStringOrNull("assignedRiderId"),
                            originLocation = o.optGeoPoint("originLocation"),
                            destinationLocation = o.optGeoPoint("destinationLocation"),
                            storeLocation = o.optGeoPoint("storeLocation"),
                            prePickupLocation = o.optGeoPoint("prePickupLocation"),
                            customerId = o.optString("customerId", "").ifBlank {
                                val digits = phone.filter(Char::isDigit)
                                if (digits.isBlank()) "" else "CLI-$digits"
                            },
                            events = o.optOrderEvents(),
                            originAddress = o.optString("originAddress", ""),
                            originReference = o.optString("originReference", ""),
                            originZoneId = o.optString("originZoneId", ""),
                            destinationAddress = o.optString("destinationAddress", ""),
                            destinationReference = o.optString("destinationReference", ""),
                            destinationZoneId = o.optString("destinationZoneId", ""),
                            carriedItem = o.optString("carriedItem", ""),
                            instructionType = enumOrDefault(o.optString("instructionType", null), PurchaseInstructionType.IN_APP),
                            purchaseDescription = o.optString("purchaseDescription", ""),
                            purchaseMaxAmount = o.optInt("purchaseMaxAmount", 0),
                            storeName = o.optString("storeName", ""),
                            storeAddress = o.optString("storeAddress", ""),
                            storeZoneId = o.optString("storeZoneId", ""),
                            purchasePayment = enumOrDefault(o.optString("purchasePayment", null), PurchasePaymentMethod.DIRECT_TO_STORE),
                            prePickupAddress = o.optString("prePickupAddress", ""),
                            prePickupReference = o.optString("prePickupReference", ""),
                            prePickupZoneId = o.optString("prePickupZoneId", ""),
                            sameDeliveryAsPrePickup = o.optBoolean("sameDeliveryAsPrePickup", false),
                            deliveryPayment = enumOrDefault(o.optString("deliveryPayment", null), DeliveryPaymentMethod.CASH),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveOrders(orders: List<LocalOrder>) {
        val arr = JSONArray()
        orders.forEach { o ->
            arr.put(JSONObject().apply {
                put("id", o.id)
                put("createdAt", o.createdAt)
                put("serviceType", o.serviceType.name)
                put("category", o.category.name)
                put("operationMode", o.operationMode.name)
                put("status", o.status.name)
                put("customerName", o.customerName)
                put("customerPhone", o.customerPhone)
                put("customerId", o.customerId)
                put("detail", o.detail)
                put("baseAmount", o.baseAmount ?: JSONObject.NULL)
                put("baseZoneName", o.baseZoneName ?: JSONObject.NULL)
                put("prePickupAmount", o.prePickupAmount ?: JSONObject.NULL)
                put("rainAmount", o.rainAmount ?: JSONObject.NULL)
                put("totalAmount", o.totalAmount ?: JSONObject.NULL)
                put("whatsappMessage", o.whatsappMessage)
                put("assignedRiderId", o.assignedRiderId ?: JSONObject.NULL)
                putGeoPoint("originLocation", o.originLocation)
                putGeoPoint("destinationLocation", o.destinationLocation)
                putGeoPoint("storeLocation", o.storeLocation)
                putGeoPoint("prePickupLocation", o.prePickupLocation)
                put("originAddress", o.originAddress)
                put("originReference", o.originReference)
                put("originZoneId", o.originZoneId)
                put("destinationAddress", o.destinationAddress)
                put("destinationReference", o.destinationReference)
                put("destinationZoneId", o.destinationZoneId)
                put("carriedItem", o.carriedItem)
                put("instructionType", o.instructionType.name)
                put("purchaseDescription", o.purchaseDescription)
                put("purchaseMaxAmount", o.purchaseMaxAmount)
                put("storeName", o.storeName)
                put("storeAddress", o.storeAddress)
                put("storeZoneId", o.storeZoneId)
                put("purchasePayment", o.purchasePayment.name)
                put("prePickupAddress", o.prePickupAddress)
                put("prePickupReference", o.prePickupReference)
                put("prePickupZoneId", o.prePickupZoneId)
                put("sameDeliveryAsPrePickup", o.sameDeliveryAsPrePickup)
                put("deliveryPayment", o.deliveryPayment.name)
                put("notes", o.notes)
                put("events", JSONArray().apply {
                    o.events.forEach { event ->
                        put(JSONObject().apply {
                            put("type", event.type.name)
                            put("at", event.at)
                            put("status", event.status?.name ?: JSONObject.NULL)
                            put("riderId", event.riderId ?: JSONObject.NULL)
                            put("note", event.note ?: JSONObject.NULL)
                            put("actor", event.actor ?: JSONObject.NULL)
                        })
                    }
                })
            })
        }
        prefs.edit().putString("orders", arr.toString()).apply()
    }

    fun loadRiders(): List<RiderProfile> {
        val raw = prefs.getString("riders", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    val docsObj = r.optJSONObject("documents")
                    val documents = RiderDocuments(
                        dniFrontUri = docsObj?.optStringOrNull("dniFrontUri"),
                        dniBackUri = docsObj?.optStringOrNull("dniBackUri"),
                        motorcyclePlateUri = docsObj?.optStringOrNull("motorcyclePlateUri"),
                        driverLicenseFrontUri = docsObj?.optStringOrNull("driverLicenseFrontUri"),
                        driverLicenseBackUri = docsObj?.optStringOrNull("driverLicenseBackUri"),
                        vehicleCardUri = docsObj?.optStringOrNull("vehicleCardUri"),
                        insuranceCardUri = docsObj?.optStringOrNull("insuranceCardUri")
                    )
                    val reviewsObj = r.optJSONObject("documentReviews")
                    val notesObj = r.optJSONObject("documentNotes")
                    val reviews = RiderDocumentKey.entries.associateWith { key ->
                        val fallback = if (documents.uriFor(key).isNullOrBlank()) DocumentReviewStatus.NOT_UPLOADED else DocumentReviewStatus.PENDING
                        enumOrDefault(reviewsObj?.optString(key.name, null), fallback)
                    }
                    val notes = RiderDocumentKey.entries.associateWith { key -> notesObj?.optString(key.name, "") ?: "" }
                        .filterValues { it.isNotBlank() }

                    add(
                        RiderProfile(
                            id = r.getString("id"),
                            name = r.optString("name", ""),
                            phone = r.optString("phone", ""),
                            birthDate = r.optString("birthDate", ""),
                            vehicleType = enumOrDefault(r.optString("vehicleType", null), VehicleType.MOTORCYCLE),
                            address = r.optString("address", ""),
                            transferAlias = r.optString("transferAlias", ""),
                            maxConcurrentOrdersOverride = r.optIntOrNull("maxConcurrentOrdersOverride"),
                            documents = documents,
                            approvalStatus = if (r.has("approvalStatus")) enumOrDefault(r.optString("approvalStatus", null), RiderApprovalStatus.PENDING)
                            else RiderApprovalStatus.APPROVED,
                            documentReviews = reviews,
                            documentNotes = notes,
                            active = r.optBoolean("active", true),
                            available = r.optBoolean("available", true),
                            availableUntilAt = r.optStringOrNull("availableUntilAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveRiders(riders: List<RiderProfile>) {
        val arr = JSONArray()
        riders.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("phone", r.phone)
                put("birthDate", r.birthDate)
                put("vehicleType", r.vehicleType.name)
                put("address", r.address)
                put("transferAlias", r.transferAlias)
                put("maxConcurrentOrdersOverride", r.maxConcurrentOrdersOverride ?: JSONObject.NULL)
                put("approvalStatus", r.approvalStatus.name)
                put("documents", JSONObject().apply {
                    put("dniFrontUri", r.documents.dniFrontUri ?: JSONObject.NULL)
                    put("dniBackUri", r.documents.dniBackUri ?: JSONObject.NULL)
                    put("motorcyclePlateUri", r.documents.motorcyclePlateUri ?: JSONObject.NULL)
                    put("driverLicenseFrontUri", r.documents.driverLicenseFrontUri ?: JSONObject.NULL)
                    put("driverLicenseBackUri", r.documents.driverLicenseBackUri ?: JSONObject.NULL)
                    put("vehicleCardUri", r.documents.vehicleCardUri ?: JSONObject.NULL)
                    put("insuranceCardUri", r.documents.insuranceCardUri ?: JSONObject.NULL)
                })
                put("documentReviews", JSONObject().apply { RiderDocumentKey.entries.forEach { key -> put(key.name, r.reviewFor(key).name) } })
                put("documentNotes", JSONObject().apply { r.documentNotes.forEach { (key, value) -> put(key.name, value) } })
                put("active", r.active)
                put("available", r.available)
                put("availableUntilAt", r.availableUntilAt ?: JSONObject.NULL)
            })
        }
        prefs.edit().putString("riders", arr.toString()).apply()
    }

    fun loadShifts(): List<ShiftTemplate> = readArray("shift_templates") { o ->
        ShiftTemplate(
            id = o.getString("id"),
            dayOfWeek = o.optInt("dayOfWeek", 1),
            startTime = o.optString("startTime", "18:00"),
            endTime = o.optString("endTime", "00:00"),
            capacity = o.optInt("capacity", 1).coerceAtLeast(1),
            enabled = o.optBoolean("enabled", true),
            specificDate = o.optStringOrNull("specificDate")
        )
    }

    fun saveShifts(items: List<ShiftTemplate>) = saveArray("shift_templates", items) { s ->
        JSONObject().apply {
            put("id", s.id); put("dayOfWeek", s.dayOfWeek); put("startTime", s.startTime)
            put("endTime", s.endTime); put("capacity", s.capacity); put("enabled", s.enabled)
            put("specificDate", s.specificDate ?: JSONObject.NULL)
        }
    }

    fun loadRiderInvitations(): List<RiderInvitation> = readArray("rider_invitations") { o ->
        RiderInvitation(
            id = o.getString("id"),
            code = o.getString("code"),
            riderId = o.getString("riderId"),
            phone = o.optString("phone", ""),
            createdAt = o.optString("createdAt", ""),
            expiresAt = o.optString("expiresAt", ""),
            status = enumOrDefault(o.optString("status", null), RiderInvitationStatus.PENDING),
            usedAt = o.optStringOrNull("usedAt"),
            resetAccess = o.optBoolean("resetAccess", false)
        )
    }

    fun saveRiderInvitations(items: List<RiderInvitation>) = saveArray("rider_invitations", items) { i ->
        JSONObject().apply {
            put("id", i.id); put("code", i.code); put("riderId", i.riderId); put("phone", i.phone)
            put("createdAt", i.createdAt); put("expiresAt", i.expiresAt); put("status", i.status.name)
            put("usedAt", i.usedAt ?: JSONObject.NULL); put("resetAccess", i.resetAccess)
        }
    }

    fun loadRiderCredentials(): List<RiderCredential> = readArray("rider_credentials") { o ->
        RiderCredential(
            riderId = o.getString("riderId"),
            saltBase64 = o.getString("saltBase64"),
            passwordHashBase64 = o.getString("passwordHashBase64"),
            iterations = o.optInt("iterations", 120000),
            updatedAt = o.optString("updatedAt", ""),
            mustChangePassword = o.optBoolean("mustChangePassword", false)
        )
    }

    fun saveRiderCredentials(items: List<RiderCredential>) = saveArray("rider_credentials", items) { c ->
        JSONObject().apply {
            put("riderId", c.riderId); put("saltBase64", c.saltBase64)
            put("passwordHashBase64", c.passwordHashBase64); put("iterations", c.iterations)
            put("updatedAt", c.updatedAt); put("mustChangePassword", c.mustChangePassword)
        }
    }

    fun loadShiftReservations(): List<RiderShiftReservation> = readArray("shift_reservations") { o ->
        RiderShiftReservation(
            id = o.getString("id"),
            riderId = o.getString("riderId"),
            shiftTemplateId = o.getString("shiftTemplateId"),
            serviceDate = o.getString("serviceDate"),
            joinedAt = o.getString("joinedAt"),
            status = enumOrDefault(o.optString("status", null), ShiftReservationStatus.RESERVED),
            cancelledAt = o.optStringOrNull("cancelledAt"),
            cancellationCount = o.optInt("cancellationCount", 0),
            lastCancelledAt = o.optStringOrNull("lastCancelledAt"),
            blockedRejoin = o.optBoolean("blockedRejoin", false)
        )
    }

    fun saveShiftReservations(items: List<RiderShiftReservation>) = saveArray("shift_reservations", items) { r ->
        JSONObject().apply {
            put("id", r.id); put("riderId", r.riderId); put("shiftTemplateId", r.shiftTemplateId)
            put("serviceDate", r.serviceDate); put("joinedAt", r.joinedAt); put("status", r.status.name)
            put("cancelledAt", r.cancelledAt ?: JSONObject.NULL)
            put("cancellationCount", r.cancellationCount)
            put("lastCancelledAt", r.lastCancelledAt ?: JSONObject.NULL)
            put("blockedRejoin", r.blockedRejoin)
        }
    }

    fun loadShiftAuditEvents(): List<ShiftAuditEvent> = readArray("shift_audit_events") { o ->
        ShiftAuditEvent(
            id = o.getString("id"),
            reservationId = o.getString("reservationId"),
            shiftTemplateId = o.getString("shiftTemplateId"),
            riderId = o.getString("riderId"),
            serviceDate = o.getString("serviceDate"),
            type = enumOrDefault(o.optString("type", null), ShiftEventType.RIDER_JOINED),
            at = o.optString("at", ""),
            actor = o.optString("actor", "")
        )
    }

    fun saveShiftAuditEvents(items: List<ShiftAuditEvent>) = saveArray("shift_audit_events", items) { e ->
        JSONObject().apply {
            put("id", e.id); put("reservationId", e.reservationId); put("shiftTemplateId", e.shiftTemplateId)
            put("riderId", e.riderId); put("serviceDate", e.serviceDate); put("type", e.type.name)
            put("at", e.at); put("actor", e.actor)
        }
    }

    fun loadLegalDocuments(): List<LegalDocument> = readArray("legal_documents") { o ->
        LegalDocument(
            id = o.getString("id"),
            type = enumOrDefault(o.optString("type", null), LegalDocumentType.TERMS),
            version = o.optString("version", "1.0"),
            title = o.optString("title", ""),
            content = o.optString("content", ""),
            effectiveDate = o.optString("effectiveDate", ""),
            published = o.optBoolean("published", false),
            requireAcceptance = o.optBoolean("requireAcceptance", false),
            updatedAt = o.optString("updatedAt", ""),
            fileUri = o.optStringOrNull("fileUri"),
            fileName = o.optStringOrNull("fileName"),
            fileSha256 = o.optStringOrNull("fileSha256")
        )
    }

    fun saveLegalDocuments(items: List<LegalDocument>) = saveArray("legal_documents", items) { d ->
        JSONObject().apply {
            put("id", d.id); put("type", d.type.name); put("version", d.version); put("title", d.title)
            put("content", d.content); put("effectiveDate", d.effectiveDate); put("published", d.published)
            put("requireAcceptance", d.requireAcceptance); put("updatedAt", d.updatedAt)
            put("fileUri", d.fileUri ?: JSONObject.NULL); put("fileName", d.fileName ?: JSONObject.NULL)
            put("fileSha256", d.fileSha256 ?: JSONObject.NULL)
        }
    }

    fun loadLegalAcceptances(): List<LegalAcceptance> = readArray("legal_acceptances") { o ->
        LegalAcceptance(
            customerId = o.getString("customerId"),
            documentId = o.getString("documentId"),
            documentType = enumOrDefault(o.optString("documentType", null), LegalDocumentType.TERMS),
            version = o.optString("version", ""),
            acceptedAt = o.optString("acceptedAt", "")
        )
    }

    fun saveLegalAcceptances(items: List<LegalAcceptance>) = saveArray("legal_acceptances", items) { a ->
        JSONObject().apply {
            put("customerId", a.customerId)
            put("documentId", a.documentId)
            put("documentType", a.documentType.name)
            put("version", a.version)
            put("acceptedAt", a.acceptedAt)
        }
    }

    fun loadPayments(): List<PaymentRecord> = readArray("payments") { o ->
        PaymentRecord(
            id = o.getString("id"),
            orderId = o.getString("orderId"),
            riderId = o.optStringOrNull("riderId"),
            channel = enumOrDefault(o.optString("channel", null), PaymentChannel.CASH),
            expectedAmount = o.optInt("expectedAmount", 0),
            status = enumOrDefault(o.optString("status", null), PaymentStatus.PENDING),
            proofUri = o.optStringOrNull("proofUri"),
            provider = o.optStringOrNull("provider"),
            providerPaymentId = o.optStringOrNull("providerPaymentId"),
            createdAt = o.getString("createdAt"),
            updatedAt = o.getString("updatedAt")
        )
    }

    fun savePayments(items: List<PaymentRecord>) = saveArray("payments", items) { p ->
        JSONObject().apply {
            put("id", p.id); put("orderId", p.orderId); put("riderId", p.riderId ?: JSONObject.NULL)
            put("channel", p.channel.name); put("expectedAmount", p.expectedAmount); put("status", p.status.name)
            put("proofUri", p.proofUri ?: JSONObject.NULL); put("provider", p.provider ?: JSONObject.NULL)
            put("providerPaymentId", p.providerPaymentId ?: JSONObject.NULL); put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
        }
    }

    fun loadRatings(): List<OrderRating> = readArray("ratings") { o ->
        OrderRating(
            orderId = o.getString("orderId"),
            customerId = o.getString("customerId"),
            riderId = o.getString("riderId"),
            stars = o.optInt("stars", 5).coerceIn(1, 5),
            tags = o.optJSONArray("tags")?.toStringList() ?: emptyList(),
            comment = o.optString("comment", ""),
            tipAmount = o.optInt("tipAmount", 0),
            tipStatus = enumOrDefault(o.optString("tipStatus", null), TipStatus.NONE),
            createdAt = o.getString("createdAt")
        )
    }

    fun saveRatings(items: List<OrderRating>) = saveArray("ratings", items) { r ->
        JSONObject().apply {
            put("orderId", r.orderId); put("customerId", r.customerId); put("riderId", r.riderId)
            put("stars", r.stars); put("tags", JSONArray(r.tags)); put("comment", r.comment)
            put("tipAmount", r.tipAmount); put("tipStatus", r.tipStatus.name); put("createdAt", r.createdAt)
        }
    }

    private fun JSONObject.optOrderEvents(): List<OrderEvent> {
        val arr = optJSONArray("events") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                add(
                    OrderEvent(
                        type = enumOrDefault(e.optString("type", null), OrderEventType.CREATED),
                        at = e.optString("at", ""),
                        status = e.optStringOrNull("status")?.let { enumOrDefault(it, OrderStatus.PENDING) },
                        riderId = e.optStringOrNull("riderId"),
                        note = e.optStringOrNull("note"),
                        actor = e.optStringOrNull("actor")
                    )
                )
            }
        }
    }

    private inline fun <T> readArray(key: String, crossinline mapper: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(mapper(arr.getJSONObject(i)))
            }
        }.getOrDefault(emptyList())
    }

    private inline fun <T> saveArray(key: String, items: List<T>, crossinline mapper: (T) -> JSONObject) {
        val arr = JSONArray()
        items.forEach { arr.put(mapper(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun JSONArray.toIntList(): List<Int> = buildList { for (i in 0 until length()) add(optInt(i)) }
    private fun JSONArray.toStringList(): List<String> = buildList { for (i in 0 until length()) add(optString(i)) }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optGeoPoint(key: String): GeoPoint? {
        val obj = optJSONObject(key) ?: return null
        if (!obj.has("latitude") || !obj.has("longitude")) return null
        return GeoPoint(obj.optDouble("latitude"), obj.optDouble("longitude"))
    }

    private fun JSONObject.putGeoPoint(key: String, point: GeoPoint?) {
        put(key, point?.let {
            JSONObject().apply { put("latitude", it.latitude); put("longitude", it.longitude) }
        } ?: JSONObject.NULL)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        runCatching { enumValueOf<T>(raw ?: "") }.getOrDefault(default)
}
