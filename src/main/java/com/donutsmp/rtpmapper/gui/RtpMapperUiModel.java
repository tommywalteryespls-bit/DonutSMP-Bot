package com.donutsmp.rtpmapper.gui;

public interface RtpMapperUiModel {
    MapperStatusView status();

    MapperSettingsView settings();

    MiningSettingsView miningSettings();

    MiningStatusView miningStatus();

    MapperStatisticsView statistics(DataScope scope);

    ChartPointProvider points(DataScope scope);

    UiActionResult startMapping();

    UiActionResult stopMapping();

    UiActionResult clearAllData();

    UiActionResult exportCsv();

    UiActionResult applySettings(MapperSettingsView settings);

    UiActionResult applyMiningSettings(MiningSettingsView settings);

    UiActionResult startMining();

    UiActionResult stopMining();

    UiActionResult emergencyStop();
}
