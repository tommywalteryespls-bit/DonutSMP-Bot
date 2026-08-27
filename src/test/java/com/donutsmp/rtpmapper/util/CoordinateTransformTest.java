package com.donutsmp.rtpmapper.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoordinateTransformTest {
    @Test
    void coordinateRoundTripIsStable() {
        CoordinateTransform transform = new CoordinateTransform(1_250.0, -7_500.0, 42.5);
        transform.setBounds(30, 40, 900, 500);

        double screenX = transform.worldToScreenX(-143_210.25);
        double screenY = transform.worldToScreenZ(82_011.75);

        assertEquals(-143_210.25, transform.screenToWorldX(screenX), 1.0e-8);
        assertEquals(82_011.75, transform.screenToWorldZ(screenY), 1.0e-8);
    }

    @Test
    void cursorAnchoredZoomDoesNotDrift() {
        CoordinateTransform transform = new CoordinateTransform(0.0, 0.0, 1_000.0);
        transform.setBounds(100, 50, 800, 400);
        double cursorX = 317.0;
        double cursorY = 229.0;
        double beforeX = transform.screenToWorldX(cursorX);
        double beforeZ = transform.screenToWorldZ(cursorY);

        transform.zoomAt(cursorX, cursorY, 3.0);

        assertEquals(beforeX, transform.screenToWorldX(cursorX), 1.0e-8);
        assertEquals(beforeZ, transform.screenToWorldZ(cursorY), 1.0e-8);
    }

    @Test
    void panMovesDataWithPointer() {
        CoordinateTransform transform = new CoordinateTransform(0.0, 0.0, 100.0);
        transform.setBounds(0, 0, 800, 600);
        double beforeX = transform.worldToScreenX(25_000.0);
        double beforeY = transform.worldToScreenZ(-15_000.0);

        transform.panPixels(25.0, -10.0);

        assertEquals(beforeX + 25.0, transform.worldToScreenX(25_000.0), 1.0e-8);
        assertEquals(beforeY - 10.0, transform.worldToScreenZ(-15_000.0), 1.0e-8);
    }

    @Test
    void fitIncludesOriginAndExtremeBounds() {
        CoordinateTransform transform = new CoordinateTransform();
        transform.setBounds(10, 20, 1_000, 500);
        transform.fitToBounds(-250_000.0, 100_000.0, 10_000.0, 200_000.0, 0.08);

        assertTrue(transform.worldToScreenX(0.0) >= transform.left());
        assertTrue(transform.worldToScreenX(0.0) <= transform.left() + transform.width());
        assertTrue(transform.worldToScreenZ(0.0) >= transform.top());
        assertTrue(transform.worldToScreenZ(0.0) <= transform.top() + transform.height());
        assertTrue(transform.worldToScreenX(-250_000.0) >= transform.left());
        assertTrue(transform.worldToScreenZ(200_000.0) <= transform.top() + transform.height());
    }

    @Test
    void fitKeepsOriginCenteredForPositiveOnlyData() {
        CoordinateTransform transform = new CoordinateTransform();
        transform.setBounds(10, 20, 1_000, 500);

        transform.fitToBounds(25_000.0, 400_000.0, 10_000.0, 150_000.0, 0.08);

        assertEquals(510.0, transform.worldToScreenX(0.0), 1.0e-8);
        assertEquals(270.0, transform.worldToScreenZ(0.0), 1.0e-8);
        assertTrue(transform.worldToScreenX(400_000.0) <= transform.left() + transform.width());
    }

    @Test
    void fitKeepsOriginCenteredForNegativeOnlyData() {
        CoordinateTransform transform = new CoordinateTransform();
        transform.setBounds(5, 15, 800, 600);

        transform.fitToBounds(-900_000.0, -50_000.0, -250_000.0, -1_000.0, 0.08);

        assertEquals(405.0, transform.worldToScreenX(0.0), 1.0e-8);
        assertEquals(315.0, transform.worldToScreenZ(0.0), 1.0e-8);
        assertTrue(transform.worldToScreenX(-900_000.0) >= transform.left());
    }
}
