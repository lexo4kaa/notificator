package com.example.notificator.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MenuCommand {

    START("/start", "get a welcome message"),
    GET_ALL("/getall", "get all saved reminders"),
    ADD("/add", "add new reminder"),
    EDIT("/edit", "edit a reminder that is in the process of being created"),
    SAVE("/save", "save entered reminder"),
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
