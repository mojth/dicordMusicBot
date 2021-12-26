package de.tmoj.discordbot.music;

import java.nio.ByteBuffer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import net.dv8tion.jda.api.audio.AudioSendHandler;

/**
 * This is more or less a copy of
 * com.sedmelluq.discord.lavaplayer.demo.jda.AudioPlayerSendHandler. the
 * original code can be found under: https://github.com/sedmelluq/lavaplayer
 */
public class SendHandler implements AudioSendHandler {

    private AudioPlayer audioPlayer;
    private ByteBuffer buffer;
    private MutableAudioFrame frame;

    public SendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        return audioPlayer.provide(frame);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        buffer.flip();
        return buffer;
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
