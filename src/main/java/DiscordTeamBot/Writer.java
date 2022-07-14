package DiscordTeamBot;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class Writer {
    public void writeToFile(String path, String content) throws Exception {
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        writer.write(content);
        writer.close();
    }
}
