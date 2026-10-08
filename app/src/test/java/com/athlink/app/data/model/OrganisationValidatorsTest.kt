package com.athlink.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrganisationValidatorsTest {

    private val V = OrganisationValidators

    @Test
    fun pan() {
        assertNull(V.pan("AAAPA1234A", true))
        assertNull(V.pan("aaapa1234a", true))
        assertNull(V.pan("", false))
        assertNotNull(V.pan("", true))
        assertNotNull(V.pan("AAAP1234A", false))
        assertNotNull(V.pan("1234567890", false))
    }

    @Test
    fun gstin() {
        assertNull(V.gstin("", false))
        assertNull(V.gstin("garbage", false))
        assertNotNull(V.gstin("", true))
        assertNull(V.gstin("27AAAPA1234A1Z5", true))
        assertNull(V.gstin("27aaapa1234a1z5", true, "AAAPA1234A"))
        assertNotNull(V.gstin("27AAAPA1234A1X5", true))
        assertNotNull(V.gstin("27AAAPA1234A1Z5", true, "BBBPB1234B"))
    }

    @Test
    fun registrationNumbers() {
        assertNull(V.registrationNumber("U92490MH2015PTC123456", true, RegistrationNumberFormat.CIN, "CIN"))
        assertNotNull(V.registrationNumber("U92490MH2015PTC12345", true, RegistrationNumberFormat.CIN, "CIN"))
        assertNull(V.registrationNumber("AAB-1234", true, RegistrationNumberFormat.LLPIN, "LLPIN"))
        assertNotNull(V.registrationNumber("AAB1234", true, RegistrationNumberFormat.LLPIN, "LLPIN"))
        assertNull(V.registrationNumber("", false, RegistrationNumberFormat.CIN, "CIN"))
        assertNotNull(V.registrationNumber("", true, RegistrationNumberFormat.FREE_TEXT, "Society registration number"))
        assertNull(V.registrationNumber("S/12345 of 2015", true, RegistrationNumberFormat.FREE_TEXT, "x"))
        assertNotNull(V.registrationNumber("bad#chars", true, RegistrationNumberFormat.FREE_TEXT, "x"))
    }

    @Test
    fun contactFields() {
        assertNull(V.email("sports@maharashtra.gov.in"))
        assertNotNull(V.email("not-an-email"))
        assertNotNull(V.email(""))
        assertNull(V.phone("+91 22 2345 6789"))
        assertNull(V.phone("022-23456789"))
        assertNotNull(V.phone("12345"))
        assertNotNull(V.phone("98765abc10"))
        assertTrue(V.isFreeMail("someone@Gmail.com"))
        assertFalse(V.isFreeMail("dsys@maharashtra.gov.in"))
    }

    @Test
    fun websiteAndYear() {
        assertNull(V.website(""))
        assertNotNull(V.website("", required = true))
        assertNull(V.website("https://sportsauthority.gov.in/about"))
        assertNull(V.website("asa.org.in"))
        assertNotNull(V.website("not a url"))
        val today = LocalDate.of(2026, 10, 8)
        assertNull(V.yearEstablished("", today))
        assertNull(V.yearEstablished("1950", today))
        assertNotNull(V.yearEstablished("2030", today))
        assertNotNull(V.yearEstablished("19x0", today))
    }

    @Test
    fun locationAndCounts() {
        assertNull(V.pincode("400053", "India"))
        assertNotNull(V.pincode("040053", "India"))
        assertNotNull(V.pincode("40005", "India"))
        assertNull(V.pincode("SW1A 1AA", "United Kingdom"))
        assertNull(V.optionalCount("", "x"))
        assertNull(V.optionalCount("25", "x"))
        assertNotNull(V.optionalCount("-1", "x"))
        assertNotNull(V.optionalCount("lots", "x"))
    }

    @Test
    fun documentsAndLogo() {
        assertNull(V.document("application/pdf", 1024))
        assertNull(V.document("image/png", V.MAX_DOCUMENT_BYTES))
        assertNotNull(V.document("application/pdf", V.MAX_DOCUMENT_BYTES + 1))
        assertNotNull(V.document("application/msword", 1024))
        assertNotNull(V.document(null, 1024))
        assertNotNull(V.document("image/jpeg", 0))
        assertNull(V.logo("image/webp", 1024))
        assertNotNull(V.logo("application/pdf", 1024))
    }
}
