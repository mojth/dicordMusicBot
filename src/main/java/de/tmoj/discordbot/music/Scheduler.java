package de.tmoj.discordbot.music;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class Scheduler extends AudioEventAdapter {

    private BlockingQueue<AudioTrack> queue = new LinkedBlockingQueue<>();
    private AudioPlayer audioPlayer;

    public Scheduler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        audioPlayer.addListener(this);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            player.startTrack(queue.poll(), false);
        }
    }

    public void addToQueue(AudioTrack track) {
        if (!audioPlayer.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    public void skip() {
        audioPlayer.startTrack(queue.poll(), false);
    }

    public void stop() {
        audioPlayer.stopTrack();
        queue.clear();
    }

    public List<String> list() {
        ArrayList<String> tracks = new ArrayList<>();
        AudioTrack playingTrack = audioPlayer.getPlayingTrack();
        if (playingTrack != null) {
            AudioTrackInfo playingTrackInfo = playingTrack.getInfo();
            tracks.add("Now playing: " + playingTrackInfo.title + " - " + playingTrackInfo.author);
        }
        queue.stream().map(t -> t.getInfo().title + " - " + t.getInfo().author).forEach(tracks::add);
        return tracks;
    }
}
