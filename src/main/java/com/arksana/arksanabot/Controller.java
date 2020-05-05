package com.arksana.arksanabot;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.Multicast;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.FileMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.message.FlexMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.flex.container.FlexContainer;
import com.linecorp.bot.model.objectmapper.ModelObjectMapper;
import com.linecorp.bot.model.profile.UserProfileResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
public class Controller {

    @Autowired
    @Qualifier("lineMessagingClient")
    private LineMessagingClient lineMessagingClient;

    @Autowired
    @Qualifier("lineSignatureValidator")
    private LineSignatureValidator lineSignatureValidator;

    List<Message> replyList = new ArrayList<>();
    List<Message> pushList = new ArrayList<>();
    String replyToken;

    @RequestMapping(value = "/webhook", method = RequestMethod.POST)
    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String xLineSignature, @RequestBody String eventsPayload) {
        try {
//            if (!lineSignatureValidator.validateSignature(eventsPayload.getBytes(), xLineSignature)) {
//                throw new RuntimeException("Invalid Signature Validation");
//            }

            // parsing event
            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            EventsModel eventsModel = objectMapper.readValue(eventsPayload, EventsModel.class);

            eventsModel.getEvents().forEach((event) -> {
                replyList.clear();
                if (event instanceof MessageEvent) {
                    if (event.getSource() instanceof GroupSource || event.getSource() instanceof RoomSource) {
                        handleGroupRoomChats((MessageEvent) event);
                    } else {
                        handleOneOnOneChats((MessageEvent) event);
                    }

                    handleText((MessageEvent) event);
                }
            });


            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private void handleText(MessageEvent event) {
        TextMessageContent textMessageContent = (TextMessageContent) event.getMessage();
        String message = textMessageContent.getText().toLowerCase();
        replyToken = event.getReplyToken();

        if (event.getMessage() instanceof AudioMessageContent || event.getMessage() instanceof ImageMessageContent || event.getMessage() instanceof FileMessageContent
        ) {
            //Kosong
        } else if (event.getMessage() instanceof TextMessageContent) {
            //Pesan
            if (message.toLowerCase().contains("!arksana")) {
                addTextReply("I am ruthless business woman!!");
                addTextReply("Let's just being nice person OK!");
                addStickerReply("11538", "51626501");
            } else if (!event.getSource().getUserId().isEmpty() && message.toLowerCase().contains("!me")) {
                String userId = event.getSource().getUserId();
                UserProfileResponse profile = getProfile(userId);
                addTextReply("Halo, " + profile.getDisplayName() + "." +
                        "\nUserID: " + profile.getUserId() + "" +
                        "\nStatus: " + profile.getStatusMessage());
            } else if (message.contains("!event")) {
                addReplyFlex("event");
            } else if (message.startsWith("!") && !message.contains("!greet") && !message.contains("!event") && !message.contains("!join") &&!message.contains("!help") ) {
                addTextReply("!help: menampilkan daftar perintah.");
            }
        }
        sendReply();
    }

    private void handleOneOnOneChats(MessageEvent event) {

    }

    private void handleGroupRoomChats(MessageEvent event) {

    }


    @RequestMapping(value = "/pushmessage/{id}/{message}", method = RequestMethod.GET)
    public ResponseEntity<String> pushmessage(
            @PathVariable("id") String userId, @PathVariable("message") String textMsg
    ) {
        List<Message> messagesList = new ArrayList<>();
        messagesList.add(new TextMessage(textMsg));
        messagesList.add(new StickerMessage("11537", "52002752"));
        PushMessage pushMessage = new PushMessage(userId, messagesList);
        push(pushMessage);

        return new ResponseEntity<String>("Push message:" + textMsg + "\nsent to: " + userId, HttpStatus.OK);
    }

    @RequestMapping(value = "/multicast", method = RequestMethod.GET)
    public ResponseEntity<String> multicast() {
        String[] userIdList = {
                "U07ce39d6f44f39c205f6fdd474c785fa",
                "",
                "",
                "",
                ""};
        Set<String> listUsers = new HashSet<String>(Arrays.asList(userIdList));
        if (listUsers.size() > 0) {
            String textMsg = "Ini pesan multicast";
            sendMulticast(listUsers, textMsg);
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    @RequestMapping(value = "/profile/{id}", method = RequestMethod.GET)
    public ResponseEntity<String> profile(
            @PathVariable("id") String userId
    ) {
        UserProfileResponse profile = getProfile(userId);

        if (profile != null) {
            String profileName = profile.getDisplayName();
            TextMessage textMessage = new TextMessage("Hello, " + profileName);
            PushMessage pushMessage = new PushMessage(userId, textMessage);
            push(pushMessage);

            return new ResponseEntity<String>("Hello, " + profileName, HttpStatus.OK);
        }
        return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    }

    //Reply
    private void sendReply() {
        ReplyMessage replyMessage = new ReplyMessage(replyToken, replyList);
        reply(replyMessage);
    }

    private void addTextReply(String text) {
        replyList.add(new TextMessage(text));
    }

    private void addStickerReply(String packageId, String stickerId) {
        replyList.add(new StickerMessage(packageId, stickerId));
    }

    private void reply(ReplyMessage replyMessage) {
        try {
            lineMessagingClient.replyMessage(replyMessage).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void addReplyFlex(String flexContainerString) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            String flexTemplate = IOUtils.toString(classLoader.getResourceAsStream(flexContainerString + ".json"));

            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            FlexContainer flexContainer = objectMapper.readValue(flexTemplate, FlexContainer.class);

            replyList.add(new FlexMessage("Flex", flexContainer));
//            ReplyMessage replyMessage = new ReplyMessage(replyToken, new FlexMessage("PesanFlex", flexContainer));
//            reply(replyMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //Push
    private void push(PushMessage pushMessage) {
        try {
            lineMessagingClient.pushMessage(pushMessage).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    //MultiCast
    private void sendMulticast(Set<String> sourceUsers, String txtMessage) {
        TextMessage message = new TextMessage(txtMessage);
        Multicast multicast = new Multicast(sourceUsers, message);

        try {
            lineMessagingClient.multicast(multicast).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    //Get Profile
    private UserProfileResponse getProfile(String userId) {
        try {
            return lineMessagingClient.getProfile(userId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}