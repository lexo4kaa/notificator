package com.example.notificator.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MenuCommand {

    START("/start", "get a welcome message"),
    MY_DATA("/mydata", "get data stored"),
    DELETE_DATA("/deletedata", "delete data stored"),
    HELP("/help", "info how to use this bot");

    private final String command;
    private final String description;

}
