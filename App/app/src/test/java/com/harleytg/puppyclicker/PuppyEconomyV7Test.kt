package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyEconomyV7Test {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun tenTreatsConvertToExactlyOneCasinoChip() {
        assertTrue(PuppyEconomyV7.canConvertTreatsToChips(10L))
        assertEquals(1L, PuppyEconomyV7.chipsForTreats(10L))
        assertEquals(50L, PuppyEconomyV7.chipsForTreats(500L))
        assertEquals(250L, PuppyEconomyV7.chipsForTreats(2_500L))
        assertEquals(1_000L, PuppyEconomyV7.chipsForTreats(10_000L))
    }

    @Test
    fun partialOrInvalidTreatConversionIsRejected() {
        assertFalse(PuppyEconomyV7.canConvertTreatsToChips(0L))
        assertFalse(PuppyEconomyV7.canConvertTreatsToChips(-10L))
        assertFalse(PuppyEconomyV7.canConvertTreatsToChips(9L))
        assertFalse(PuppyEconomyV7.canConvertTreatsToChips(11L))
        assertEquals(0L, PuppyEconomyV7.chipsForTreats(11L))
    }

    @Test
    fun ticketShopPricingEscalatesByQuarterBaseAndCapsAtSixTimesBase() {
        assertEquals(250L, PuppyEconomyV7.ticketShopPrice(TicketRarity.COMMON, 0))
        assertEquals(312L, PuppyEconomyV7.ticketShopPrice(TicketRarity.COMMON, 1))
        assertEquals(375L, PuppyEconomyV7.ticketShopPrice(TicketRarity.COMMON, 2))
        assertEquals(1_500L, PuppyEconomyV7.ticketShopPrice(TicketRarity.COMMON, 20))
        assertEquals(1_500L, PuppyEconomyV7.ticketShopPrice(TicketRarity.COMMON, 10_000))

        assertEquals(25_000L, PuppyEconomyV7.ticketShopPrice(TicketRarity.LEGENDARY, 0))
        assertEquals(150_000L, PuppyEconomyV7.ticketShopPrice(TicketRarity.LEGENDARY, 20))
    }

    @Test
    fun upgradeBoneCostsTrackStrengthAndTicketUpgradesDoubleThem() {
        val byId = V5_UPGRADES.associateBy { it.id }
        assertEquals(1L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("better_treats")))
        assertEquals(3L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("golden_bowl")))
        assertEquals(14L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("puppy_power")))
        assertEquals(24L, PuppyEconomyV7.upgradeBoneCost(byId.getValue("treat_factory")))
    }

    @Test
    fun accessoryPricesArePermanentShopCurrencyPrices() {
        assertEquals(150L, PuppyEconomyV7.accessoryPrice("Bandana"))
        assertEquals(250L, PuppyEconomyV7.accessoryPrice("Bow"))
        assertEquals(1_000L, PuppyEconomyV7.accessoryPrice("Crown"))
        assertEquals(null, PuppyEconomyV7.accessoryPrice("None"))
    }

    @Test
    fun rareTicketRollConfigurationMatchesPolicy() {
        assertEquals(500, PuppyEconomyV7.TICKET_DROP_DENOMINATOR)
        val vm = source("PuppyClickerV6ViewModel.kt")
        assertTrue(vm.contains("Random.nextInt(PuppyEconomyV7.TICKET_DROP_DENOMINATOR) == 0"))
        assertTrue(vm.contains("roll < 7_800 -> TicketRarity.COMMON"))
        assertTrue(vm.contains("roll < 9_350 -> TicketRarity.UNCOMMON"))
        assertTrue(vm.contains("roll < 9_850 -> TicketRarity.RARE"))
        assertTrue(vm.contains("roll < 9_980 -> TicketRarity.EPIC"))
        assertTrue(vm.contains("else -> TicketRarity.LEGENDARY"))
    }

    @Test
    fun passiveTreatProductionIsAbsentAndAutoOwnershipFeedsActiveBonus() {
        val vm = source("PuppyClickerV6ViewModel.kt")
        assertFalse(vm.contains("current.autoPerSecond > 0"))
        assertFalse(vm.contains("addTreats((current.autoPerSecond"))
        assertTrue(vm.contains("val activeBonus = V5_UPGRADES"))
        assertTrue(vm.contains("if (upgrade.effect == V5UpgradeEffect.AUTO) actualAmount else 0"))
        assertTrue(vm.contains("nextHumanTaps % PuppyEconomyV7.ACTIVE_BONUS_INTERVAL == 0L"))
    }

    @Test
    fun accessoriesCannotBeEquippedUntilOwnedAndCasinoExchangeHasNoReversePath() {
        val vm = source("PuppyClickerV6ViewModel.kt")
        assertTrue(vm.contains("value !in current.ownedAccessories"))
        assertTrue(vm.contains("fun buyAccessoryWithPupCoins"))
        assertTrue(vm.contains("fun convertTreatsToCasinoChips"))
        assertFalse(vm.contains("convertCasinoChipsToTreats"))
        assertFalse(vm.contains("convertChipsToTreats"))
    }
}
