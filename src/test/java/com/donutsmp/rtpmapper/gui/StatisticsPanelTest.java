package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.region.RtpRegion;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsPanelTest {
    @Test
    void compactAndFullLayoutsExposeTheExactNumberOfScrollableBucketRows() {
        StatisticsPanel panel = new StatisticsPanel();
        MapperStatisticsView statistics = statisticsWithTenBuckets(false);

        assertEquals(1, panel.visibleBucketRows(statistics, 152));
        assertEquals(9, panel.maximumBucketScroll(statistics, 152));
        assertEquals(8, panel.visibleBucketRows(statistics, 256));
        assertEquals(2, panel.maximumBucketScroll(statistics, 256));
    }

    @Test
    void visibleLegacyUnknownCountReservesAnAdditionalLegendRow() {
        StatisticsPanel panel = new StatisticsPanel();
        MapperStatisticsView statistics = statisticsWithTenBuckets(true);

        assertEquals(7, panel.visibleBucketRows(statistics, 256));
        assertEquals(3, panel.maximumBucketScroll(statistics, 256));
    }

    private static MapperStatisticsView statisticsWithTenBuckets(boolean includeUnknown) {
        List<MapperStatisticsView.RadialBucketView> buckets = java.util.stream.IntStream.range(0, 10)
            .mapToObj(index -> new MapperStatisticsView.RadialBucketView(index * 25_000.0, (index + 1) * 25_000.0, 1))
            .toList();
        java.util.ArrayList<MapperStatisticsView.RegionCountView> regions = new java.util.ArrayList<>();
        for (RtpRegion region : RtpRegion.selectableValues()) {
            regions.add(new MapperStatisticsView.RegionCountView(region, 1));
        }
        regions.add(new MapperStatisticsView.RegionCountView(
            RtpRegion.UNKNOWN,
            includeUnknown ? 1 : 0
        ));
        return new MapperStatisticsView(
            10,
            0, 0,
            -1, 1,
            -1, 1,
            1, 0, 2,
            25, 25, 25, 25,
            buckets,
            regions
        );
    }
}
