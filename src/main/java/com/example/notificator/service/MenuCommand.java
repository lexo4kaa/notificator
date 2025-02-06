package com.example.notificator.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MenuCommand {

    START("/start", "get a welcome message"),
    GET_ALL("/getall", "get all saved reminders"),
    ADD("/add", "add new reminder"),
    EDIT("/edit", "edit the entered reminder"),
    SAVE("/save", "save the entered reminder"),
    DELETE("/delete", "delete the reminder"),
    SWITCH("/switch", "switch locale"),
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
