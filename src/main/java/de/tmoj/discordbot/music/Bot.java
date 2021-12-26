package de.tmoj.discordbot.music;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Bot extends ListenerAdapter {

    private static final String PREFIX = "!";
    private static final String BULB_EMOJI = "\uD83D\uDCA1";
    private static final String WARN_EMOJI = "\u26A0";
    private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);
    private MessageEmbed help;
    private DefaultAudioPlayerManager audioPlayerManager;
    private Map<Long, Scheduler> schedulerMap;

    public static void main(String[] args) throws LoginException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "Debug");
        LOGGER.debug("Debug logging enabled");
        if (args.length != 1) {
            System.out.println("Usage: Provide the Token as first and only argument");
            System.exit(1);
        }
        JDABuilder.createLight(args[0], GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES)
                .addEventListeners(new Bot()).build();
    }

    public Bot() {
        help = new EmbedBuilder().setTitle(BULB_EMOJI + " Usage") //
                .setDescription("Use one of the following commands: \n"//
                        + "!play <url> - adds the Song to the queue\n" //
                        + "!skip - skips to the next song\n" //
                        + "!stop - stops playback\n"//
                        + "!list - list songs in queue\n") //
                .setColor(0xfcda00).build();
        audioPlayerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        schedulerMap = new ConcurrentHashMap<>();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String contentRaw = event.getMessage().getContentRaw();
        if (!contentRaw.startsWith(PREFIX)) {
            return;
        }
        String args[] = contentRaw.trim().split("\\s");
        LOGGER.debug("Recieved arguments: " + args.toString());
        switch (args[0]) {
        case "!play" -> play(args, event);
        case "!skip" -> skip(event.getGuild());
        case "!stop" -> stop(event.getGuild());
        case "!list" -> list(event);
        default -> help(event.getChannel());
        }
    }

    private synchronized Scheduler getSchedulerByGuildId(Guild guild) {
        return schedulerMap.computeIfAbsent(guild.getIdLong(), l -> buildSchedulerAndSetGuildSendHandler(guild));
    }

    private Scheduler buildSchedulerAndSetGuildSendHandler(Guild guild) {
        AudioPlayer audioPlayer = audioPlayerManager.createPlayer();
        guild.getAudioManager().setSendingHandler(new SendHandler(audioPlayer));
        LOGGER.info("New Scheduler for Guild: " + guild.getName());
        return new Scheduler(audioPlayer);
    }

    private void play(String[] args, MessageReceivedEvent event) {
        if (args.length != 2) {
            help(event.getChannel());
            return;
        }
        connectVoiceChannel(event);
        Scheduler scheduler = getSchedulerByGuildId(event.getGuild());
        audioPlayerManager.loadItemOrdered(scheduler, args[1], new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                LOGGER.info("Track Loaded: " + track.getIdentifier());
                scheduler.addToQueue(track);
                event.getChannel().sendMessage(track.getInfo().title + " added to queue").queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                LOGGER.info("paylist Loaded: " + playlist.getName());
                for (AudioTrack track : playlist.getTracks()) {
                    scheduler.addToQueue(track);
                    event.getChannel().sendMessage(track.getInfo().title + " added to queue").queue();
                }
            }

            @Override
            public void noMatches() {
                LOGGER.info("No matches for " + args[1]);
                event.getChannel().sendMessage(WARN_EMOJI + " can't find Track " + args[1]).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getChannel().sendMessage(WARN_EMOJI + " Error: " + exception.getMessage());
                LOGGER.error("Error while loading Track " + args[1], exception);
            }
        });
    }

    private void connectVoiceChannel(MessageReceivedEvent event) {
        if (!event.getGuild().getAudioManager().isConnected()) {
            Optional<VoiceChannel> voiceChannel = findfirstChannel(event.getGuild());
            if (voiceChannel.isPresent()) {
                event.getGuild().getAudioManager().openAudioConnection(voiceChannel.get());
                LOGGER.info("Connected to voice channel for guild " + event.getGuild());
            } else {
                event.getChannel().sendMessage(WARN_EMOJI + " Error: can't find voice channel");
                LOGGER.error("Can't find voice channel for guild " + event.getGuild().getName());
            }
        }
    }

    private Optional<VoiceChannel> findfirstChannel(Guild guild) {
        return guild.getVoiceChannels().stream().findFirst();
    }

    private void skip(Guild guild) {
        getSchedulerByGuildId(guild).skip();
    }

    private void stop(Guild guild) {
        getSchedulerByGuildId(guild).stop();
    }

    private void list(MessageReceivedEvent event) {
        List<String> tracks = getSchedulerByGuildId(event.getGuild()).list();
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        for (String track : tracks) {
            joiner.add(track);
        }
        MessageEmbed embed = new EmbedBuilder().setTitle("Playlist").setDescription(joiner.toString()).build();
        event.getChannel().sendMessageEmbeds(embed).queue();
    }

    private void help(MessageChannel messageChannel) {
        messageChannel.sendMessageEmbeds(help).queue();
    }

}
