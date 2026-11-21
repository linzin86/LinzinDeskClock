package com.linzin.clockcore;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CityNamesTest {
    @Test public void prefersTheLocalityThenTheCity() {
        assertEquals("Taipei", CityNames.select(" Taipei ", "City", "Taiwan", "Province", new int[0], new String[0]));
        assertEquals("City", CityNames.select(" ", "City", "Taiwan", "Province", new int[0], new String[0]));
    }
    @Test public void excludesCountryAndProvinceEvenWhenTheyAppearAsTheCity() {
        assertNull(CityNames.select("Taiwan", "Province", "Taiwan", "Province", new int[] { 2, 4, 9 }, new String[] { "Country", "State", "Street" }));
    }
    @Test public void usesTheMostDetailedAllowedAdministrativeEntry() {
        assertEquals("Town", CityNames.select(null, null, "Country", "State",
            new int[] { 5, 6, 8, 9 }, new String[] { "County", "District", "Town", "Street" }));
    }
    @Test public void doesNotInventALocationWhenAllEntriesAreTooBroad() {
        assertNull(CityNames.select("", "", "Country", "State", new int[] { 1, 4 }, new String[] { "Country", "State" }));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsMisalignedAdministrativeArrays() {
        CityNames.select("", "", "", "", new int[] { 5 }, new String[0]);
    }
}
