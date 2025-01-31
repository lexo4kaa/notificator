package com.example.notificator.service;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.example.notificator.config.BotConfig;
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
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.cronutils.model.CronType.QUARTZ;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final BotConfig config;

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
                sendTextMessage(chatId, "Sorry, command was not recognized");
                return;
            }

            switch (command) {
                case START:
                    startCommandReceived(chatId, update.getMessage().getChat().getUserName());
                    break;
                case ADD_REMINDER:
                    addReminderCommandReceived(chatId);
                    break;
                default:
                    sendTextMessage(chatId, "The logic for this command has not yet been implemented");
                    break;
            }
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
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Specify the text/time to send the notification");
        executeBotApiMethod(sendMessage);
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
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
        CronParser cronParser = new CronParser(cronDefinition);

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

}
