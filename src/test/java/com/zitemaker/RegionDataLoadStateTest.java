package com.zitemaker;

import com.zitemaker.helpers.RegionData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionDataLoadStateTest {

    @Test
    void newRegionDataStartsDeferredWithoutFailing() {
        RegionData regionData = new RegionData(null);
        assertFalse(regionData.isBlockDataLoaded(), "Newly created RegionData should not have block data loaded");
        assertFalse(regionData.isLoadFailed(), "Newly created RegionData should not be marked as failed");
    }

    @Test
    void resetLoadStateClearsFailureFlag() {
        RegionData regionData = new RegionData(null);
        regionData.setBlockDataLoaded(true);
        assertTrue(regionData.isBlockDataLoaded());

        regionData.setLoadFailed(true);
        assertTrue(regionData.isLoadFailed(), "Load failure flag must be true before reset");

        regionData.setBlockDataLoaded(false);
        regionData.resetLoadState();

        assertFalse(regionData.isBlockDataLoaded());
        assertFalse(regionData.isLoadFailed(), "resetLoadState must clear loadFailed flag to false");
    }
}
