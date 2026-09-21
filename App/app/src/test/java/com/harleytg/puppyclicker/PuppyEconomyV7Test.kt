package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyEconomyV7Test {
    @Test
    fun treatToChipExchangeIsExactTenToOneAndOneWayPolicyHasNoReverseFunction() {
        assertEquals(1L, PuppyEconomyV7.chipsForTreats(10L))
        assertEquals(50L, PuppyEconomyV7.chipsForTreats(500L))
        assertEquals(250L, PuppyEconomyV7.chipsForTreats(2_500L))
        assertEquals(1_000L, PuppyEconomyV7.chipsForTreats(10_000L))
        assertNull(PuppyEconomyV7.chipsForTreats(9L))
        assertNull(PuppyEconomyV7.chipsForTreats(11L))
        assertNull(PuppyEconomyV7.chipsForTreats(0L))
        assertFalse(PuppyEconomyV7.canConvertTreatsToChips(499L, 500L))
        assertTrue(PuppyEconomyV7.canConvertTreatsToChips(500L, 500L))
    }

    @Test
    fun pupCoinTicketPricesEscalateByQuarterBaseAndCapAtSixTimesBase() {
        assertEquals(250L, PuppyEconomyV7.ticketShopCost(TicketRarity.COMMON, 0))
        assertEquals(312L, PuppyEconomyV7.ticketShopCost(TicketRarity.COMMON, 1))
        assertEquals(375L, PuppyEconomyV7.ticketShopCost(TicketRarity.COMMON, 2))
        assertEquals(437L, PuppyEconomyV7.ticketShopCost(TicketRarity.COMMON, 3))
        assertEquals(1_500L, PuppyEconomyV7.ticketShopCost(TicketRarity.COMMON, 20))
        assertEquals(1_500L, PuppyEconomyV7.ticketShopCost(TicketRarity.COMMON, 500))
        assertEquals(150_000L, PuppyEconomyV7.ticketShopCost(TicketRarity.LEGENDARY, 500))
    }

    @Test
    fun accessoryPricesMatchPublishedPupCoinShop() {
        assertEquals(150L, PuppyEconomyV7.accessoryPrice("Bandana"))
        assertEquals(250L, PuppyEconomyV7.accessoryPrice("Bow"))
        assertEquals(1_000L, PuppyEconomyV7.accessoryPrice("Crown"))
        assertNull(PuppyEconomyV7.accessoryPrice("None"))
    }

    @Test
    fun upgradeBoneCostsScaleByStrengthAndTicketUpgradesDoubleThem() {
        val byId = V5_UPGRADES.associateBy { it.id }
        assertEquals(1L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("better_treats")))
        assertEquals(3L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("golden_bowl")))
        assertEquals(14L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("puppy_power")))
        assertEquals(24L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("treat_factory")))
    }

    @Test
    fun upgradeTreatGrowthUsesHarderV7CurvesAndSmartShopper() {
        val cookie = V5_UPGRADES.first { it.id == "better_treats" }
        val ticket = V5_UPGRADES.first { it.id == "lucky_collar" }

        assertEquals(35L, PuppyEconomyV7.upgradeTreatCost(V6GameState(), cookie))
        assertEquals(
            55L,
            PuppyEconomyV7.upgradeTreatCost(
                V6GameState(upgrades = mapOf(cookie.id to 1)),
                cookie
            )
        )
        assertEquals(
            102L,
            PuppyEconomyV7.upgradeTreatCost(
                V6GameState(upgrades = mapOf(ticket.id to 1)),
                ticket
            )
        )

        val shopper = V6GameState(
            upgrades = mapOf(cookie.id to 1),
            prestigeSkills = mapOf(PrestigeSkill.SMART_SHOPPER to 2)
        )
        assertEquals(49L, PuppyEconomyV7.upgradeTreatCost(shopper, cookie))
    }

    @Test
    fun activeBonusUsesLegacyAutoOwnershipWithoutPassiveProduction() {
        val upgrades = mapOf(
            "chew_toy" to 2,
            "playmate" to 1
        )
        assertEquals(7, PuppyEconomyV7.activeBonus(upgrades, 0))
        assertEquals(10, PuppyEconomyV7.activeBonus(upgrades, 1))
    }

    @Test
    fun tapTicketConfigurationIsOneInFiveHundredWithPublishedRarityBands() {
        assertTrue(PuppyEconomyV7.shouldDropTicket(0))
        assertFalse(PuppyEconomyV7.shouldDropTicket(1))
        assertFalse(PuppyEconomyV7.shouldDropTicket(499))
        assertFalse(PuppyEconomyV7.shouldDropTicket(500))

        assertEquals(TicketRarity.COMMON, PuppyEconomyV7.ticketRarityForRoll(0))
        assertEquals(TicketRarity.COMMON, PuppyEconomyV7.ticketRarityForRoll(7_799))
        assertEquals(TicketRarity.UNCOMMON, PuppyEconomyV7.ticketRarityForRoll(7_800))
        assertEquals(TicketRarity.RARE, PuppyEconomyV7.ticketRarityForRoll(9_350))
        assertEquals(TicketRarity.EPIC, PuppyEconomyV7.ticketRarityForRoll(9_850))
        assertEquals(TicketRarity.LEGENDARY, PuppyEconomyV7.ticketRarityForRoll(9_980))
    }
}
