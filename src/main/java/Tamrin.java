import org.checkerframework.checker.units.qual.A;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.bots.AbsSender;


import com.google.gson.Gson ;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;


public class Tamrin extends TelegramLongPollingBot {
    // name , file
    private String nameOfPlayList = null;
    private ArrayList<User> users = new ArrayList<>();
    private User curUser = null;
    // { { date , audio } , { chatId , messageId } }
    private ArrayList<Pair<Pair<Long,String>,Pair<Long,Integer>>> sent = new ArrayList<>() ;

    public void clear_history() {
        ArrayList<Pair<Long,Integer>> history = curUser.getHistory();
        if (history.isEmpty())
            return;
        for (Pair<Long,Integer> message : history) {
            DeleteMessage deleteMessage = new DeleteMessage(curUser.getChatId(), message.getValue());
            try {
                execute(deleteMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        history.clear();
        curUser.setHistory(history);
    }


    public void sendMessage(String text) {
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(curUser.getChatId());
        System.out.println(curUser.getName() + " " + text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void deleteSent() {
        setSent();
        ArrayList<Pair<Pair<Long,String>,Pair<Long,Integer>>> remove = new ArrayList<>() ;
        ArrayList<Pair<Pair<Long,String>,Pair<Long,Integer>>> add = new ArrayList<>() ;
        if(sent.isEmpty()) return ;
        for(int i = 0 ; i < sent.size() ; ++i) {
            Pair<Pair<Long,String>,Pair<Long,Integer>> message = sent.get(i) ;
            long now = System.currentTimeMillis() ;
            long messageTime = message.getKey().getKey() ;
            if(now / 1000 - messageTime >= 20 * 60 * 60){
                remove.add(message) ;

                DeleteMessage deleteMessage = new DeleteMessage(message.getValue().getKey(), message.getValue().getValue()) ;
                SendAudio sendAudio = new SendAudio() ;
                sendAudio.setAudio(message.getKey().getValue());
                sendAudio.setChatId(message.getValue().getKey());
                try {
                    execute(deleteMessage) ;
                    Message message1 = execute(sendAudio) ;
                    add.add(new Pair(new Pair(message1.getDate(),message1.getAudio().getFileId()) , new Pair(message1.getChatId(),message1.getMessageId()))) ;
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
        for(Pair<Pair<Long,String>,Pair<Long,Integer>>  message : remove)
            sent.remove(message) ;
        for(Pair<Pair<Long,String>,Pair<Long,Integer>>  message : add)
            sent.add(message) ;
        updateSentFile() ;
    }

    public void updateSentFile() {
        File file = new File("sent.json") ;
        try {
            file.createNewFile() ;
            Gson gson = new Gson() ;
            String json = gson.toJson(sent) ;
            FileWriter fw = new FileWriter(file) ;
            fw.write(json) ;
            fw.flush();
            fw.close() ;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSent() {
        Gson gson = new Gson() ;
        try {
            sent = gson.fromJson(new FileReader(new File("sent.json")) , ArrayList.class) ;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        boolean alreadyUser = false;
        /*
        for (User user : users) {
            if (user.getChatId() == update.getMessage().getChatId()) {
                curUser = user;
                alreadyUser = true;
                break;
            }
        }*/

        File usersFile = new File("users") ;
        usersFile.mkdir() ;
        File []files = usersFile.listFiles() ;
        for(File file : files) {
            if(file.isFile()) {
                long chatId = update.getMessage().getChatId() ;
                String name = file.getName() ;
                if(name.equals(String.valueOf(chatId) + ".json")) {
                    alreadyUser = true ;
                    Gson gson = new Gson() ;
                    try {
                        curUser = gson.fromJson(new FileReader(file) , User.class) ;
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    break ;
                }
            }
        }


        if (!alreadyUser) {
            curUser = new User(update.getMessage());
            users.add(curUser);
        }
        System.out.println(curUser.getName() + " " + update.getMessage().getText());
        String command = curUser.getCommand();
        if (command == null) {
            String str = update.getMessage().getText();
            if ("/create".equals(str) || "/add".equals(str)) {
                curUser.setCommand("/create");
            } else if ("/get".equals(str)) {
                curUser.setCommand("/get");
            } else if ("/list".equals(str)) {
                curUser.setCommand(null);
                String stringBuilder = "List of your playlists : \n";
                for (PlayList playList : curUser.getPlayLists()) {
                    stringBuilder += playList.getName() + ",";
                }
                sendMessage(stringBuilder);
            } else if (str.equals("/start")) {
                String stringBuilder = "Hey welcome to playlist bot";
                sendMessage(stringBuilder);
            } else if (str.equals("/users")) {
                if (update.getMessage().getFrom().getUserName().equals("Nima10Khodaveisi")) {
                    String string = "list of users : ";
                    for (User user : users)
                        string += user.getName() + ", ";
                    sendMessage(string);
                }
            }
        } else if (command.equals("/create")) {
            String name = update.getMessage().getText();
            nameOfPlayList = name;
            curUser.setCommand("name");
            curUser.createNewPlayList(name);
        } else if (command.equals("name")) {
            if (update.getMessage().getAudio() == null) {
                // /done
                System.out.println("done " + nameOfPlayList);
                sendMessage(nameOfPlayList + " has been created!");
                clear_history();
                curUser.setCommand(null);
                return;
            }
            curUser.add(nameOfPlayList, update.getMessage());
            curUser.addToHistory(new Pair(update.getMessage().getChatId(),update.getMessage().getMessageId()));
            System.out.println("add " + update.getMessage().getAudio().getTitle());
        } else if (command.equals("/get")) {
            curUser.setCommand(null);
            String name = update.getMessage().getText();
            PlayList playList = curUser.getPlayList(name);
            if (playList == null) {
                return;
            }
            clear_history();
            ArrayList<String> songs = playList.getSongs();
            for (String song : songs) {
                System.out.println(song);
                SendAudio sendAudio = new SendAudio();
                sendAudio.setAudio(song);
                sendAudio.setChatId(update.getMessage().getChatId());
                System.out.println("get " + name + " " + song);
                try {
                    Message message = execute(sendAudio);
                    sent.add(new Pair(new Pair(message.getDate(),message.getAudio().getFileId()) , new Pair(message.getChatId(),message.getMessageId()))) ;
                    updateSentFile() ;
                    curUser.addToHistory(new Pair(message.getChatId(),message.getMessageId())) ;
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "MyOwnPlayLists_bot";
    }

    @Override
    public String getBotToken() {
        return "928487559:AAEvAZnXgaV5aw8Wzq9kPV1QtW85Lgwl0l8";
    }
}