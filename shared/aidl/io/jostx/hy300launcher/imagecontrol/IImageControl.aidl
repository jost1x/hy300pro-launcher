package io.jostx.hy300launcher.imagecontrol;
// Empty response means success. Matrix order: BL, TL, TR, BR; inward units / 1000.
interface IImageControl {
    String status();
    String shutdownStatus();
    String requestShutdown();
    String applyGeometry(in int[] matrix, int zoom);
}
