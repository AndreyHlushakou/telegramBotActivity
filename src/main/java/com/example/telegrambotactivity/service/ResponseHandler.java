package com.example.telegrambotactivity.service;

import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.sender.SilentSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.example.telegrambotactivity.service.ResponseHandler.UserState.*;

public final class ResponseHandler {

    private final SilentSender sender;
    private final Map<Long, UserState> chatStates;

    public ResponseHandler(SilentSender sender0, DBContext db) {
        sender = sender0;
        chatStates = db.getMap("chatStates");
    }

    private static final TimeUnit timeUnit = TimeUnit.MINUTES;
    private static long delay = 30;
    private static ScheduledExecutorService executor;
    private final Function<Long, TimerTask> timerTaskFunction = (chatId) -> new TimerTask() {
        @Override
        public void run() {
            String text = "Go to Activity!!!\nTime send =" + LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
            buildSendMessageAndExecute(chatId, text, STOP_KEYBOARD);
        }
    };


    public enum UserState {
        START_CHAT,
        REPLY_START,
        REPLY_SELECT_ACTION_START,
        SET_INTERVAL,
        INFINITY_ACTIVITY,
        STOP_CHAT
    }

    private static final BiConsumer<Long, Message>[] STATE_MACHINE = new BiConsumer[UserState.values().length];
    {
        STATE_MACHINE[START_CHAT.ordinal()] = this::startChat;
        STATE_MACHINE[REPLY_START.ordinal()] = this::replyToStart;
        STATE_MACHINE[REPLY_SELECT_ACTION_START.ordinal()] = this::replySelectActionStart;
        STATE_MACHINE[SET_INTERVAL.ordinal()] = this::replySetInterval;
        STATE_MACHINE[INFINITY_ACTIVITY.ordinal()] = this::replyInfinityActivity;
        STATE_MACHINE[STOP_CHAT.ordinal()] = this::stopChat;
    }

    public void selectState(long chatId, Message message) {
        userMessageId = message.getMessageId();
        if (message.getText().equalsIgnoreCase("/start")) {
            STATE_MACHINE[START_CHAT.ordinal()].accept(chatId, message);
            return;
        }
        if (message.getText().equalsIgnoreCase("/stop")) {
            STATE_MACHINE[STOP_CHAT.ordinal()].accept(chatId, message);
            return;
        }
        STATE_MACHINE[chatStates.get(chatId).ordinal()].accept(chatId, message);
    }

    public boolean userIsActive(Long chatId) {
        return chatStates.containsKey(chatId);
    }


    private Integer lastMessageId = null;
    private Integer userMessageId = null;

    private void buildSendMessageAndExecute(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
        Optional<Message> messageOptional = sender.execute(sendMessage);

        messageOptional.ifPresentOrElse(
                m -> {
                    if (lastMessageId != null) {
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setChatId(m.getChatId());
                        deleteMessage.setMessageId(lastMessageId);
                        sender.execute(deleteMessage);

                        if (userMessageId != null) {
                            deleteMessage = new DeleteMessage();
                            deleteMessage.setChatId(m.getChatId());
                            deleteMessage.setMessageId(userMessageId);
                            sender.execute(deleteMessage);
                            userMessageId = null;
                        }
                    }
                    lastMessageId = m.getMessageId();
                },
                ()-> System.out.println("Delete error"));
    }

    public void startChat(long chatId, Message message) {
        String text = "Hello " + message.getFrom().getUserName() + "!";
        buildSendMessageAndExecute(chatId, text, EMPTY_KEYBOARD);
        STATE_MACHINE[REPLY_START.ordinal()].accept(chatId, message);
    }

    public void stopChat(long chatId, Message message) {
        String text = "See you soon!\nPress /start to order again";
        buildSendMessageAndExecute(chatId, text, EMPTY_KEYBOARD);
        chatStates.remove(chatId);
    }

    public void replyToStart(long chatId, Message message) {
        String text = "Select action." +
                "\nDelay=" + delay + " " + timeUnit.toString() +
                "\nOr press /stop to exit.";
        buildSendMessageAndExecute(chatId, text, START_KEYBOARD);
        chatStates.put(chatId, REPLY_SELECT_ACTION_START);
    }

    private void replySelectActionStart(long chatId, Message message) {
        String messageButton = message.getText();
        if (messageButton.equalsIgnoreCase(START_LIST[0])) {
            String text = "Start infinity activity.";
            buildSendMessageAndExecute(chatId, text, STOP_KEYBOARD);
            chatStates.put(chatId, INFINITY_ACTIVITY);

            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(timerTaskFunction.apply(chatId), delay, delay, timeUnit);
        }
        else if (messageButton.equalsIgnoreCase(START_LIST[1])) {
            String text = "This delay =" + delay + " " + timeUnit.toString();
            buildSendMessageAndExecute(chatId, text, EMPTY_KEYBOARD);
            chatStates.put(chatId, SET_INTERVAL);
        } else if (messageButton.equalsIgnoreCase(START_LIST[2])) {
            stopChat(chatId, message);
        } else unexpectedMessage(chatId);
    }

    private void replySetInterval(long chatId, Message message) {
        String message0 = message.getText();
        try {
            delay = Long.parseLong(message0);

            String text = "Save delay =" + delay + " " + timeUnit.toString();
            buildSendMessageAndExecute(chatId, text, EMPTY_KEYBOARD);
            STATE_MACHINE[REPLY_START.ordinal()].accept(chatId, message);
        } catch (NumberFormatException e) {
            String text = "Incorrect number =" + message0 + " .\nTry again.";
            buildSendMessageAndExecute(chatId, text, EMPTY_KEYBOARD);
        }
    }

    private void replyInfinityActivity(long chatId, Message message) {
        String messageButton = message.getText();

        if (messageButton.equalsIgnoreCase(STOP_LIST[0])) {
            executor.shutdown();

            String text = "Stop Activity";
            buildSendMessageAndExecute(chatId, text, EMPTY_KEYBOARD);
            STATE_MACHINE[REPLY_START.ordinal()].accept(chatId, message);
        } else unexpectedMessage(chatId);

    }

    private void unexpectedMessage(long chatId) {
        String text = "I did not expect that.";
        buildSendMessageAndExecute(chatId, text, EMPTY_KEYBOARD);
    }


    private static ReplyKeyboard replyKeyboardFactory(String[] stringList) {
        KeyboardRow row = new KeyboardRow();
        row.addAll(Arrays.stream(stringList).toList());
        return new ReplyKeyboardMarkup(List.of(row));
    }

    private static final ReplyKeyboard EMPTY_KEYBOARD = new ReplyKeyboardRemove(true);

    private static final String[] START_LIST = new String[]{"Start", "Set delay", "Exit"};
    private static final ReplyKeyboard START_KEYBOARD = replyKeyboardFactory(START_LIST);

    private static final String[] STOP_LIST =  new String[]{"Stop"};
    private static final ReplyKeyboard STOP_KEYBOARD = replyKeyboardFactory(STOP_LIST);


}
