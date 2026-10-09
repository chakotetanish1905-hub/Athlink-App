package com.athlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Date

class PlayerProfileFormTest {

    private val today = LocalDate.of(2026, 10, 9)

    private fun completeForm() = PlayerProfileForm(
        fullName = "Asha Patel",
        dateOfBirth = "2010-05-05",
        country = "India",
        state = "Gujarat",
        city = "Surat",
        primarySport = "Cricket",
        secondarySports = listOf("Football"),
        sportProfile = mapOf("playerRole" to "Bowler", "battingStyle" to "Left Hand"),
        skillLevel = SkillLevel.INTERMEDIATE,
        goals = listOf(PlayerGoal.FIND_COACH, PlayerGoal.JOIN_TOURNAMENTS),
        coachingFormat = CoachingFormat.GROUP,
        trainingTime = TrainingTime.EVENING,
        travelDistance = TravelDistance.KM_10,
        consentAlreadyRecorded = true,
        termsAccepted = true,
        privacyAccepted = true
    )

    // ── Signup form ─────────────────────────────────────────────────────

    @Test
    fun signupRequiresTermsAndPrivacy() {
        val ok = PlayerSignupForm("Asha Patel", "asha@x.in", "secret1", "secret1", termsAccepted = true, privacyAccepted = true)
        assertTrue(ok.validate().isEmpty())
        assertEquals(setOf(PlayerSignupField.TERMS), ok.copy(termsAccepted = false).validate().keys)
        assertEquals(setOf(PlayerSignupField.PRIVACY), ok.copy(privacyAccepted = false).validate().keys)
    }

    @Test
    fun signupValidatesEachField() {
        val errors = PlayerSignupForm("", "bad", "", "x").validate()
        assertEquals(PlayerSignupField.entries.toSet(), errors.keys)
    }

    @Test
    fun signupNormalisesEmailAndName() {
        val f = PlayerSignupForm("  Asha   Patel ", "  Asha@X.IN ")
        assertEquals("asha@x.in", f.normalisedEmail)
        assertEquals("Asha Patel", f.normalisedName)
    }

    // ── Step validation ─────────────────────────────────────────────────

    @Test
    fun completeFormIsValid() {
        assertTrue(completeForm().validateAll(today).isEmpty())
        assertNull(completeForm().firstInvalidStep(today))
    }

    @Test
    fun requiredFieldsPerStep() {
        val empty = PlayerProfileForm()
        assertTrue(empty.validate(PlayerOnboardingStep.ABOUT, today).keys.containsAll(
            listOf(PlayerField.FULL_NAME, PlayerField.DATE_OF_BIRTH, PlayerField.STATE, PlayerField.CITY)))
        assertEquals(setOf(PlayerField.PRIMARY_SPORT), empty.validate(PlayerOnboardingStep.SPORT, today).keys)
        assertTrue(empty.validate(PlayerOnboardingStep.GAME, today).isEmpty())          // all optional
        assertEquals(setOf(PlayerField.SKILL_LEVEL), empty.validate(PlayerOnboardingStep.LEVEL, today).keys)
        assertEquals(setOf(PlayerField.GOALS), empty.validate(PlayerOnboardingStep.GOALS, today).keys)
        assertEquals(
            setOf(PlayerField.COACHING_FORMAT, PlayerField.TRAINING_TIME, PlayerField.TRAVEL_DISTANCE),
            empty.validate(PlayerOnboardingStep.TRAINING, today).keys
        )
        assertEquals(PlayerOnboardingStep.ABOUT, empty.firstInvalidStep(today))
    }

    @Test
    fun consentOnlyRequiredWhenNotAlreadyRecorded() {
        val legacy = completeForm().copy(consentAlreadyRecorded = false, termsAccepted = false, privacyAccepted = false)
        assertEquals(setOf(PlayerField.TERMS, PlayerField.PRIVACY), legacy.validate(PlayerOnboardingStep.READY, today).keys)
        assertTrue(legacy.copy(termsAccepted = true, privacyAccepted = true).validate(PlayerOnboardingStep.READY, today).isEmpty())
    }

    @Test
    fun resumesAtFirstIncompleteStep() {
        val f = completeForm().copy(goals = emptyList())
        assertEquals(PlayerOnboardingStep.GOALS, f.firstInvalidStep(today))
    }

    // ── Edits keep the form consistent ──────────────────────────────────

    @Test
    fun changingPrimarySportDropsOldAnswersAndDuplicates() {
        val f = completeForm().withPrimarySport("Football")
        assertEquals("Football", f.primarySport)
        assertTrue(f.secondarySports.isEmpty())             // Football removed from secondary
        assertTrue(f.sportProfile.isEmpty())                // cricket answers dropped
        val g = f.withSportAnswer("position", "Midfielder")
        assertEquals(mapOf("position" to "Midfielder"), g.sportProfile)
        assertEquals(emptyMap<String, String>(), g.withSportAnswer("position", null).sportProfile)
    }

    @Test
    fun secondarySportsAreCappedAndExcludePrimary() {
        var f = completeForm().copy(secondarySports = emptyList())
        assertEquals(f, f.toggleSecondarySport("Cricket"))  // primary can't be secondary
        listOf("Football", "Tennis", "Hockey", "Chess", "Boxing", "Kabaddi").forEach { f = f.toggleSecondarySport(it) }
        assertEquals(PlayerValidators.MAX_SECONDARY_SPORTS, f.secondarySports.size)
        assertFalse("Kabaddi" in f.secondarySports)
    }

    @Test
    fun answersForAnotherSportAreInvalid() {
        val f = completeForm().copy(sportProfile = mapOf("position" to "Goalkeeper"))  // football key on cricket
        assertTrue(PlayerField.SPORT_PROFILE in f.validate(PlayerOnboardingStep.GAME, today))
    }

    @Test
    fun switchingCountryClearsIndianState() {
        val abroad = completeForm().withCountry("Nepal")
        assertEquals("", abroad.state)
        assertEquals("International", abroad.copy(state = "Bagmati").region)
        assertEquals("West India", completeForm().region)
    }

    // ── Firestore maps: public vs private ───────────────────────────────

    @Test
    fun publicMapNeverContainsPrivateData() {
        val fields = completeForm().copy(phoneNumber = "9876543210", gender = Gender.FEMALE).toPlayerFields("uid1")
        listOf("email", "dateOfBirth", "phoneNumber", "gender", "password", "notificationPreferences", "profileStatus", "role")
            .forEach { assertFalse("$it must not be public", it in fields) }
        assertEquals("uid1", fields["uid"])
        assertEquals("Asha Patel", fields["displayName"])
        assertEquals("West India", fields["region"])
        assertEquals("INTERMEDIATE", fields["skillLevel"])
        assertEquals(listOf("FIND_COACH", "JOIN_TOURNAMENTS"), fields["goals"])
        assertEquals(mapOf("format" to "GROUP", "trainingTime" to "EVENING", "maxDistanceKm" to 10), fields["coachingPreferences"])
    }

    @Test
    fun privateMapHoldsDobAndNeverAPassword() {
        val fields = completeForm().copy(phoneNumber = " 98765 43210 ").toUserFields()
        assertEquals("2010-05-05", fields["dateOfBirth"])
        assertEquals("98765 43210", fields["phoneNumber"])
        assertFalse("password" in fields)
        assertFalse("age" in fields)                        // age is always derived
    }

    @Test
    fun anyDistanceIsStoredAsZero() {
        val fields = completeForm().copy(travelDistance = TravelDistance.ANY).toPlayerFields("u")
        assertEquals(0, (fields["coachingPreferences"] as Map<*, *>)["maxDistanceKm"])
        assertEquals(TravelDistance.ANY, TravelDistance.fromKm(0))
        assertNull(TravelDistance.fromKm(null))
    }

    // ── Loading stored data (old documents must not crash) ──────────────

    @Test
    fun legacyPlayerWithNoProfileLoadsWithDefaults() {
        val legacyUser = User(uid = "u", name = "Old Player", email = "o@x.in", role = UserRole.PLAYER)
        val form = PlayerProfileForm.fromStored(legacyUser, null)
        assertEquals("Old Player", form.fullName)
        assertEquals("India", form.country)
        assertFalse(form.consentAlreadyRecorded)
        assertEquals(PlayerLanding.ONBOARDING, PlayerProfilePolicy.landing(legacyUser, null, today))
    }

    @Test
    fun unknownStoredValuesAreIgnoredNotCrashing() {
        val user = User(uid = "u", name = "A B", gender = "SOMETHING_NEW")
        val player = PlayerProfile(uid = "u", skillLevel = "LEGEND", goals = listOf("FIND_COACH", "TIME_TRAVEL"),
            coachingPreferences = CoachingPreferences(format = "?", maxDistanceKm = 7))
        val form = PlayerProfileForm.fromStored(user, player)
        assertNull(form.gender)
        assertNull(form.skillLevel)
        assertEquals(listOf(PlayerGoal.FIND_COACH), form.goals)
        assertNull(form.coachingFormat)
        assertNull(form.travelDistance)
    }

    @Test
    fun roundTripThroughStoredModel() {
        val f = completeForm()
        val user = User(uid = "u", name = f.fullName, dateOfBirth = f.dateOfBirth, termsAcceptedAt = Date(), privacyAcceptedAt = Date(),
            profileStatus = PlayerProfileStatus.COMPLETE.name)
        val player = PlayerProfile(uid = "u", displayName = f.fullName, country = "India", state = "Gujarat", city = "Surat",
            region = "West India", primarySport = "Cricket", secondarySports = listOf("Football"),
            sportProfile = f.sportProfile, skillLevel = "INTERMEDIATE", goals = listOf("FIND_COACH", "JOIN_TOURNAMENTS"),
            coachingPreferences = CoachingPreferences("GROUP", "EVENING", 10))
        val loaded = PlayerProfileForm.fromStored(user, player)
        assertEquals(f, loaded)
        assertEquals(PlayerLanding.DASHBOARD, PlayerProfilePolicy.landing(user, player, today))
    }

    @Test
    fun completeStatusAloneIsNotEnough() {
        val user = User(uid = "u", name = "A B", profileStatus = PlayerProfileStatus.COMPLETE.name)
        assertEquals(PlayerLanding.ONBOARDING, PlayerProfilePolicy.landing(user, null, today))           // no public profile
        assertEquals(PlayerLanding.ONBOARDING, PlayerProfilePolicy.landing(user, PlayerProfile(uid = "u"), today)) // missing data
        val incomplete = user.copy(profileStatus = PlayerProfileStatus.INCOMPLETE.name)
        assertEquals(PlayerLanding.ONBOARDING, PlayerProfilePolicy.landing(incomplete, PlayerProfile(uid = "u"), today))
    }

    @Test
    fun sportProfilesAreSanitisedAndDescribed() {
        assertTrue(SportProfiles.attributesFor("Chess").isEmpty())
        assertEquals(3, SportProfiles.attributesFor("Cricket").size)
        val clean = SportProfiles.sanitise("Cricket", mapOf("playerRole" to "Bowler", "playerRole2" to "x", "battingStyle" to "Upside down"))
        assertEquals(mapOf("playerRole" to "Bowler"), clean)
        assertEquals(listOf("Playing role" to "Bowler"), SportProfiles.describe("Cricket", clean))
        // Every sport with questions uses a sport name players and coaches share.
        SportProfilesKeys.forEach { assertTrue("$it not in Sports.ALL", it in Sports.ALL) }
    }

    private val SportProfilesKeys = listOf("Cricket", "Football", "Badminton", "Basketball", "Tennis", "Table Tennis",
        "Athletics", "Swimming", "Hockey", "Volleyball", "Kabaddi").filter { SportProfiles.attributesFor(it).isNotEmpty() }
}
