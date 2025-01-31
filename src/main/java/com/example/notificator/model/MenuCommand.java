package com.example.notificator.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MenuCommand {

    START("/start", "get a welcome message"),
    MY_DATA("/mydata", "get data stored"),
    DELETE_DATA("/deletedata", "delete data stored"),
    ADD_REMINDER("/addreminder", "add reminder"),
    HELP("/help", "info how to use this bot");

    private final String command;
    private final String description;

    public static MenuCommand fromCommand(String command) {
        for (MenuCommand menuCommand : MenuCommand.values()) {
            if (menuCommand.getCommand().equals(command)) {
                return menuCommand;
            }
        }
        return null;
    }

}
