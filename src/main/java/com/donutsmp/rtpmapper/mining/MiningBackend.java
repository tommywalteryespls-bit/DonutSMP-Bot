package com.donutsmp.rtpmapper.mining;

import java.util.List;

/** Loader-independent boundary around the optional Baritone API. */
public interface MiningBackend {
    boolean available();

    void start(List<String> blockIds, int quantity);

    boolean isMineProcessActive();

    void cancelMine();

    void cancelEverything();
}
