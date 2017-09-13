package com.xxxtai.express.constant;

import lombok.Getter;

public enum  Command {

    FORWARD(1, "前进"),
    STOP(2, "停止"),
    TURN_LEFT(3, "向左转"),
    TURN_RIGHT(4, "向右转"),
    GO_AHEAD(5, "向前走");

    private @Getter
    String command;

    private @Getter
    String description;

    private @Getter
    int value;

    Command(int value, String description){
        this.value = value;
        this.command = Constant.PREFIX + Integer.toHexString(value) + Constant.COMMAND_SUFFIX;
        this.description = description;
    }
}