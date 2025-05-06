package com.example.telegrambotactivity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.abilitybots.api.bot.AbilityBot;
import org.telegram.abilitybots.api.objects.Ability;
import org.telegram.abilitybots.api.objects.Flag;
import org.telegram.abilitybots.api.objects.Reply;

import static org.telegram.abilitybots.api.objects.Locality.USER;
import static org.telegram.abilitybots.api.objects.Privacy.PUBLIC;
import static org.telegram.abilitybots.api.util.AbilityUtils.getChatId;

@Service
public class BotService extends AbilityBot {

    private final ResponseHandler responseHandler = new ResponseHandler(silent, db);

    public BotService(@Value("${bot.token}") String token, @Value("${bot.name}") String name) {
        super(token, name);
    }

    public Ability startBot() {
        return Ability.builder()
                .name("start")
                .info("Starts the bot")
                .locality(USER)
                .privacy(PUBLIC)
//                .input(0)
                .action(ctx -> responseHandler.selectState(ctx.chatId(), ctx.update().getMessage()))
//                .post()
                .build();
    }

    public Reply replyBot() {
        return Reply.of(
                (abilityBot, upd) -> responseHandler.selectState(getChatId(upd), upd.getMessage()),
                Flag.TEXT,
                upd -> responseHandler.userIsActive(getChatId(upd)));
    }

    @Override
    public long creatorId() {
        return
//                1L;
                (long) ((Math.random()+1) * 1000000);
    }
}

