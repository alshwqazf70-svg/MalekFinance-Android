package com.malekfinance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCategoryPolicyTest {
    @Test
    fun trimsNamesAndRejectsBlankOrOverlongValues() {
        assertEquals("طعام", FinanceCategoryPolicy.normalizedName("  طعام  "))
        assertEquals(null, FinanceCategoryPolicy.normalizedName("   "))
        assertEquals(null, FinanceCategoryPolicy.normalizedName("س".repeat(33)))
        assertEquals("س".repeat(32), FinanceCategoryPolicy.normalizedName("س".repeat(32)))
    }

    @Test
    fun categoryNamesAreUniqueRegardlessOfKindAndCase() {
        assertTrue(FinanceCategoryPolicy.nameAlreadyExists(listOf("عام", "طعام"), " طعام "))
        assertTrue(FinanceCategoryPolicy.nameAlreadyExists(listOf("عام", "طعام"), "عَام"))
        assertFalse(FinanceCategoryPolicy.nameAlreadyExists(listOf("عام", "طعام"), "مواصلات"))
    }

    @Test
    fun categoryKindMustMatchTransactionUnlessCategoryIsShared() {
        assertTrue(FinanceCategoryPolicy.isCompatible("EXPENSE", "EXPENSE"))
        assertTrue(FinanceCategoryPolicy.isCompatible("INCOME", "INCOME"))
        assertTrue(FinanceCategoryPolicy.isCompatible("BOTH", "INCOME"))
        assertTrue(FinanceCategoryPolicy.isCompatible("BOTH", "EXPENSE"))
        assertFalse(FinanceCategoryPolicy.isCompatible("INCOME", "EXPENSE"))
        assertFalse(FinanceCategoryPolicy.isCompatible("EXPENSE", "INCOME"))
    }
}
