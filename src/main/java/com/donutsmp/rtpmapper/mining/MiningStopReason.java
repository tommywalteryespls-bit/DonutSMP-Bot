package com.donutsmp.rtpmapper.mining;

public enum MiningStopReason {
    NONE,
    USER_REQUEST,
    EMERGENCY_STOP,
    COMPLETED,
    TIMEOUT,
    DISCONNECTED,
    CONNECTION_CHANGED,
    SERVER_NOT_ALLOWED,
    BACKEND_ERROR,
    CLIENT_SHUTDOWN
}
