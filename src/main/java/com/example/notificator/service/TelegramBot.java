package com.example.notificator.service;

import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.example.notificator.config.BotConfig;
import com.example.notificator.dto.NotificationDTO;
import com.example.notificator.model.Notification;
import com.example.notificator.model.User;
import com.example.notificator.repository.NotificationRepository;
import com.example.notificator.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static com.cronutils.model.CronType.QUARTZ;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private static final String EDIT_TEXT_CALLBACK_DATA = "EDIT_TEXT";
    private static final String EDIT_CRON_CALLBACK_DATA = "EDIT_CRON";

    private final CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final BotConfig config;
    /**
     * key - chat id, value - notification that should be saved
     */
    private final Map<Long, NotificationDTO> userNotificationsDuringCreation = new HashMap<>();

    private Locale locale = new Locale("en", "US");

    @Autowired
    public TelegramBot(BotConfig config, UserRepository userRepository, NotificationRepository notificationRepository) {
        super(config.getToken());
        this.config = config;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        createCommands();
    }

    private void createCommands() {
        List<BotCommand> listOfCommands = Arrays.stream(MenuCommand.values()).map(command -> new BotCommand(command.getCommand(), command.getDescription())).collect(Collectors.toList());
        try {
            execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            MenuCommand command = MenuCommand.fromCommand(messageText);

            if (command == null) {
                if (isUserHasNotificationInProcess(chatId)) {
                    continueNotificationCreationProcess(chatId, messageText);
                } else {
                    sendTextMessage(chatId, "Sorry, command was not recognized");
                }
            } else {
                processEnteredCommand(update, command, chatId);
            }
        } else if (update.hasCallbackQuery()) {
            processCallbackQuery(update);
        }
    }

    private boolean isUserHasNotificationInProcess(Long chatId) {
        return userNotificationsDuringCreation.get(chatId) != null;
    }

    private void continueNotificationCreationProcess(Long chatId, String messageText) {
        NotificationDTO notificationToSave = userNotificationsDuringCreation.get(chatId);
        if (notificationToSave.getTime() == null) {
            try {
                cronParser.parse(messageText);
                notificationToSave.setTime(messageText);
                sendTextMessage(chatId, getMessage("request.notification.text"));
            } catch (Exception e) {
                sendTextMessage(chatId, e.getMessage());
            }
            return;
        }
        if (notificationToSave.getText() == null) {
            notificationToSave.setText(messageText);
        }
        sendTextMessage(chatId, "You have filled in all the required data." + "\nCron-expression: " + notificationToSave.getTime() + "\nNotification text: " + notificationToSave.getText() + "\nIf you want to edit the notification, use the /edit command" + "\nIf you want to save filled data, use the /save command");
    }

    private void processEnteredCommand(Update update, MenuCommand command, Long chatId) {
        switch (command) {
            case START:
                startCommandReceived(chatId, update.getMessage().getChat().getUserName());
                break;
            case ADD:
                addReminderCommandReceived(chatId);
                break;
            case SAVE:
                saveReminderCommandReceived(chatId);
                break;
            case EDIT:
                editReminderCommandReceived(chatId);
                break;
            default:
                sendTextMessage(chatId, "The logic for this command has not yet been implemented");
                break;
        }
    }

    private void startCommandReceived(Long chatId, String userName) {
        registerUser(chatId, userName);
        sendTextMessage(chatId, "Hi, " + userName + ", nice to meet you!");
    }

    private void registerUser(Long chatId, String userName) {
        if (userRepository.findById(chatId).isEmpty()) {
            User user = new User();
            user.setChatId(chatId);
            user.setUsername(userName);
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            userRepository.save(user);
        }
    }

    private void addReminderCommandReceived(Long chatId) {
        userNotificationsDuringCreation.put(chatId, new NotificationDTO());
        sendTextMessage(chatId, getMessage("request.notification.cron"));
    }

    private void saveReminderCommandReceived(Long chatId) {
        NotificationDTO notificationToSave = userNotificationsDuringCreation.get(chatId);
        if (notificationToSave == null) {
            sendTextMessage(chatId, "You have not started the creation process, use the " + MenuCommand.ADD.getCommand() + " command for this");
            return;
        }
        if (notificationToSave.getTime() == null || notificationToSave.getText() == null) {
            sendTextMessage(chatId, "You need to fill all the data. Current notification values: " + "\nCron-expression: " + notificationToSave.getTime() + "\nNotification text: " + notificationToSave.getText());
            return;
        }
        saveNotification(notificationToSave, chatId);
        sendTextMessage(chatId, "Notification has been saved.");
    }

    private void saveNotification(NotificationDTO notificationToSave, Long chatId) {
        Notification notification = new Notification();
        notification.setTime(notificationToSave.getTime());
        notification.setText(notificationToSave.getText());
        notification.setUser(userRepository.getByChatId(chatId));
        notificationRepository.save(notification);
    }

    private void editReminderCommandReceived(Long chatId) {
        NotificationDTO notificationToSave = userNotificationsDuringCreation.get(chatId);
        if (notificationToSave == null) {
            sendTextMessage(chatId, "You have not started the creation process, use the " + MenuCommand.ADD.getCommand() + " command for this");
            return;
        }

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("What exactly do you want to edit?");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(getInlineKeyboardButton("Notification text", EDIT_TEXT_CALLBACK_DATA));
        row.add(getInlineKeyboardButton("Cron expression", EDIT_CRON_CALLBACK_DATA));
        rows.add(row);
        markup.setKeyboard(rows);
        sendMessage.setReplyMarkup(markup);

        executeBotApiMethod(sendMessage);
    }

    private InlineKeyboardButton getInlineKeyboardButton(String text, String callbackData) {
        InlineKeyboardButton textButton = new InlineKeyboardButton();
        textButton.setText(text);
        textButton.setCallbackData(callbackData);
        return textButton;
    }

    private void processCallbackQuery(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();

        EditMessageText messageText = new EditMessageText();
        messageText.setChatId(chatId);
        messageText.setMessageId(getMessageIdFromCallback(callbackQuery));
        switch (callbackQuery.getData()) {
            case EDIT_TEXT_CALLBACK_DATA:
                userNotificationsDuringCreation.get(chatId).setText(null);
                messageText.setText(getMessage("request.notification.text"));
                break;
            case EDIT_CRON_CALLBACK_DATA:
                userNotificationsDuringCreation.get(chatId).setTime(null);
                messageText.setText(getMessage("request.notification.cron"));
                break;
        }
        executeBotApiMethod(messageText);
    }

    // this is a weird way to get the message id from a callback, but I haven't found any others
    private int getMessageIdFromCallback(CallbackQuery callbackQuery) {
        String messageToString = callbackQuery.getMessage().toString();
        int startIndex = messageToString.indexOf("messageId=");
        int endIndex = messageToString.indexOf(",", startIndex);
        return Integer.parseInt(messageToString.substring(startIndex + "messageId=".length(), endIndex));
    }

    private void sendTextMessage(Long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(textToSend);
        executeBotApiMethod(sendMessage);
    }

    private void executeBotApiMethod(BotApiMethod<? extends Serializable> method) {
        try {
            execute(method);
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    @Scheduled(cron = "${job.notification.cron}")
    @Transactional
    protected void sendNotification() {
        ZonedDateTime time = ZonedDateTime.now();

        Iterable<User> users = userRepository.findAll();

        for (User user : users) {
            for (Notification notification : user.getNotifications()) {
                String cronExpression = notification.getTime();
                ExecutionTime executionTime = ExecutionTime.forCron(cronParser.parse(cronExpression));
                if (executionTime.isMatch(time)) {
                    sendTextMessage(user.getChatId(), notification.getText());
                }
            }
        }
    }

    private String getMessage(String message) {
        return ResourceBundle.getBundle("messages", locale).getString(message);
    }

}
