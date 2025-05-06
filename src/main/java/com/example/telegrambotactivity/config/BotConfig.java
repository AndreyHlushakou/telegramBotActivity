package com.example.telegrambotactivity.config;

import com.example.telegrambotactivity.service.BotService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotConfig {

    private final BotService botService;

    public BotConfig(BotService botService) {
        this.botService = botService;
    }

    @PostConstruct
    public void config() {
        try {
            new TelegramBotsApi(DefaultBotSession.class).registerBot(botService);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

}
